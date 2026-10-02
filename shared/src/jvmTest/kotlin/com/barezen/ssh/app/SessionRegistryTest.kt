// shared/src/jvmTest/kotlin/com/barezen/ssh/app/SessionRegistryTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.FakeSshSession
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 会话注册表状态机（设计 §7.1）。
 *
 * 用 [CoroutineScope] 承载"主线程"（Compose 重组线程的替身）：本套 fake 的 connect 不真正挂起，
 * 协程在 launch 调用点内联跑完，断言无需 flush 调度队列。挂起型用例（cancel）才需要真调度器。
 */
class SessionRegistryTest {

    private val web01 = Server("1", "web-01", "10.0.0.11", 22, "root")
    private val db01 = Server("2", "db-01", "10.0.0.12", 22, "root")

    private fun registry(client: SshClient, maxSessions: Int = SessionRegistry.DEFAULT_MAX_SESSIONS) =
        SessionRegistry(client, CoroutineScope(Dispatchers.Unconfined), maxSessions)

    /** connect 恒返回预置会话（可指定第 N 条用哪个），用于并存/上限/关闭用例。 */
    private class ScriptedSshClient(private val sessions: List<FakeSshSession>) : SshClient {
        var calls = 0
            private set
        override suspend fun connect(request: ConnectRequest): SshSession = sessions[calls++]
    }

    /** 建连失败：把失败信息原样带给注册表（不吞异常细节）。 */
    private class FailingSshClient(private val message: String) : SshClient {
        override suspend fun connect(request: ConnectRequest) = throw IllegalStateException(message)
    }

    @Test fun connectReturnsUniqueIdAndAdvancesToConnected() = runTest {
        val session = FakeSshSession()
        val registry = registry(ScriptedSshClient(listOf(session)))

        val id = registry.connect(web01, AuthMethod.Password("pw"))

        assertEquals(1, registry.sessions.size)
        val snapshot = assertNotNull(registry.sessions.single())
        assertEquals(id, snapshot.id)
        assertEquals(web01, snapshot.server)
        assertEquals("web-01", snapshot.title)
        assertEquals(ConnectionState.Connected(web01, 1L), snapshot.state)
        assertSame(session, snapshot.session)
        assertEquals(id, registry.activeId)          // 新会话即前台（确认连接后立刻看到它）
        assertNotNull(registry.active)
        assertSame(session, registry.active?.session)

        // id 唯一且不复用：关闭后再连，拿到的仍是新 id
        registry.close(id)
        val second = registry.connect(web01, AuthMethod.Password("pw"))
        assertTrue(second.raw > id.raw, "SessionId 必须单调递增、不复用")
    }

    @Test fun twoSessionsCoexistAndActivateSwitchesActive() = runTest {
        val first = FakeSshSession()
        val second = FakeSshSession()
        val registry = registry(ScriptedSshClient(listOf(first, second)))

        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val idB = registry.connect(db01, AuthMethod.Password("pw"))

        assertEquals(listOf(idA, idB), registry.sessions.map { it.id })   // 顺序 = 插入序 = 标签顺序
        assertEquals(idB, registry.activeId)                             // 后连的成为前台
        registry.activate(idA)
        assertEquals(idA, registry.activeId)
        assertSame(first, registry.active?.session)
        assertSame(first, registry.sessions.first().session)
        assertSame(second, registry.sessions.last().session)             // 后台会话保持连接

        registry.activate(idB)
        assertSame(second, registry.active?.session)
    }

    @Test fun closeRemovesAndActivatesLeftNeighbour() = runTest {
        val registry = registry(ScriptedSshClient(listOf(FakeSshSession(), FakeSshSession(), FakeSshSession())))
        val idA = registry.connect(web01, AuthMethod.Password("pw"))
        val idB = registry.connect(web01, AuthMethod.Password("pw"))
        val idC = registry.connect(web01, AuthMethod.Password("pw"))

        registry.activate(idB)
        registry.close(idB)
        assertEquals(listOf(idA, idC), registry.sessions.map { it.id })
        assertEquals(idA, registry.activeId)      // 关活动标签 -> 激活左邻

        registry.close(idA)                       // 关最左邻 -> 无左邻则补位到新列表首位
        assertEquals(idC, registry.activeId)

        registry.close(idC)                       // 关最后一个 -> 无活动会话
        assertTrue(registry.sessions.isEmpty())
        assertNull(registry.activeId)
        assertNull(registry.active)
    }

    @Test fun closeAllClosesEverySession() = runTest {
        val first = FakeSshSession()
        val second = FakeSshSession()
        val registry = registry(ScriptedSshClient(listOf(first, second)))
        registry.connect(web01, AuthMethod.Password("pw"))
        registry.connect(db01, AuthMethod.Password("pw"))

        registry.closeAll()

        assertTrue(registry.sessions.isEmpty())
        assertNull(registry.activeId)
        assertTrue(first.closed, "closeAll 必须真的关掉底层 SSH 会话（否则 keep-alive 线程泄漏）")
        assertTrue(second.closed)
    }

