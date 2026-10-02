// shared/src/jvmTest/kotlin/com/barezen/ssh/app/SessionResourcesTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ssh.forward.ForwardEntryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 第 4 刀（方案 A）：端口转发所有权上移到**会话**。
 *
 * 红线的形状：Host 随标签切换而重建，若隧道管理器归 Host 持有，切走标签就会把用户正在用的
 * 隧道一起关掉。故本用例组锁定三件事——切走保持（不 closeAll）、关闭即释放、每会话只自动启用一次。
 *
 * 测试全程走 fake：registry 的资源工厂注入 [TunnelSession]，其 startForward 记录建了几条隧道，
 * 零真实 SSH。
 */
class SessionResourcesTest {

    private val web01 = Server("srv-a", "web-01", "10.0.0.11", 22, "root")
    private val db01 = Server("srv-b", "db-01", "10.0.0.12", 22, "root")
    private val spec = ForwardSpec(ForwardKind.LOCAL, 18081, "db.internal", 5432)

    /** 记录隧道创建与关闭的会话替身；其余能力本用例组用不到。 */
    private class TunnelSession : SshSession {
        val created = mutableListOf<FakeTunnel>()

        override fun pingMs(): Long = 1L
        override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
        override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
            error("本测试不挂终端")

        override fun newSftp(): SftpFs = error("本测试不开 SFTP")

        override fun startForward(spec: ForwardSpec): ForwardTunnel =
            FakeTunnel(spec).also { created += it }

        override fun close() = Unit
    }

    private class FakeTunnel(override val spec: ForwardSpec) : ForwardTunnel {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    /** 按调用顺序发会话的客户端：第 n 次建连拿第 n 条（两条会话各有独立底层连接）。 */
    private class QueuedClient(private val sessions: List<SshSession>) : SshClient {
        private var calls = 0
        override suspend fun connect(request: ConnectRequest): SshSession = sessions[calls++]
    }

    /** registry 的会话级资源工厂：每条会话一个 [JvmSessionResources]，与本刀生产接线逐字同构。 */
    private fun registry(client: SshClient) = SessionRegistry(
        ssh = client,
        scope = CoroutineScope(Dispatchers.Unconfined),
        resourcesFactory = { session -> JvmSessionResources(CoroutineScope(Dispatchers.Unconfined), session) },
    )

    /** 取某条会话的资源（生产侧 Host 就是这么拿的）。 */
    private fun resourcesOf(registry: SessionRegistry, id: SessionId): JvmSessionResources =
        assertNotNull(registry.sessions.first { it.id == id }.resources as? JvmSessionResources)

    // ---- 红线的核心：切走标签不断隧道 ----

    /**
     * 建立隧道 -> 切到另一条会话 -> 切回：**同一个** JvmSessionResources 与 ForwardManager 实例、
     * 同一条 ACTIVE 条目、隧道从未被 close。
     *
     * 以 Host 持有 manager 的实现（切走即 closeAll）在这里会拿到空 entries 与已关闭的隧道。
     */
    @Test fun switchingTabsKeepsTunnelAliveAndReusesSameManager() = runTest {
        val sessionA = TunnelSession()
        val sessionB = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA, sessionB)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val idB = registry.connect(db01, AuthMethod.Password("pw"))

        val managerA = resourcesOf(registry, idA).forwardManager
        val entry = managerA.apply(spec)
        val tunnel = assertNotNull(entry.tunnel) as FakeTunnel
        assertEquals(ForwardEntryState.ACTIVE, entry.state)

        registry.activate(idB)
        assertEquals(idB, registry.activeId)
        assertTrue(!tunnel.closed, "切走标签不得关闭用户正在使用的隧道")
        assertEquals(
            listOf(entry),
            managerA.entries.value,
            message = "切走后活动转发列表不得被清空（Host 若在切走时 closeAll 会在这里露馅）",
        )

        registry.activate(idA)
        val back = resourcesOf(registry, idA).forwardManager
        assertSame(managerA, back, "切回必须复用同一 ForwardManager 实例，隧道句柄不丢")
        assertEquals(listOf(entry), back.entries.value)
        assertSame(ForwardEntryState.ACTIVE, back.entries.value.single().state)
        assertTrue(!tunnel.closed, "切回后隧道仍未被关闭")
    }

    /** 两条会话各自一套资源：B 看不到 A 的条目，A 的隧道建在 A 的会话上（不串台）。 */
    @Test fun eachSessionOwnsItsOwnForwardManager() = runTest {
        val sessionA = TunnelSession()
        val sessionB = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA, sessionB)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val idB = registry.connect(db01, AuthMethod.Password("pw"))

        val resourcesA = resourcesOf(registry, idA)
        val resourcesB = resourcesOf(registry, idB)
        assertTrue(resourcesA !== resourcesB)

