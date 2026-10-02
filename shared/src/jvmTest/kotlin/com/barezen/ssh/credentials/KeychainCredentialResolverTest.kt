// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/KeychainCredentialResolverTest.kt
package com.barezen.ssh.credentials

import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.ssh.AuthMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 记录每次调用的假存储：断言适配器拼出的 id，并可注入失败以验证异常不外溢。 */
private class RecordingStore(
    private val available: Boolean = true,
    private val failSave: Boolean = false,
    private val failDelete: Boolean = false,
    private val failLoad: Boolean = false,
) : CredentialStore {
    val secrets = mutableMapOf<String, CharArray>()
    val savedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
    val loadedIds = mutableListOf<String>()

    override fun save(id: String, secret: CharArray) {
        savedIds += id
        if (failSave) throw CredentialStoreException("save refused: " + id)
        secrets[id] = secret.copyOf()
    }

    override fun load(id: String): CharArray? {
        loadedIds += id
        if (failLoad) throw CredentialStoreException("load failed: " + id)
        return secrets[id]?.copyOf()
    }

    override fun delete(id: String) {
        deletedIds += id
        if (failDelete) throw CredentialStoreException("delete refused: " + id)
        secrets.remove(id)
    }

    override fun isAvailable(): Boolean = available

    fun seed(id: String, value: String) {
        secrets[id] = value.toCharArray()
    }
}

private fun passwordServer(id: String = "web-01") = Server(
    id = id,
    name = "web",
    host = "10.0.0.1",
    port = 22,
    user = "root",
    auth = StoredAuth.Password,
)

private fun keyServer(id: String = "web-01", keyPath: String = "/home/root/.ssh/id_ed25519") = Server(
    id = id,
    name = "web",
    host = "10.0.0.1",
    port = 22,
    user = "root",
    auth = StoredAuth.Key(keyPath),
)

/** 被测适配器：假存储 + 显式原生可用性标志（默认按钥匙串可用，便于测存取路径）。 */
private fun resolver(
    store: CredentialStore,
    nativeAvailable: Boolean = true,
): KeychainCredentialResolver = KeychainCredentialResolver(store, nativeAvailable)

/** 断言 id 记录与异常注入用的假存储；每个用例各自新建实例，用例间无共享状态。 */
private fun freshStore(
    failSave: Boolean = false,
    failDelete: Boolean = false,
    failLoad: Boolean = false,
): RecordingStore = RecordingStore(failSave = failSave, failDelete = failDelete, failLoad = failLoad)

class KeychainCredentialResolverTest {

    @Test
    fun passwordRoundTripUsesPasswordId() {
        val store = freshStore()
        val r = resolver(store)
        val s = passwordServer(id = "web-01")

        r.remember(s, AuthMethod.Password("s3cret"))

        assertEquals(listOf("server/web-01/password"), store.savedIds)
        assertEquals(AuthMethod.Password("s3cret"), r.load(s))
        assertEquals(listOf("server/web-01/password"), store.loadedIds)
    }

    @Test
    fun keyPassphraseRoundTripUsesKeypassIdAndKeepsKeyPath() {
        val store = freshStore()
        val r = resolver(store)
        val s = keyServer(id = "db-02", keyPath = "/keys/id_rsa")

        r.remember(s, AuthMethod.PrivateKey("/keys/id_rsa", "phrase"))

        assertEquals(listOf("server/db-02/keypass"), store.savedIds)
        val loaded = r.load(s)
        assertIs<AuthMethod.PrivateKey>(loaded)
        assertEquals("/keys/id_rsa", loaded.keyPath)
        assertEquals("phrase", loaded.passphrase)
    }

    @Test
    fun privateKeyWithoutPassphraseSavesNothingToKeychain() {
        val store = freshStore()
        val r = resolver(store)

        r.remember(keyServer(), AuthMethod.PrivateKey("/keys/id_rsa"))

        // 明文私钥：不写入口令；只做一次清理（可能残留的旧口令），绝不 save
        assertEquals(emptyList(), store.savedIds)
        assertEquals(listOf("server/web-01/keypass"), store.deletedIds)
    }

    @Test
    fun privateKeyWithoutPassphraseDeletesStaleKeypassEntry() {
        val store = freshStore()
        store.seed("server/web-01/keypass", "old-phrase")
        val r = resolver(store)

        r.remember(keyServer(), AuthMethod.PrivateKey("/keys/id_rsa", null))
        r.remember(keyServer(), AuthMethod.PrivateKey("/keys/id_rsa", ""))

        assertEquals(listOf("server/web-01/keypass", "server/web-01/keypass"), store.deletedIds)
        assertNull(store.secrets["server/web-01/keypass"])
        assertNull(r.load(keyServer()))
    }