    @Test fun ninthSessionRejectedWithoutEvictingExisting() = runTest {
        val sessions = (1..SessionRegistry.DEFAULT_MAX_SESSIONS).map { FakeSshSession() }
        val registry = registry(ScriptedSshClient(sessions))
        val ids = (1..SessionRegistry.DEFAULT_MAX_SESSIONS).map { registry.connect(web01, AuthMethod.Password("pw")) }

        val error = assertFailsWith<SessionLimitException>(
            message = "达上限必须拒绝，而不是静默淘汰既有会话",
        ) {
            registry.connect(db01, AuthMethod.Password("pw"))
        }

        assertEquals(SessionRegistry.DEFAULT_MAX_SESSIONS, error.max)
        // 达限拒绝，不淘汰：既有会话一条不少、一条不关
        assertEquals(ids, registry.sessions.map { it.id })
        assertTrue(sessions.none { it.closed }, "拒绝新连接不得隐式关闭用户既有会话")
    }

    @Test fun connectedImpliesSessionAndOtherStatesNeverCarryOne() = runTest {
        val session = FakeSshSession()
        val registry = registry(ScriptedSshClient(listOf(session)))

        val id = registry.connect(web01, AuthMethod.Password("pw"))
        assertIs<ConnectionState.Connected>(registry.sessions.single().state)
        assertNotNull(registry.sessions.single().session)   // Connected 必有会话

        registry.disconnect(id)
        val after = registry.sessions.single()
        assertEquals(ConnectionState.Disconnected, after.state)
        assertNull(after.session)                           // 非 Connected 必无会话（延续 AppModelTest:87 的锁定）
        assertTrue(session.closed, "断开必须真的关掉底层 SSH 会话")
        assertTrue(registry.active?.session == null)        // 投影 shellSession 恒与状态成对
    }

    @Test fun failureKeepsFailedStateWithNullSessionAndRetryResetsInPlace() = runTest {
        val session = FakeSshSession()
        var attempt = 0
        val client = object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession =
                if (attempt++ == 0) throw IllegalStateException("auth failed") else session
        }
        val registry = registry(client)

        val id = registry.connect(web01, AuthMethod.Password("bad"))

        val failed = registry.sessions.single()
        assertEquals(id, failed.id)
        val failedState = assertIs<ConnectionState.Failed>(failed.state)
        assertEquals("auth failed", failedState.message)
        assertNull(failed.session)                          // 失败不得留下半开会话

        registry.retry(id)

        // retry 不发连接（本表不保存凭据）：只把该会话复位为可连接态，id 与 title 原地复用
        val reset = registry.sessions.single()
        assertEquals(id, reset.id)
        assertEquals("web-01", reset.title)
        assertIs<ConnectionState.Connecting>(reset.state)
        assertNull(reset.session)
        assertEquals(id, registry.activeId)

        // 重连正戏：调用方重开认证对话框后走 connect + close(id)，新会话接管原位置的顺序
        val fresh = registry.connect(web01, AuthMethod.Password("pw"))
        assertTrue(fresh.raw > id.raw)
        registry.close(id)
        val recovered = registry.sessions.single()
        assertEquals(fresh, recovered.id)
        assertEquals(ConnectionState.Connected(web01, 1L), recovered.state)
        assertSame(session, recovered.session)
    }

    /** shell 启动失败（第 1 刀新增原语）：关会话、就地落 Failed，不新增会话（标签不闪烁）。 */
    @Test fun failClosesSessionAndSetsFailedInPlace() = runTest {
        val session = FakeSshSession()
        val registry = registry(ScriptedSshClient(listOf(session)))
        val id = registry.connect(web01, AuthMethod.Password("pw"))

        registry.fail(id, "shell 启动失败：权限不足")

        val snapshot = registry.sessions.single()
        assertEquals(id, snapshot.id, "fail 必须原地复用 id，不得新增会话")
        assertEquals("web-01", snapshot.title)
        assertEquals(ConnectionState.Failed(web01, "shell 启动失败：权限不足"), snapshot.state)
        assertNull(snapshot.session, "失败态不得保留会话")
        assertTrue(session.closed, "fail 必须关掉底层 SSH 会话")
        assertEquals(id, registry.activeId, "失败会话保持前台，供 UI 显示失败态与重试")
    }

    @Test fun cancelRemovesConnectingSessionAndStopsItsCoroutine() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var observedCancellation = false
        val client = object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession {
                if (!entered.isCompleted) entered.complete(Unit)
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    observedCancellation = true
                    throw e
                }
                awaitCancellation()
            }
        }
        // 真调度器 + 真线程：cancel 必须能打断挂起中的 connect（Unconfined 下同样可取消）
        val registry = SessionRegistry(client, CoroutineScope(Dispatchers.Default))

        val id = registry.connect(web01, AuthMethod.Password("pw"))
        assertIs<ConnectionState.Connecting>(registry.sessions.single().state)
        assertEquals(id, registry.activeId)
        // 取消是跨线程异步到达的：先等处协程真的停在 connect 的 await 上，否则断言的是竞态
        entered.await()

        registry.cancel(id)

        assertTrue(registry.sessions.isEmpty(), "取消连接中的会话必须从列表移除")
        assertNull(registry.activeId)
        // 取消跨线程送达：轮询等待，不做固定 sleep（慢机器上固定等待必然变成竞态）
        var waited = 0L
        while (!observedCancellation && waited < 5_000) { delay(5); waited += 5 }
        assertTrue(observedCancellation, "cancel 必须真的掐掉在途连接协程，而不是只改状态")
        gate.complete(Unit)
        assertNull(registry.active)
    }
}