        resourcesA.forwardManager.apply(spec)
        assertTrue(resourcesB.forwardManager.entries.value.isEmpty(), "B 不得看到 A 的转发条目")
        assertEquals(1, sessionA.created.size)
        assertEquals(0, sessionB.created.size, "A 的隧道必须建在 A 的会话上")
    }

    /**
     * autoStart 只在**会话首次**进入活动态时执行一次：切走再切回不得重复读取规则、重复 apply。
     *
     * 重复 apply 对同 spec 的 ACTIVE 条目虽幂等（ForwardManager 直接返回既有条目），但真正要挡的是
     * 「用户手动关掉的转发被切标签重新打开」——那等于无人值守地多开一个本机监听端口（安全敏感）。
     */
    @Test fun autoStartRunsOncePerSessionRegardlessOfTabSwitching() = runTest {
        val sessionA = TunnelSession()
        val sessionB = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA, sessionB)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val idB = registry.connect(db01, AuthMethod.Password("pw"))

        val resourcesA = resourcesOf(registry, idA)
        var loads = 0
        assertTrue(
            resourcesA.ensureAutoStartApplied {
                loads++
                listOf(com.barezen.ssh.servers.ForwardRule.of(spec))
            },
            "首次进入活动会话应执行自动启用",
        )
        assertTrue(
            !resourcesA.ensureAutoStartApplied { error("切回标签不得再次读取规则文件") },
            "自动启用每会话只执行一次",
        )
        assertEquals(1, loads)
        assertEquals(1, sessionA.created.size, "重复触发不得再开一条隧道")

        registry.activate(idB)
        registry.activate(idA)
        val back = resourcesOf(registry, idA)
        assertTrue(!back.ensureAutoStartApplied { error("切回标签不得再次读取规则文件") })
        assertEquals(1, sessionA.created.size)
    }

    // ---- 关闭即释放（不保活） ----

    /** 关闭标签：隧道 close + 条目清空 + 资源标记已释放。 */
    @Test fun closeReleasesSessionTunnels() = runTest {
        val sessionA = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val resources = resourcesOf(registry, idA)
        val tunnel = assertNotNull(resources.forwardManager.apply(spec).tunnel) as FakeTunnel

        registry.close(idA)

        assertTrue(tunnel.closed, "关闭标签必须释放隧道——不保活是用户决策")
        assertTrue(resources.forwardManager.entries.value.isEmpty())
        assertTrue(resources.closed)
        assertTrue(registry.sessions.isEmpty())
    }

    @Test fun closeAllReleasesEverySessionTunnel() = runTest {
        val sessionA = TunnelSession()
        val sessionB = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA, sessionB)))
        registry.connect(web01, AuthMethod.Password("pw"))
        registry.connect(db01, AuthMethod.Password("pw"))
        val resources = registry.sessions.map { assertNotNull(it.resources as? JvmSessionResources) }
        val tunnels = resources.map { assertNotNull(it.forwardManager.apply(spec).tunnel) as FakeTunnel }

        registry.closeAll()

        assertTrue(tunnels.all { it.closed }, "closeAll 必须释放全部会话的隧道")
        assertTrue(resources.all { it.forwardManager.entries.value.isEmpty() })
        assertTrue(resources.all { it.closed })
        assertTrue(registry.sessions.isEmpty())
    }

    @Test fun disconnectReleasesSessionTunnels() = runTest {
        val sessionA = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val resources = resourcesOf(registry, idA)
        val tunnel = assertNotNull(resources.forwardManager.apply(spec).tunnel) as FakeTunnel

        registry.disconnect(idA)

        assertTrue(tunnel.closed, "断开必须释放隧道（会话没了隧道必然不通，不做保活）")
        assertTrue(resources.forwardManager.entries.value.isEmpty())
        assertNull(registry.sessions.single().resources, "断开后不得残留会话资源")
        assertNull(registry.sessions.single().session)
    }

    @Test fun failReleasesSessionTunnels() = runTest {
        val sessionA = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val resources = resourcesOf(registry, idA)
        val tunnel = assertNotNull(resources.forwardManager.apply(spec).tunnel) as FakeTunnel

        registry.fail(idA, "shell 启动失败")

        assertTrue(tunnel.closed)
        assertTrue(resources.forwardManager.entries.value.isEmpty())
        assertNull(registry.sessions.single().resources)
    }

    /** 建连失败本就不该有资源。 */
    @Test fun failedConnectLeavesNoResources() = runTest {
        val registry = registry(object : SshClient {
            override suspend fun connect(request: ConnectRequest) = throw IllegalStateException("auth failed")
        })
        registry.connect(web01, AuthMethod.Password("bad"))
        assertNull(registry.sessions.single().resources, "失败态不得挂会话资源")
        assertEquals(ConnectionState.Failed(web01, "auth failed"), registry.sessions.single().state)
    }

    // ---- 幂等与惰性 ----

    @Test fun closeIsIdempotent() = runTest {
        val sessionA = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val resources = resourcesOf(registry, idA)
        assertNotNull(resources.forwardManager.apply(spec).tunnel)

        resources.close()
        resources.close()

        assertTrue(resources.closed)
        assertTrue(resources.forwardManager.entries.value.isEmpty())
    }

    /**
     * 没碰过实际转发的会话：释放时不得凭空创建隧道管理器。
     *
     * 惰性不是省事，是正确性问题——一个从未打开过转发面板的会话不该有任何本机监听端口，
     * close 也不该为了「关掉不存在的隧道」而把它造出来再关。
     */
    @Test fun closingUntouchedSessionNeverCreatesTunnelManager() = runTest {
        val sessionA = TunnelSession()
        val registry = registry(QueuedClient(listOf(sessionA)))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val resources = resourcesOf(registry, idA)

        resources.close()

        assertEquals(0, sessionA.created.size, "未使用转发的会话不得创建任何隧道")
        assertTrue(resources.closed)
    }

    // ---- 工厂语义 ----

    /**
     * 不注入工厂＝不创建任何会话资源：这是 SessionRegistry 新参数的默认行为，
     * 既保证既有调用方零改动等价，也保证产品代码里不存在测试专用分支。
     */
    @Test fun registryWithoutFactoryCreatesNoResources() = runTest {
        val session = TunnelSession()
        val registry = SessionRegistry(QueuedClient(listOf(session)), CoroutineScope(Dispatchers.Unconfined))

        val id = registry.connect(web01, AuthMethod.Password("pw"))

        assertEquals(ConnectionState.Connected(web01, 1L), registry.sessions.single().state)
        assertSame(session, registry.sessions.single().session)
        assertNull(registry.sessions.single().resources)

        registry.close(id)
        assertTrue(registry.sessions.isEmpty())
    }
}
