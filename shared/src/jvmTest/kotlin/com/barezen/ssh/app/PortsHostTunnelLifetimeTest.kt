// shared/src/jvmTest/kotlin/com/barezen/ssh/app/PortsHostTunnelLifetimeTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ssh.forward.ForwardEntryState
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 第 4 刀红线（**UI 级**锁定）：在端口转发页上切换标签，用户正在使用的隧道不得被切断。
 *
 * 缺陷的形状在接线层而不是注册表层——旧 PortsHost 以 LaunchedEffect(active?.id) 为 key，
 * 切走标签就重跑并 closeAll()，切回时按 autoStart 重建（非 autoStart 的规则干脆不恢复）。
 * 故本用例直接经 [BareZenAppContent] 组合真实 Host，并在**组合之后**切换活动会话：
 * 只有真实走过一次重组，才能证明 DisposableEffect + closeAll 确实已被移除。
 */
class PortsHostTunnelLifetimeTest {

    /** 记录隧道创建/关闭的会话替身；两条会话各一份，便于断言不串台。 */
    private class TunnelSession(private val label: String) : SshSession {
        val created = mutableListOf<FakeTunnel>()

        override fun pingMs(): Long = 1L
        override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
        override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
            error("本测试不挂终端")

        override fun newSftp(): SftpFs = error("本测试不开 SFTP")

        override fun startForward(spec: ForwardSpec): ForwardTunnel =
            FakeTunnel(spec, label).also { created += it }

        override fun close() = Unit
    }

    private class FakeTunnel(override val spec: ForwardSpec, val label: String) : ForwardTunnel {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private class QueuedClient(private val sessions: List<SshSession>) : SshClient {
        private var calls = 0
        override suspend fun connect(request: ConnectRequest): SshSession = sessions[calls++]
    }

    private val web01 = Server("srv-a", "web-01", "127.0.0.1", 22, "root")
    private val db01 = Server("srv-b", "db-01", "127.0.0.1", 22, "root")
    private val spec = ForwardSpec(com.barezen.ssh.ssh.ForwardKind.LOCAL, 18081, "db.internal", 5432)

    private fun model(a: SshSession, b: SshSession) = AppModel(
        repo = InMemoryServerRepository(listOf(web01, db01)),
        ssh = QueuedClient(listOf(a, b)),
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        // 与生产接线（AppModel.real）逐字同构：资源归会话，Host 只订阅
        resourcesFactory = { session -> JvmSessionResources(CoroutineScope(Dispatchers.Unconfined), session) },
    )

    /**
     * 在 A 上建立隧道 -> 切到 B -> 切回 A：隧道实例从未 close，且仍是同一个 manager 实例上的条目。
     *
     * 旧实现（Host 持 manager + 切走 closeAll）在这里会看到 A 的 manager 条目被清空、隧道被关闭。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun switchingTabsOnPortsScreenKeepsTunnelAlive() = runComposeUiTest {
        val sessionA = TunnelSession("a")
        val sessionB = TunnelSession("b")
        val m = model(sessionA, sessionB)
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.PORTS)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        // A 的活动会话资源就是转发页实际订阅的那一个
        val idA = assertNotNull(m.registry.activeId)
        val resourcesA = assertNotNull(
            m.registry.sessions.first { it.id == idA }.resources as? JvmSessionResources,
        )
        val entry = resourcesA.forwardManager.apply(spec)
        val tunnel = assertNotNull(entry.tunnel) as FakeTunnel
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("活动转发：1 条").fetchSemanticsNodes().isNotEmpty()
        }

        // 组合之后新开 B 并成为活动会话：旧 Host 会在此 closeAll A 的隧道
        m.startConnect(db01, AuthMethod.Password("x"))
        m.navigate(Destination.PORTS)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("活动转发：0 条").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!tunnel.closed, "切到另一条会话的标签，不得关闭用户正在使用的隧道")

        // 切回 A：隧道仍在，且是被同一个 manager 持有的同一条 ACTIVE 条目
        m.registry.activate(idA)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("活动转发：1 条").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!tunnel.closed, "切回标签后隧道必须仍然活着（方案 A 的唯一存在理由）")
        val back = assertNotNull(
            m.registry.sessions.first { it.id == idA }.resources as? JvmSessionResources,
        ).forwardManager
        assertTrue(back === resourcesA.forwardManager, "切回必须复用同一 ForwardManager 实例")
        assertTrue(back.entries.value.single().state == ForwardEntryState.ACTIVE)
        assertTrue(back.entries.value.single().tunnel === tunnel, "活动转发列表不得丢条目")
    }

    /** 组合期间关闭活动标签：隧道必须随会话释放（关闭即释放，不保活）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun closingActiveTabFromPortsScreenReleasesTunnel() = runComposeUiTest {
        val sessionA = TunnelSession("a")
        val sessionB = TunnelSession("b")
        val m = model(sessionA, sessionB)
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.PORTS)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        val idA = assertNotNull(m.registry.activeId)
        val resourcesA = assertNotNull(
            m.registry.sessions.first { it.id == idA }.resources as? JvmSessionResources,
        )
        val tunnel = assertNotNull(resourcesA.forwardManager.apply(spec).tunnel) as FakeTunnel
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("活动转发：1 条").fetchSemanticsNodes().isNotEmpty()
        }

        m.registry.close(idA)

        assertTrue(tunnel.closed, "关闭标签必须释放隧道")
        assertTrue(resourcesA.forwardManager.entries.value.isEmpty())
    }
}
