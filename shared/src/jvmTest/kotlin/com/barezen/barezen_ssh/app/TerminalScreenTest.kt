// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/TerminalScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.screens.TerminalScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

class TerminalScreenTest {
    private fun model(state: ConnectionState) = AppModel(
        repo = InMemoryServerRepository(listOf(Server("1", "web-01", "10.0.0.11", 22, "root"))),
        ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
        scope = CoroutineScope(Dispatchers.Unconfined),
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
}
