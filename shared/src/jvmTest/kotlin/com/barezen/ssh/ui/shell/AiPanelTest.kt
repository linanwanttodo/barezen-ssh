// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/shell/AiPanelTest.kt
package com.barezen.ssh.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.app.AiChatModel
import com.barezen.ssh.app.AiConfig
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.AiTransport
import com.barezen.ssh.app.AiRequest
import com.barezen.ssh.terminal.TerminalBridge
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ui.theme.BareZenTheme
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 线上 DTO 是 jvmMain internal：测试内用本地等价结构（顶层，序列化插件要求）
@kotlinx.serialization.Serializable
private data class AiWire(val choices: List<AiChoice> = emptyList())
@kotlinx.serialization.Serializable
private data class AiChoice(val delta: AiDelta = AiDelta())
@kotlinx.serialization.Serializable
private data class AiDelta(val content: String? = null)

/** T-7：AI 面板 UI。rail 切换、无 key 禁用、审批注入全流程、上下文附带。零网络。 */
@OptIn(ExperimentalTestApi::class)
class AiPanelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeKeys : AiKeyStore {
        var key: String? = "sk-test"
        override fun loadKey(): String? = key
        override fun saveKey(key: String) { this.key = key }
        override fun deleteKey() { this.key = null }
        override val keychainAvailable: Boolean = true
    }

    private class FakeShellChannel : ShellChannel {
        val written = mutableListOf<ByteArray>()
        override fun write(bytes: ByteArray) { written += bytes }
        override fun resize(cols: Int, rows: Int) {}
        override fun close() {}
    }

    private class FakeTransport(private val reply: String, private val json: Json) : AiTransport {
        override fun stream(request: AiRequest): Flow<String> = flow {
            val wire = AiWire(choices = listOf(AiChoice(delta = AiDelta(reply))))
            emit("data: " + json.encodeToString(wire) + "\n\n")
            emit("data: [DONE]\n\n")
        }
    }

    private fun chatWith(reply: String, keys: AiKeyStore = FakeKeys()): AiChatModel =
        AiChatModel(
            scope = CoroutineScope(Dispatchers.Unconfined),
            transport = FakeTransport(reply, json),
            configProvider = {
                val key = keys.loadKey()
                if (key == null) null
                else AiConfig("https://api.example.com/v1", "test-model", key)
            },
        )

    private fun bridgeWithLines(vararg lines: String): Pair<TerminalBridge, FakeShellChannel> {
        val buf = TerminalTextBuffer(80, 24, StyleState(), 1000)
        lines.forEach { text -> buf.screenBuffer.addNewLine(TextStyle.EMPTY, CharBuffer(text)) }
        val channel = FakeShellChannel()
        val bridge = TerminalBridge()
        bridge.attach(buf, channel)
        return bridge to channel
    }

    @Test
    fun railToggleEmitsCallback() = runComposeUiTest {
        var toggled = false
        setContent {
            BareZenTheme {
                AiPanel(chatWith("x"), FakeKeys(), null, expanded = false, onToggle = { toggled = true })
            }
        }
        onNodeWithTag("ai-panel-toggle").performClick()
        assertTrue(toggled)
    }

    @Test
    fun unconfiguredInputDisabledWithGuidance() = runComposeUiTest {
        val chat = AiChatModel(
            scope = CoroutineScope(Dispatchers.Unconfined),
            transport = FakeTransport("", json),
            configProvider = { null },
        )
        setContent {
            BareZenTheme {
                AiPanel(chat, FakeKeys().apply { deleteKey() }, null, expanded = true, onToggle = {})
            }
        }
        onNodeWithTag("ai-input").assertIsNotEnabled()
        onNodeWithTag("ai-send").assertIsNotEnabled()
        onNodeWithText("在设置-智能助手填写 API 配置后可用").assertIsDisplayed()
        onNodeWithText("未配置模型").assertIsDisplayed()
    }

    @Test
    fun approvalDialogShowsExactCommandAndInjects() = runComposeUiTest {
        // 回复含围栏代码块（bash）→ 候选命令 uptime
        val reply = "看负载：" + System.lineSeparator() + "FENCE_OPEN" + System.lineSeparator() +
            "uptime" + System.lineSeparator() + "FENCE_CLOSE"
        val chat = chatWith(reply.replace("FENCE_OPEN", "\u0060\u0060\u0060bash").replace("FENCE_CLOSE", "\u0060\u0060\u0060"))
        val (bridge, channel) = bridgeWithLines("idle")
        setContent {
            BareZenTheme {
                AiPanel(chat, FakeKeys(), bridge, expanded = true, onToggle = {})
            }
        }
        onNodeWithTag("ai-input").performTextInput("查一下负载")
        onNodeWithTag("ai-send").performClick()
        // 流式回复完成（同步 fake）后出现注入候选
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("ai-inject-candidate").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("ai-inject-candidate").performClick()
        // 审批对话框：显示确切命令
        onNodeWithText("注入终端命令").assertIsDisplayed()
        onNodeWithText("uptime").assertIsDisplayed()
        // 未确认前：什么都不写入
        assertTrue(channel.written.isEmpty())
        onNodeWithTag("ai-inject-confirm").performClick()
        // 确认后才写入，且带换行
        waitUntil(timeoutMillis = 5_000) { channel.written.isNotEmpty() }
        assertEquals("uptime\n", channel.written[0].toString(Charsets.UTF_8))
        // 成功后对话框关闭
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("ai-inject-confirm").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun injectConfirmWithoutChannelShowsErrorAndKeepsDialog() = runComposeUiTest {
        // 回复含提示符行 → 候选命令 df -h（避开模板转义，用 Unicode 反引号的等价路径覆盖围栏块之外）
        val chat = chatWith("看磁盘：" + System.lineSeparator() + "$ df -h")
        setContent {
            BareZenTheme {
                AiPanel(chat, FakeKeys(), null, expanded = true, onToggle = {}) // 无桥接：无会话可注入
            }
        }
        onNodeWithTag("ai-input").performTextInput("看磁盘")
        onNodeWithTag("ai-send").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("ai-inject-candidate").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("ai-inject-candidate").performClick()
        onNodeWithTag("ai-inject-confirm").performClick()
        onNodeWithText("当前没有可注入的终端会话").assertIsDisplayed()
        onNodeWithTag("ai-inject-confirm").assertIsDisplayed() // 对话框保留
    }

    @Test
    fun contextSwitchSendsSnapshotAndShowsChip() = runComposeUiTest {
        val chat = chatWith("好的")
        val (bridge, _) = bridgeWithLines("proc a", "proc b", "proc c")
        setContent {
            BareZenTheme {
                AiPanel(chat, FakeKeys(), bridge, expanded = true, onToggle = {})
            }
        }
        onNodeWithTag("ai-context-switch").performClick()
        onNodeWithTag("ai-input").performTextInput("帮我看看")
        onNodeWithTag("ai-send").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("已附带终端输出 3 行").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(3, chat.messages[0].attachedLines)
    }

    @Test
    fun contextSwitchOnWithoutOutputShowsHonestNote() = runComposeUiTest {
        val chat = chatWith("好的")
        setContent {
            BareZenTheme {
                AiPanel(chat, FakeKeys(), null, expanded = true, onToggle = {}) // 开关开了但没有会话
            }
        }
        onNodeWithTag("ai-context-switch").performClick()
        onNodeWithTag("ai-input").performTextInput("帮我看看")
        onNodeWithTag("ai-send").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("已开启附带，但当前没有可用的终端输出").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun configuredPanelEnablesInputAndSend() = runComposeUiTest {
        setContent {
            BareZenTheme {
                AiPanel(chatWith("x"), FakeKeys(), null, expanded = true, onToggle = {})
            }
        }
        onNodeWithTag("ai-input").assertIsEnabled()
        onNodeWithTag("ai-send").assertIsNotEnabled() // 空输入时发送禁用
        onNodeWithTag("ai-input").performTextInput("在吗")
        onNodeWithTag("ai-send").assertIsEnabled()
    }
}
