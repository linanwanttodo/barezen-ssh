// shared/src/jvmTest/kotlin/com/barezen/ssh/app/ServersScreenMultiSessionTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ui.screens.ServersScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 服务器列表的「已连接」语义在多会话下的定稿（spec §3.5）。
 *
 * 一条会话时徽标与改造前逐字一致（「已连接 · N ms」）；**多条会话时不得撒谎**——
 * 延迟是某一条会话的实测值、不是该服务器的属性，故只能显示「已连接 · N 个会话」，
 * 且仅当该服务器就是活动会话时才附带延迟。
 */
class ServersScreenMultiSessionTest {

    /** 带指定往返时延的会话替身（FakeSshSession 的 pingMs 固定为 1，无法表达两条不同延迟）。 */
    private class LatencySession(private val latencyMs: Long) : SshSession {
        override fun pingMs(): Long = latencyMs
        override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
        override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
            error("本测试不挂终端")

        override fun newSftp(): SftpFs = error("本测试不开 SFTP")
        override fun startForward(spec: ForwardSpec): ForwardTunnel = error("本测试不建隧道")
        override fun close() = Unit
    }

    private val web01 = Server("srv-a", "web-01", "10.0.0.11", 22, "root")
    private val db01 = Server("srv-b", "db-01", "10.0.0.12", 22, "root")

    private fun model() = AppModel(
        repo = InMemoryServerRepository(listOf(web01, db01)),
        ssh = QueuedSshClient(listOf(LatencySession(24L), LatencySession(31L), LatencySession(47L))),
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    @OptIn(ExperimentalTestApi::class)
    @Test fun singleSessionKeepsCurrentLatencyBadge() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }

        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()
        onNodeWithText("1 台服务器未连接").assertIsDisplayed()
    }

    /**
     * 同一服务器连两条会话：不得再显示单条延迟（那是其中一条的值，会误导），
     * 改为会话条数；未连接计数也不得把这台机器算成未连接。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun twoSessionsOnSameServerReportCountWithoutLatency() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }

        onNodeWithText("已连接 · 2 个会话").assertIsDisplayed()
        assertEquals(
            emptyList(),
            onAllNodesWithText("已连接 · 31 ms").fetchSemanticsNodes().map { it.id },
            "同一服务器有两条会话时不得只报其中一条的延迟",
        )
        onNodeWithText("1 台服务器未连接").assertIsDisplayed()
    }

    /**
     * 两条会话分属两台服务器：两条都算已连接（未连接计数为 0），
     * 且只有**活动会话**那条服务器显示延迟——非活动会话的延迟不该冒充成活动会话的。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun bothServersCountAsConnectedAndOnlyActiveShowsLatency() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.startConnect(db01, AuthMethod.Password("x"))   // 后连者成为活动会话
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }

        onNodeWithText("已连接 · 31 ms").assertIsDisplayed()   // db-01 是活动会话
        onNodeWithText("已连接 · 24 ms").assertDoesNotExist()  // web-01 已连接但非活动
        onNodeWithText("0 台服务器未连接").assertIsDisplayed()
    }

    /** 全部重连仍是禁用占位：本刀不启用（spec §3.5 明文）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun reconnectAllStaysDisabledUnderMultipleSessions() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.startConnect(db01, AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }

        onNodeWithText("全部重连").assertIsNotEnabled()
    }

    /** 失败横幅只在**活动会话**失败时出现；非活动会话的失败不该顶掉活动会话的列表态。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun failedBannerOnlyReflectsActiveSession() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.startConnect(db01, AuthMethod.Password("x"))
        val idA = m.registry.sessions.first { it.server == web01 }.id
        val idB = m.registry.sessions.first { it.server == db01 }.id
        m.registry.fail(idA, "auth failed")

        // 活动会话是 B（未失败）：不显示失败横幅
        assertEquals(idB, m.registry.activeId)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        assertTrue(onAllNodesWithText("连接失败：auth failed").fetchSemanticsNodes().isEmpty())

        // 切到失败的 A：横幅如实出现
        m.registry.activate(idA)
        onNodeWithText("连接失败：auth failed").assertIsDisplayed()
    }

    /** 卡片状态行逐卡判定：连上的卡「已连接」，其余仍是「未连接」。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun cardsReportTheirOwnConnectionState() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }

        // db-01 未连接：仍显示「未连接」与「连接」按钮
        onNodeWithText("未连接").assertIsDisplayed()
        onNodeWithText("连接").assertIsDisplayed()
        // 未连接的卡不显示统计瓦片（不造数）
        onNodeWithText("连接后查看负载、内存和运行时间。").assertIsDisplayed()
    }

    /** 断开后该服务器不再计入已连接（徽标回落「未连接」）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectDropsServerFromConnectedSet() = runComposeUiTest {
        val m = model()
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.SERVERS)
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()

        m.disconnect()
        onNodeWithText("已连接 · 24 ms").assertDoesNotExist()
        onNodeWithText("2 台服务器未连接").assertIsDisplayed()
    }
}
