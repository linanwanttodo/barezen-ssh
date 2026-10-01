// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/AutoConnectTest.kt
package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.StoredAuth
import com.barezen.barezen_ssh.settings.NoopSettingsRepository
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 启动自动连接（设计 §9.3）：只对「私钥认证」且 id 仍存在的服务器生效；
 * 密码认证不自动连（我们不落盘密码）；目标不存在不连接、不弹窗、不打扰。
 */
class AutoConnectTest {

    /** 记录请求并永久挂起 —— 让连接停留在 Connecting，便于断言。 */
    private class CapturingSshClient : SshClient {
        val requests = mutableListOf<ConnectRequest>()
        override suspend fun connect(request: ConnectRequest): SshSession {
            requests += request
            awaitCancellation()
        }
    }

    private fun model(servers: List<Server>, client: CapturingSshClient): AppModel = AppModel(
        repo = InMemoryServerRepository(servers),
        ssh = client,
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    @Test fun connectsToConfiguredKeyServer() {
        val keyServer = Server("srv-1", "web-01", "10.0.0.11", 22, "root", auth = StoredAuth.Key("~/.ssh/id_ed25519"))
        val client = CapturingSshClient()
        val m = model(listOf(keyServer), client)
        m.settings.update { it.copy(autoConnectServerId = "srv-1") }

        m.autoConnectIfConfigured()

        assertEquals(1, client.requests.size, "私钥认证 + id 存在 → 发起一次连接")
        val auth = assertIs<AuthMethod.PrivateKey>(client.requests.single().auth)
        assertEquals("~/.ssh/id_ed25519", auth.keyPath)
        assertTrue(m.connection is ConnectionState.Connecting, "连接保持进行中（假客户端挂起）")
    }

    @Test fun skipsPasswordAuthServer() {
        val pwServer = Server("srv-2", "db-01", "10.0.0.12", 22, "admin", auth = StoredAuth.Password)
        val client = CapturingSshClient()
        val m = model(listOf(pwServer), client)
        m.settings.update { it.copy(autoConnectServerId = "srv-2") }

        m.autoConnectIfConfigured()

        assertEquals(0, client.requests.size, "密码认证不自动连接（无持久化密码）")
        assertTrue(m.connection is ConnectionState.Disconnected)
    }

    @Test fun ignoresMissingServerQuietly() {
        val other = Server("srv-3", "cache-01", "10.0.0.13", 22, "root", auth = StoredAuth.Key("~/.ssh/id_ed25519"))
        val client = CapturingSshClient()
        val m = model(listOf(other), client)
        m.settings.update { it.copy(autoConnectServerId = "srv-gone") }

        m.autoConnectIfConfigured()

        assertEquals(0, client.requests.size, "目标不存在：不连接、不弹窗、不打扰")
        assertTrue(m.connection is ConnectionState.Disconnected)
    }
}
