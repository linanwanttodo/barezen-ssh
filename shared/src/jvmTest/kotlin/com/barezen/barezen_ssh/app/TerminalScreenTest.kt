// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/TerminalScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.settings.NoopSettingsRepository
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.screens.TerminalScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalScreenTest {
    private fun model(state: ConnectionState) = AppModel(
        repo = InMemoryServerRepository(listOf(Server("1", "web-01", "10.0.0.11", 22, "root"))),
        ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    ).apply { applyConnectionForTest(state) }

    @OptIn(ExperimentalTestApi::class)
    fun assertCta(m: AppModel) = runComposeUiTest {
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
    }

    @Test fun disconnectedShowsCta() { assertCta(model(ConnectionState.Disconnected)) }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedShowsErrorAndRetry() = runComposeUiTest {
        val m = model(ConnectionState.Failed(Server("1", "web-01", "10.0.0.11", 22, "root"), "auth failed"))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("连接失败：auth failed").assertIsDisplayed()
        onNodeWithText("重试").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedShowsStatusBarWithLatency() = runComposeUiTest {
        val m = model(ConnectionState.Connected(Server("1", "web-01", "10.0.0.11", 22, "root"), 12))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("已连接").assertIsDisplayed()
        onNodeWithText("SSH 往返 12 ms").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun tabStripHoldsDisabledPlaceholderButtons() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        // 「+」按 GREEN 指定走 IconButton 图标语义 → 断 contentDescription（见报告）
        onNodeWithContentDescription("多标签（M4 占位）").assertIsNotEnabled()
        onNodeWithText("助手").assertIsNotEnabled() // AI 助手 M5 占位
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun statusBarShowsMetricSlotsWithoutFakeValues() = runComposeUiTest {
        val m = model(ConnectionState.Connected(Server("1", "web-01", "10.0.0.11", 22, "root"), 12))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("已连接").assertIsDisplayed()
        // brief 写 onNodeWithText("web-01")：改版后标签条会话标签 + 状态栏各渲染一次服务器名
        //（设计 mock 同样两处），多节点歧义 → 断两处（plan 允许因新节点断言歧义微调）
        assertEquals(2, onAllNodesWithText("web-01").fetchSemanticsNodes().size)
        onNodeWithText("SSH 往返 12 ms").assertIsDisplayed()
        onNodeWithText("负载 —").assertIsDisplayed()
        onNodeWithText("内存 —").assertIsDisplayed()
        onNodeWithText("运行 —").assertIsDisplayed()
        // brief 写 exact 计数：`负载 —`（同节点整串，GREEN 指定）与 exact `—` 逐条目全等互斥
        //（hasText substring=false 是 Text 条目全等）→ 子串计数，保持「恰好 3 个指标位、无数字」不变量
        assertEquals(3, onAllNodesWithText("—", substring = true).fetchSemanticsNodes().size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedStatusBarShowsOnlyDotAndLabel() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("未连接").assertIsDisplayed()
        onNodeWithText("负载 —").assertDoesNotExist() // 未连接态不渲染指标位（照设计空态）
    }
}
