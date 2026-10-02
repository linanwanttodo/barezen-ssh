package com.barezen.ssh.app

import com.barezen.ssh.credentials.CredentialResolver
import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.ServerRepository
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.SshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 记录调用的假实现：验证 AppModel 与凭据端口的契约（存取、幂等清理、可用性判定）。 */
private class FakeCredentialResolver(
    private val available: Boolean = true,
    private val stored: MutableMap<String, AuthMethod> = mutableMapOf(),
) : CredentialResolver {
    val forgetCalls = mutableListOf<String>()
    val rememberCalls = mutableListOf<Pair<String, AuthMethod>>()

    override fun load(server: Server): AuthMethod? = stored[server.id]
    override fun remember(server: Server, auth: AuthMethod) {
        rememberCalls += server.id to auth
        stored[server.id] = auth
    }
    override fun forget(serverId: String) {
        forgetCalls += serverId
        stored.remove(serverId)
    }
    override fun isAvailable(): Boolean = available
}

private class RecordingRepo(initial: List<Server> = emptyList()) : ServerRepository {
    private val items = initial.toMutableList()
    val deleted = mutableListOf<String>()
    override fun list(): List<Server> = items.toList()
    override fun upsert(server: Server) {
        items.removeAll { it.id == server.id }
        items += server
    }
    override fun delete(id: String) {
        deleted += id
        items.removeAll { it.id == id }
    }
}

private fun srv(id: String, name: String = "srv") = Server(
    id = id,
    name = name,
    host = "10.0.0.1",
    port = 22,
    user = "root",
    auth = StoredAuth.Password,
)

private fun modelWith(
    repo: ServerRepository = RecordingRepo(),
    credentials: CredentialResolver,
) = AppModel(
    repo = repo,
    ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
    scope = CoroutineScope(Dispatchers.Unconfined),
    settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    credentials = credentials,
)

class AppModelCredentialsTest {

    @Test
    fun removeServer_deletesKeychainEntry() {
        val repo = RecordingRepo(listOf(srv("a"), srv("b")))
        val creds = FakeCredentialResolver()
        val model = modelWith(repo, creds)

        model.removeServer("a")

        assertEquals(listOf("a"), creds.forgetCalls)
        assertEquals(listOf("a"), repo.deleted)
        assertEquals(listOf("b"), model.servers.map { it.id })
    }

    @Test
    fun removeServer_isIdempotentForUnknownId() {
        val creds = FakeCredentialResolver()
        val model = modelWith(RecordingRepo(listOf(srv("a"))), creds)

        model.removeServer("missing")

        // forget 仍被调用（幂等，不抛异常），且仓库中无关的服务器不受影响
        assertEquals(listOf("missing"), creds.forgetCalls)
        assertEquals(listOf("a"), model.servers.map { it.id })
    }

    @Test
    fun hasStoredCredential_falseWhenKeychainUnavailable() {
        val creds = FakeCredentialResolver(available = false)
        val s = srv("a")
        creds.remember(s, AuthMethod.Password("pw"))
        val model = modelWith(credentials = creds)

        assertFalse(model.hasStoredCredential(s))
    }

    @Test
    fun hasStoredCredential_trueWhenEntryExists() {
        val creds = FakeCredentialResolver()
        val s = srv("a")
        creds.remember(s, AuthMethod.Password("pw"))
        val model = modelWith(credentials = creds)

        assertTrue(model.hasStoredCredential(s))
    }

    @Test
    fun hasStoredCredential_falseWhenNoEntry() {
        val model = modelWith(credentials = FakeCredentialResolver())

        assertFalse(model.hasStoredCredential(srv("a")))
    }

    @Test
    fun defaultConstructorDoesNotTouchCredentials() {
        val repo = RecordingRepo(listOf(srv("a")))
        val model = AppModel(
            repo = repo,
            ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
            scope = CoroutineScope(Dispatchers.Unconfined),
            settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        )

        assertFalse(model.hasStoredCredential(srv("a")))
        model.removeServer("a")
        assertEquals(emptyList(), model.servers)
    }

    @Test
    fun hasStoredCredential_falseAfterRemove() {
        val creds = FakeCredentialResolver()
        val s = srv("a")
        creds.remember(s, AuthMethod.Password("pw"))
        val model = modelWith(RecordingRepo(listOf(s)), creds)
        assertTrue(model.hasStoredCredential(s))

        model.removeServer("a")

        assertFalse(model.hasStoredCredential(s))
    }
}
