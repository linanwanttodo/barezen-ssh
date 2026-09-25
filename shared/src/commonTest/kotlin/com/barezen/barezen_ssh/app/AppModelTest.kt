package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
