// shared/src/jvmTest/kotlin/com/barezen/ssh/app/CommandPaletteUiTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 命令面板的**壳层接线**：入口键位、Ctrl+K 快捷键、选中项上报、删除二次确认。
 * 纯匹配逻辑在 commonTest 的 CommandPaletteTest。
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {

    private fun modelWith(servers: List<com.barezen.ssh.servers.Server>): AppModel = AppModel(
        repo = InMemoryServerRepository(servers),
        ssh = object : com.barezen.ssh.ssh.SshClient {
            override suspend fun connect(request: com.barezen.ssh.ssh.ConnectRequest) =
                error("unused")
        },
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    // ---- 入口 ----

    @Test fun titlebarKeyOpensPalette() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(modelWith(emptyList())) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette").assertIsDisplayed()
        onNodeWithTag("command-palette-input").assertIsDisplayed()
    }

    @Test fun paletteShowsNavigationCommands() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(modelWith(emptyList())) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        // 六个导航命令都在（面板一打开就列全，不需要先输字）
        listOf("转到仪表盘", "转到服务器", "转到终端", "转到文件", "转到端口转发", "转到设置")
            .forEach { onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun paletteEmptyStateIsHonest() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(modelWith(emptyList())) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("zzzzqqqq")
        // 搜不到就说搜不到，不给假结果
        onNodeWithText("无匹配命令").assertIsDisplayed()
    }

    // ---- 选择与执行 ----

    @Test fun typingFiltersAndEnterNavigates() = runComposeUiTest {
        val m = modelWith(emptyList())
        setContent { BareZenTheme { BareZenAppContent(m) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("终端")
        // Enter 执行选中项：过滤后首条就是「转到终端」
        onNodeWithTag("command-palette-input").performKeyInput {
            keyDown(Key.Enter); keyUp(Key.Enter)
        }
        waitForIdle()
        assertTrue(m.current == Destination.TERMINAL, "Enter 应执行选中的导航命令，实际=${m.current}")
    }

    @Test fun arrowDownMovesSelectionAndEnterRunsSecond() = runComposeUiTest {
        val m = modelWith(emptyList())
        setContent { BareZenTheme { BareZenAppContent(m) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("转到")
        // ↓ 移到第二条（服务器），Enter 执行
        onNodeWithTag("command-palette-input").performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("command-palette-input").performKeyInput {
            keyDown(Key.Enter); keyUp(Key.Enter)
        }
        waitForIdle()
        assertTrue(
            m.current == Destination.SERVERS,
            "↓ + Enter 应执行第二条命令，实际=${m.current}",
        )
    }

    // ---- 服务器命令 ----

    @Test fun connectCommandOpensDialog() = runComposeUiTest {
        val m = modelWith(
            listOf(com.barezen.ssh.servers.Server("s1", "网关", "gw.example.com", user = "root")),
        )
        setContent { BareZenTheme { BareZenAppContent(m) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("gw.example")
        onNodeWithTag("palette-row-connect-s1").performClick()
        waitForIdle()
        // 连接命令应打开连接确认对话框（不是直接连）
        onNodeWithText("连接到 网关").assertIsDisplayed()
    }

    @Test fun deleteCommandRequiresConfirmation() = runComposeUiTest {
        val m = modelWith(
            listOf(com.barezen.ssh.servers.Server("s1", "网关", "gw.example.com", user = "root")),
        )
        setContent { BareZenTheme { BareZenAppContent(m) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("删除")
        onNodeWithTag("palette-row-delete-s1").performClick()
        waitForIdle()
        // 二次确认在场，且服务器还在（没删）
        onNodeWithText("删除服务器").assertIsDisplayed()
        assertTrue(m.servers.any { it.id == "s1" }, "确认前不得删除")
        onNodeWithText("取消").performClick()
        waitForIdle()
        assertTrue(m.servers.any { it.id == "s1" }, "取消后不得删除")
    }

    @Test fun deleteCommandRemovesServerAfterConfirm() = runComposeUiTest {
        val m = modelWith(
            listOf(com.barezen.ssh.servers.Server("s1", "网关", "gw.example.com", user = "root")),
        )
        setContent { BareZenTheme { BareZenAppContent(m) } }
        onNodeWithTag("titlebar-key-命令面板").performClick()
        onNodeWithTag("command-palette-input").performTextInput("删除")
        onNodeWithTag("palette-row-delete-s1").performClick()
        waitForIdle()
        // 确认框里有两个「删除」：标题文案 + 按钮，按 testTag 更稳
        onNodeWithTag("confirm-delete-server").performClick()
        waitForIdle()
        assertTrue(m.servers.none { it.id == "s1" }, "确认后应删除服务器")
    }
}
