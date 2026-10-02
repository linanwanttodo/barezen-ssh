// shared/src/jvmTest/kotlin/com/barezen/ssh/app/TerminalScreenTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ui.screens.TerminalScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

class TerminalScreenTest {
    private fun model(state: ConnectionState) = AppModel(
        repo = InMemoryServerRepository(listOf(Server("1", "web-01", "10.0.0.11", 22, "root"))),
        ssh = ControllableSshClient(),
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

    // 原 connectedShowsStatusBarWithLatency / statusBarShowsMetricSlotsWithoutFakeValues（共 2 例）已删除。
    // 理由见终报：二者断言的都是「Connected 且 shellSession == null」下的渲染结果，
    // 而该状态在会话注册表的新不变量（Connected 必有 session）下不可表达；
    // 一旦 Connected 携带真会话，TerminalScreen 会挂真 TerminalView（SwingPanel interop），
    // 在无显示环境的 headless 测试中抛 LocalInteropContainer not provided。
    // 故「状态栏延迟文案」「恰好 3 个指标位且无数字」两条不变量在本环境不可验证，
    // 由 T-4 终态在有显示环境下补验，不以恒真/削弱断言替代。

    @OptIn(ExperimentalTestApi::class)
    @Test fun tabStripPlusEnabledAssistantStillPlaceholder() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        // 刀5 落地多标签：「+」由禁用占位转正为可用入口（spec §4.1 Q5，跳服务器列表选机），
        // contentDescription 由「多标签（M4 占位）」同步改为动作语义「新建终端」（见终报偏离清单）
        onNodeWithContentDescription("新建终端").assertIsEnabled()
        onNodeWithText("助手").assertIsNotEnabled() // AI 助手 M5 占位
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedStatusBarShowsOnlyDotAndLabel() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("未连接").assertIsDisplayed()
        onNodeWithText("负载 —").assertDoesNotExist() // 未连接态不渲染指标位（照设计空态）
    }
}
