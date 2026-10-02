// shared/src/jvmTest/kotlin/com/barezen/ssh/app/ConnectOverlayActiveSessionTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 连接中/失败覆盖层必须跟着**活动会话**，且保持既有语义：终端屏不叠加（终端屏自理状态机）、
 * 失败框可关闭而横幅仍在。
 *
 * 关键回归：两条会话**同时 Connecting** 时，覆盖层只该显示活动会话的那一条。
 * 若覆盖层仍读单值投影（改造前的 model.connection），并发建连下它只能反映其一——
 * 此时用户在 A 标签上看到的是 B 的连接进度。
 */
class ConnectOverlayActiveSessionTest {

    private val web01 = Server("srv-a", "web-01", "10.0.0.11", 22, "root")
    private val db01 = Server("srv-b", "db-01", "10.0.0.12", 22, "root")

    /** 悬停连接：两条会话各有自己的闸门，均停放行前状态保持 Connecting。 */
    private class HangingSsh : SshClient {
        private val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        private var calls = 0

        override suspend fun connect(request: ConnectRequest): SshSession {
            gates[calls++].await()
            error("悬停连接被放行——测试不应到达此处")
        }
    }

    private fun model(ssh: SshClient) = AppModel(
        repo = InMemoryServerRepository(listOf(web01, db01)),
        ssh = ssh,
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    /**
     * 两条会话同时 Connecting，且活动会话已切回 A：覆盖层必须显示 A 的服务器名。
     * 读单值投影的实现在这里会显示后连的 B。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun connectingOverlayFollowsActiveSessionWhenTwoAreConnecting() = runComposeUiTest {
        val m = model(HangingSsh())
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("x"))
        m.requestConnect(db01)
        m.confirmConnect(AuthMethod.Password("x"))
        assertEquals(2, m.registry.sessions.size, "两条会话应同时处于连接中")
        val idA = m.registry.sessions.first { it.server == web01 }.id
        m.registry.activate(idA)
        m.navigate(Destination.SERVERS)   // 覆盖层只在非终端页叠加

        setContent { BareZenTheme { BareZenAppContent(m) } }

        onNodeWithText("正在连接 web-01…").assertIsDisplayed()
        assertTrue(
            onAllNodesWithText("正在连接 db-01…").fetchSemanticsNodes().isEmpty(),
            "覆盖层只反映活动会话的连接进度，不得显示非活动会话",
        )
    }

    /** 覆盖层「取消」只掐活动会话，另一条继续连接（取消不得误伤兄弟会话）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun cancelInOverlayOnlyCancelsActiveSession() = runComposeUiTest {
        val m = model(HangingSsh())
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("x"))
        m.requestConnect(db01)
        m.confirmConnect(AuthMethod.Password("x"))
        val idA = m.registry.sessions.first { it.server == web01 }.id
        m.registry.activate(idA)
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        onNodeWithText("取消").performClick()

        assertEquals(1, m.registry.sessions.size, "只应移除被取消的那条会话")
        assertEquals(db01, m.registry.sessions.single().server)
    }

    /**
     * 终端屏不叠加覆盖层（既有语义）：壳体连接中对话框与终端屏内连接中面板不得同现两份。
     *
     * 覆盖层在 \`current == TERMINAL\` 时提前返回，故终端页上「正在连接 X…」恰有一个节点
     * （来自 TerminalScreen 的 ConnectingPane），而不是两个。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun overlayIsNotDrawnOnTerminalScreen() = runComposeUiTest {
        val m = model(HangingSsh())
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("x"))
        m.navigate(Destination.TERMINAL)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        assertEquals(
            1,
            onAllNodesWithText("正在连接 web-01…").fetchSemanticsNodes().size,
            "终端屏不叠加壳级连接覆盖层，避免同文案双弹",
        )
    }

    /** 失败框可关闭、横幅仍在（既有语义）——关闭只影响对话框，不改变会话状态。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun failedOverlayDismissLeavesBannerAndSessionState() = runComposeUiTest {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("Auth fail")
        })
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        // 对话框与横幅同文案：先 2 个节点，关掉对话框后剩横幅 1 个
        assertEquals(2, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("取消").performClick()
        assertEquals(1, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("重试").assertIsDisplayed()
    }

    /** 无活动会话时不渲染任何覆盖层（不能凭残留状态弹框）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun noOverlayWhenThereIsNoActiveSession() = runComposeUiTest {
        val m = model(HangingSsh())
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("x"))
        m.cancelConnect()
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        assertTrue(onAllNodesWithText("正在连接 web-01…").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("取消").fetchSemanticsNodes().isEmpty())
    }
}