    @Test
    fun emptyPassphraseDeletesStaleEntryThenNewPassphraseCanBeStored() {
        val store = freshStore()
        val r = resolver(store)
        val s = keyServer()

        r.remember(s, AuthMethod.PrivateKey("/keys/id_rsa", "phrase"))
        r.remember(s, AuthMethod.PrivateKey("/keys/id_rsa", ""))

        assertEquals(listOf("server/web-01/keypass"), store.savedIds)
        assertNull(r.load(s))

        r.remember(s, AuthMethod.PrivateKey("/keys/id_rsa", "next"))
        // keyPath 取自 Server.auth，不是 remember 时传入的值
        assertEquals(AuthMethod.PrivateKey("/home/root/.ssh/id_ed25519", "next"), r.load(s))
    }

    @Test
    fun rememberPasswordDoesNotTouchKeypassId() {
        val store = freshStore()
        val r = resolver(store)

        r.remember(passwordServer(), AuthMethod.Password("pw"))

        assertFalse(store.savedIds.contains("server/web-01/keypass"))
        assertFalse(store.deletedIds.contains("server/web-01/keypass"))
    }

    @Test
    fun loadPasswordServerReturnsNullWhenOnlyKeypassEntryExists() {
        val store = freshStore()
        store.seed("server/web-01/keypass", "phrase")
        val r = resolver(store)

        assertNull(r.load(passwordServer()))
        assertEquals(listOf("server/web-01/password"), store.loadedIds)
    }

    @Test
    fun loadKeyServerReturnsNullWhenOnlyPasswordEntryExists() {
        val store = freshStore()
        store.seed("server/web-01/password", "pw")
        val r = resolver(store)

        assertNull(r.load(keyServer()))
        assertEquals(listOf("server/web-01/keypass"), store.loadedIds)
    }

    @Test
    fun loadWithoutEntryReturnsNull() {
        val r = resolver(freshStore())

        assertNull(r.load(passwordServer()))
        assertNull(r.load(keyServer()))
    }

    @Test
    fun loadKeepsNamespacingAcrossServerIds() {
        val store = freshStore()
        val r = resolver(store)

        r.remember(passwordServer(id = "a"), AuthMethod.Password("pw-a"))
        r.remember(passwordServer(id = "b"), AuthMethod.Password("pw-b"))

        assertEquals(AuthMethod.Password("pw-a"), r.load(passwordServer(id = "a")))
        assertEquals(AuthMethod.Password("pw-b"), r.load(passwordServer(id = "b")))
    }

    @Test
    fun loadTakesKeyPathFromServerNotFromKeychain() {
        val store = freshStore()
        val r = resolver(store)
        val keyPath = "/home/root/.ssh/id_ed25519"

        r.remember(keyServer(keyPath = keyPath), AuthMethod.PrivateKey(keyPath, "phrase"))

        val loaded = r.load(keyServer(keyPath = keyPath))
        assertIs<AuthMethod.PrivateKey>(loaded)
        assertEquals(keyPath, loaded.keyPath)
        assertEquals("phrase", loaded.passphrase)
        // 口令条目按 serverId 复用：换一个 keyPath 的同一服务器只换密钥，口令仍来自钥匙串
        val sameIdOtherPath = keyServer(keyPath = "/other/id_rsa")
        val reloaded = r.load(sameIdOtherPath)
        assertIs<AuthMethod.PrivateKey>(reloaded)
        assertEquals("/other/id_rsa", reloaded.keyPath)
    }

    @Test
    fun loadKeyServerWithoutStoredPassphraseReturnsNull() {
        // 无存储凭据即「回退现状」：返回 null，让调用方走明文私钥或提示输入
        val store = freshStore()
        val r = resolver(store)
        store.seed("server/web-01/password", "pw")

        assertNull(r.load(keyServer()))
    }

    @Test
    fun loadNeverWritesBackToKeychain() {
        val store = freshStore()
        store.seed("server/web-01/keypass", "phrase")

        resolver(store).load(keyServer())

        assertEquals(emptyList(), store.savedIds)
        assertEquals(emptyList(), store.deletedIds)
    }

    @Test
    fun forgetDeletesBothIds() {
        val store = freshStore()
        val r = resolver(store)
        store.seed("server/web-01/password", "pw")
        store.seed("server/web-01/keypass", "phrase")

        r.forget("web-01")

        assertEquals(listOf("server/web-01/password", "server/web-01/keypass"), store.deletedIds)
        assertTrue(store.secrets.isEmpty())
    }

    @Test
    fun forgetIsIdempotentForUnknownId() {
        val store = freshStore()
        val r = resolver(store)

        r.forget("ghost")
        r.forget("ghost")

        assertEquals(
            listOf(
                "server/ghost/password",
                "server/ghost/keypass",
                "server/ghost/password",
                "server/ghost/keypass",
            ),
            store.deletedIds,
        )
    }

