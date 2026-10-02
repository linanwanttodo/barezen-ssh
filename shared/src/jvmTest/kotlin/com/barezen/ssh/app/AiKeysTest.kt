// shared/src/jvmTest/kotlin/com/barezen/ssh/app/AiKeysTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.credentials.CredentialStore
import com.barezen.ssh.credentials.CredentialStoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-7：AI key 存储。不碰真钥匙串，只验证存取语义与降级诚实性。 */
class AiKeysTest {

    private class MapCredentialStore : CredentialStore {
        val map = mutableMapOf<String, CharArray>()
        override fun save(id: String, secret: CharArray) { map[id] = secret.copyOf() }
        override fun load(id: String): CharArray? = map[id]?.copyOf()
        override fun delete(id: String) { map.remove(id) }
        override fun isAvailable(): Boolean = true
    }

    private class BrokenCredentialStore : CredentialStore {
        override fun save(id: String, secret: CharArray) = throw CredentialStoreException("denied")
        override fun load(id: String): CharArray = throw CredentialStoreException("denied")
        override fun delete(id: String) = throw CredentialStoreException("denied")
        override fun isAvailable(): Boolean = false
    }

    @Test
    fun keyRoundTripAndDelete() {
        val keys = PlatformAiKeyStore(MapCredentialStore(), nativeAvailable = false)
        assertFalse(keys.keychainAvailable) // 降级可用性如实上报
        assertNull(keys.loadKey())
        keys.saveKey("sk-abc")
        assertEquals("sk-abc", keys.loadKey())
        keys.deleteKey()
        assertNull(keys.loadKey())
    }

    @Test
    fun brokenStoreDegradesQuietly() {
        val keys = PlatformAiKeyStore(BrokenCredentialStore(), nativeAvailable = false)
        keys.saveKey("sk-abc") // 不抛
        keys.deleteKey() // 不抛
        assertNull(keys.loadKey()) // 读失败 → null（未配置）
        assertFalse(keys.keychainAvailable)
    }

    @Test
    fun nativeAvailableFlagPassesThrough() {
        val keys = PlatformAiKeyStore(MapCredentialStore(), nativeAvailable = true)
        assertTrue(keys.keychainAvailable)
    }

    @Test
    fun blankKeyReadsAsUnset() {
        val store = MapCredentialStore()
        store.save(AiKeyStore.KEY_ID, "   ".toCharArray())
        val keys = PlatformAiKeyStore(store, nativeAvailable = true)
        assertNull(keys.loadKey())
    }
}
