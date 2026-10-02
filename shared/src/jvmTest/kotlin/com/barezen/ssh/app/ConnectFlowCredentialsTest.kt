// shared/src/jvmTest/kotlin/com/barezen/ssh/app/ConnectFlowCredentialsTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.credentials.CredentialResolver
import com.barezen.ssh.credentials.NoopCredentialResolver
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 内存凭据端口：记录 remember/forget 调用，供 AppModel 侧断言（不落盘、零平台依赖）。 */
private class RecordingResolver(
    private val available: Boolean = true,
    initial: Map<String, AuthMethod> = emptyMap(),
) : CredentialResolver {
    private val entries = LinkedHashMap(initial)
    val remembered = mutableListOf<Pair<String, AuthMethod>>()
    val forgotten = mutableListOf<String>()

    override fun load(server: Server): AuthMethod? = entries[server.id]
    override fun remember(server: Server, auth: AuthMethod) {
        entries[server.id] = auth
        remembered += server.id to auth
    }

    override fun forget(serverId: String) {
        entries.remove(serverId)
        forgotten += serverId
    }

    override fun isAvailable(): Boolean = available
}

class ConnectFlowCredentialsTest {
    private val web01 = Server("1", "web-01", "10.0.0.11", 22, "root")

    private fun model(
        resolver: CredentialResolver,
        ssh: SshClient = object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("unused")
        },
    ) = AppModel(
        repo = InMemoryServerRepository(listOf(web01)),
        ssh = ssh,
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        credentials = resolver,
    )

    @Test
    fun storedCredentialMakesServerReportableAsRemembered() {
        val resolver = RecordingResolver(initial = mapOf("1" to AuthMethod.Password("s3cret")))
        val m = model(resolver)
        assertTrue(m.hasStoredCredential(web01))
    }

    @Test
    fun unavailableKeychainNeverReportsStoredCredential() {
        // 钥匙串不可用时即便底层残留条目也一律按「无凭据」处理（UI 据此不给免输入入口）
        val resolver = RecordingResolver(available = false, initial = mapOf("1" to AuthMethod.Password("x")))
        val m = model(resolver)
        assertFalse(m.hasStoredCredential(web01))
        assertNull(m.credentials.load(web01).takeIf { m.credentials.isAvailable() })
    }

    @Test
    fun noopBaselineKeepsMemoryOnlyBehaviour() {
        // 既有默认注入（NoopCredentialResolver）下：无凭据、不可用、连接流程与 T-1 之前一致
        val m = model(NoopCredentialResolver)
        assertFalse(m.hasStoredCredential(web01))
        assertFalse(m.credentials.isAvailable())
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertNull(m.pendingConnect)
    }

    @Test
    fun rememberThenLoadRoundTripThroughModelPort() {
        val resolver = RecordingResolver()
        val m = model(resolver)
        assertFalse(m.hasStoredCredential(web01))
        m.credentials.remember(web01, AuthMethod.Password("pw"))
        assertEquals(listOf<Pair<String, AuthMethod>>("1" to AuthMethod.Password("pw")), resolver.remembered)
        assertTrue(m.hasStoredCredential(web01))
        assertEquals(AuthMethod.Password("pw"), m.credentials.load(web01))
    }

    @Test
    fun removingServerForgetsItsCredentialEntry() {
        val resolver = RecordingResolver(initial = mapOf("1" to AuthMethod.Password("pw")))
        val m = model(resolver)
        assertTrue(m.hasStoredCredential(web01))
        m.removeServer("1")
        assertEquals(listOf("1"), resolver.forgotten)
        assertTrue(m.servers.isEmpty())
    }
}