    @Test
    fun forgetSwallowsStoreExceptionForBothIds() {
        // GnomeKeyringStore/MacKeychainStore 在条目不存在时退出码非 0 即抛：删除服务器不得因此失败
        val store = freshStore(failDelete = true)
        val r = resolver(store)

        r.forget("web-01")
        r.forget("web-01")

        assertEquals(
            listOf(
                "server/web-01/password",
                "server/web-01/keypass",
                "server/web-01/password",
                "server/web-01/keypass",
            ),
            store.deletedIds,
        )
    }

    @Test
    fun forgetKeepsGoingWhenFirstDeleteThrows() {
        val store = object : CredentialStore {
            val deleted = mutableListOf<String>()
            override fun save(id: String, secret: CharArray) = Unit
            override fun load(id: String): CharArray? = null
            override fun delete(id: String) {
                deleted += id
                throw CredentialStoreException("clear 失败（退出码 1）")
            }
            override fun isAvailable(): Boolean = true
        }

        resolver(store).forget("web-01")

        // 第一条抛异常后，第二条仍必须被尝试
        assertEquals(listOf("server/web-01/password", "server/web-01/keypass"), store.deleted)
    }

    @Test
    fun rememberSwallowsStoreException() {
        val store = freshStore(failSave = true)
        val r = resolver(store)

        r.remember(passwordServer(), AuthMethod.Password("pw"))
        r.remember(keyServer(), AuthMethod.PrivateKey("/keys/id_rsa", "phrase"))

        assertEquals(listOf("server/web-01/password", "server/web-01/keypass"), store.savedIds)
        assertNull(r.load(passwordServer()))
    }

    @Test
    fun loadSwallowsStoreExceptionAndReturnsNull() {
        val store = freshStore(failLoad = true)
        val r = resolver(store)

        assertNull(r.load(passwordServer()))
        assertNull(r.load(keyServer()))
    }

    @Test
    fun isAvailableTrueWhenNativeKeychainSelected() {
        assertTrue(resolver(freshStore(), nativeAvailable = true).isAvailable())
    }

    @Test
    fun isAvailableFalseWhenFellBackToInMemoryStore() {
        // CredentialStores.platformDefault() 降级后 InMemoryCredentialStore.isAvailable() 恒为 true，
        // 适配器必须仍如实报告「非系统钥匙串」，UI 才能提示「凭据仅保存在内存」
        val inMemory = InMemoryCredentialStore()
        assertTrue(inMemory.isAvailable())
        val r = resolver(inMemory, nativeAvailable = false)

        assertFalse(r.isAvailable())

        // 降级不等于不可用存储：仍能正常存取
        r.remember(passwordServer(), AuthMethod.Password("pw"))
        assertEquals(AuthMethod.Password("pw"), r.load(passwordServer()))
    }

    @Test
    fun platformDefaultNeverThrowsAndReturnsUsableResolver() {
        val r = KeychainCredentialResolver.platformDefault()
        val s = passwordServer(id = "t")

        r.remember(s, AuthMethod.Password("v"))
        assertEquals(AuthMethod.Password("v"), r.load(s))
        r.forget(s.id)
        assertNull(r.load(s))
    }

    @Test
    fun platformDefaultReportsUnavailableWhenNativeKeychainMissing() {
        val r = KeychainCredentialResolver.platformDefault()
        // 沙箱（Linux 无 D-Bus）应降级到内存：此时 isAvailable 必须为 false，UI 才能提示「凭据仅保存在内存」
        if (KeychainCredentialResolver.probeNativeStore() == null) {
            assertFalse(r.isAvailable())
        }
        assertEquals(KeychainCredentialResolver.probeNativeStore() != null, r.isAvailable())
    }

    @Test
    fun probeNativeStoreReturnsStoreOrNullWithoutThrowing() {
        // 探测本身不得抛异常：失败一律以 null 表示「已降级」
        val native = KeychainCredentialResolver.probeNativeStore()
        if (native != null) {
            assertTrue(native.isAvailable())
        }
    }

    @Test
    fun worksWithInMemoryStoreEndToEnd() {
        val r = resolver(InMemoryCredentialStore())
        val s = keyServer(id = "s-1", keyPath = "/keys/id_rsa")

        r.remember(s, AuthMethod.PrivateKey("/keys/id_rsa", "phrase"))
        assertEquals(AuthMethod.PrivateKey("/keys/id_rsa", "phrase"), r.load(s))

        r.forget(s.id)
        assertNull(r.load(s))
    }

    @Test
    fun passwordAndKeyPassphraseIdsMatchCredentialIdsConvention() {
        val store = freshStore()
        val r = resolver(store)

        r.remember(passwordServer(id = "web-01"), AuthMethod.Password("pw"))
        r.remember(keyServer(id = "web-01"), AuthMethod.PrivateKey("/keys/id_rsa", "phrase"))

        assertEquals(
            listOf(CredentialIds.password("web-01"), CredentialIds.keyPassphrase("web-01")),
            store.savedIds,
        )
        assertEquals("server/web-01/password", CredentialIds.password("web-01"))
        assertEquals("server/web-01/keypass", CredentialIds.keyPassphrase("web-01"))
    }
}