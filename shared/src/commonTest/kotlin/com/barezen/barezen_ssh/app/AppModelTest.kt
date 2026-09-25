package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
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
    )

    @Test fun connectHappyPathEndsConnected() = runBlocking {
        val session = object : SshSession {
            override fun pingMs() = 12L
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
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

    /** 防重入：Connecting 期间的第二次连接必须整体忽略——否则并发协程互相丢弃会话、状态末写覆盖。 */
    @Test fun secondConnectIgnoredWhileConnecting() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var connectCalls = 0
        val firstSession = object : SshSession {
            override fun pingMs() = 12L
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
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

        // Connecting 期间第二次经「卡片→对话框→确认」路径到达：必须被防重入守卫整体忽略
        m.requestConnect(Server("2", "db-01", "10.0.0.12", 22, "root"))
        m.confirmConnect(AuthMethod.Password("pw2"))
        assertEquals(1, connectCalls)
        assertTrue(m.connection is ConnectionState.Connecting)
        assertEquals("1", (m.connection as ConnectionState.Connecting).server.id)
        assertNull(m.shellSession) // 连接未完成不预存会话：状态与会话不脱节

        // 放行后第一次连接完整收尾：会话入成员且与状态成对一致
        gate.complete(Unit)
        yield()
        assertEquals(ConnectionState.Connected(server, 12L), m.connection)
        assertSame(firstSession, m.shellSession)
    }
}
