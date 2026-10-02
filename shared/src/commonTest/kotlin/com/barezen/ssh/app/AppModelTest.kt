package com.barezen.ssh.app

import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppModelTest {
    private val server = Server("1", "web-01", "10.0.0.11", 22, "root")

    private fun model(client: SshClient) = AppModel(
        repo = InMemoryServerRepository(listOf(server)),
        ssh = client,
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    @Test fun connectHappyPathEndsConnected() = runBlocking {
        val session = object : SshSession {
            override fun pingMs() = 12L
            override fun exec(command: String, timeoutMs: Long) = ExecResult(0, "", "")
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
            override fun newSftp() = error("unused")
            override fun startForward(spec: ForwardSpec) = error("unused")
            override fun close() {}
        }
        val m = model(object : SshClient { override suspend fun connect(request: ConnectRequest) = session })
        m.startConnect(server, AuthMethod.Password("pw"))
        assertEquals(ConnectionState.Connected(server, 12L), m.connection)
    }

    @Test fun connectFailureCarriesMessage() = runBlocking {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = throw IllegalStateException("auth failed")
        })
        m.startConnect(server, AuthMethod.Password("bad"))
        val state = m.connection
        assertTrue(state is ConnectionState.Failed && state.message.contains("auth failed"))
    }

    /**
     * 防重入（per-SessionId 语义）：**同一条会话**不会重复建连——注册表为每次 connect 分配
     * 新的 SessionId，而每条会话只允许一个在途连接协程（registry.connect 内
     * connectJobs.remove(id)?.cancel() + 每会话独立终态槽位）。
     *
     * 单会话时代该守卫是全局的（"连接中忽略一切新连接请求"）；多会话下它退化为 per-SessionId
     * （见本类末尾 concurrentConnectToDifferentServersIsAllowed 与 spec §2.3 Q3）。
     *
     * 本用例锁定三件事（原用例的意图逐条保留）：
     *   1. Connecting 期间状态不被覆盖；
     *   2. 连接未完成不预存会话（状态与会话的成对不变量）；
     *   3. 放行后完整收尾为 Connected 且会话与状态一致。
     */
    @Test fun sameSessionIsNotConnectedTwiceWhileConnecting() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var connectCalls = 0
        val firstSession = object : SshSession {
            override fun pingMs() = 12L
            override fun exec(command: String, timeoutMs: Long) = ExecResult(0, "", "")
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
            override fun newSftp() = error("unused")
            override fun startForward(spec: ForwardSpec) = error("unused")
            override fun close() {}
        }
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession {
                connectCalls++
                if (connectCalls == 1) gate.await() // 第一次连接悬停在 connect 内（模拟多秒建连）
                return firstSession
            }
        })

        m.startConnect(server, AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Connecting)
        assertEquals(1, connectCalls)
        assertNull(m.shellSession) // 连接未完成不预存会话：状态与会话不脱节

        // 同一条会话的第二次 connect 会分配新 id（= 用户主动新建标签），因此这里断言的是
        // 旧守卫真正防的东西：**在途会话不被任何后续路径改写**。活动会话切换到新 id 后，
        // 原来那条会话的状态仍是它自己写入的结果。
        val firstId = m.registry.sessions.single().id
        m.registry.connect(server, AuthMethod.Password("pw2"))
        assertEquals(2, connectCalls, "每次 connect 都是新会话，各起自己的建连协程")
        val first = m.registry.sessions.first { it.id == firstId }
        assertTrue(first.state is ConnectionState.Connecting, "新会话不得改写前一条会话的状态")

        // 投影跟随活动会话：新会话（未被 gate 拦）已当场 Connected，故投影随之改变；
        // 旧会话仍是它自己写入的 Connecting——两条会话的终态互不干扰。
        assertTrue(m.connection is ConnectionState.Connected)
        assertEquals(2, m.registry.sessions.size)

        // 放行：两条会话各自独立收尾，各自持有会话（状态与会话成对一致）
        gate.complete(Unit)
        yield()
        val sessionById = m.registry.sessions.associateBy { it.id }
        assertSame(firstSession, sessionById.getValue(firstId).session)
        assertTrue(sessionById.getValue(firstId).state is ConnectionState.Connected)
        val newId = m.registry.sessions.first { it.id != firstId }.id
        assertSame(firstSession, sessionById.getValue(newId).session)
        assertTrue(sessionById.getValue(newId).state is ConnectionState.Connected)
        // 投影 = 活动会话（后连的那条）
        assertEquals(sessionById.getValue(m.registry.activeId!!).state, m.connection)
    }

    /**
     * 并发连接**不同**服务器是被允许的（spec §2.3 / 决策 Q3）——这正是多标签的意义。
     *
     * 显式锁定"全局互斥已废除"这一新契约：第一台仍停在 Connecting 时，对第二台的 startConnect
     * 必须真正发起第二次 ssh.connect，且两条会话并存于注册表（各自的终态互不覆盖）。
     * 旧行为（全局守卫）下本用例会看到 connectCalls == 1。
     */
    @Test fun concurrentConnectToDifferentServersIsAllowed() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val db01 = Server("2", "db-01", "10.0.0.12", 22, "root")
        var connectCalls = 0
        val secondSession = object : SshSession {
            override fun pingMs() = 31L
            override fun exec(command: String, timeoutMs: Long) = ExecResult(0, "", "")
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
            override fun newSftp() = error("unused")
            override fun startForward(spec: ForwardSpec) = error("unused")
            override fun close() {}
        }
        val m = AppModel(
            repo = InMemoryServerRepository(listOf(server, db01)),
            ssh = object : SshClient {
                override suspend fun connect(request: ConnectRequest): SshSession {
                    connectCalls++
                    if (connectCalls == 1) gate.await() // 第一台悬停：模拟长建连期间用户去连第二台
                    return secondSession
                }
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
            settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        )

        m.startConnect(server, AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Connecting)

        // 第一台仍在 Connecting：连第二台不得被吞掉。
        // 注意：第二台不被 gate 拦（只有 connectCalls == 1 才 await），故它当场 Connected，
        // 而第一台仍停在 Connecting——这正是"两条会话各有独立终态"的证据。
        m.startConnect(db01, AuthMethod.Password("pw2"))
        assertEquals(2, connectCalls, "不同服务器必须可并发建连（全局互斥已废除）")
        assertEquals(2, m.registry.sessions.size, "两条会话并存")
        val byId = m.registry.sessions.associateBy { it.server.id }
        assertTrue(
            byId.getValue("1").state is ConnectionState.Connecting,
            "被拦住的会话不得被后发起的那条改写",
        )
        assertEquals(ConnectionState.Connected(db01, 31L), byId.getValue("2").state)

        // 第一台放行：它也独立收尾为 Connected（各自持有会话），且不覆盖第二台已写入的终态。
        // 这里不断言延迟取值：本用例的 fake 对两次调用返回同一个会话对象，
        // 两条会话的延迟必然相同——断言它只会测到 fake 的构造，而非会话隔离。
        gate.complete(Unit)
        yield()
        val settled = m.registry.sessions.associateBy { it.server.id }
        assertSame(secondSession, settled.getValue("1").session)
        assertSame(secondSession, settled.getValue("2").session)
        assertTrue(settled.getValue("1").state is ConnectionState.Connected)
        assertTrue(settled.getValue("2").state is ConnectionState.Connected)
        assertEquals(db01, m.registry.active?.server, "后连的会话是前台")
    }
}
