// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/CredentialStoresTest.kt
package com.barezen.ssh.credentials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialStoresTest {

    @Test
    fun platformDefaultNeverThrowsAndIsFunctional() {
        val store = CredentialStores.platformDefault()
        assertNotNull(store)
        store.save("server/t/password", "v".toCharArray())
        assertContentEquals("v".toCharArray(), store.load("server/t/password"))
        store.delete("server/t/password")
    }

    @Test
    fun fallbackStoreIsInMemoryWhenNoNativeBackend() {
        // 本沙箱（Linux 无 D-Bus）应降级为内存存储；在真机上有钥匙串时此断言跳过。
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("linux")) {
            val store = CredentialStores.platformDefault()
            if (!store.isAvailable().not()) return // 环境意外带钥匙串时不作断言
            assertTrue(store is InMemoryCredentialStore)
        }
    }

    @Test
    fun credentialIdsFollowConvention() {
        assertEquals("server/web-01/password", CredentialIds.password("web-01"))
        assertEquals("server/web-01/keypass", CredentialIds.keyPassphrase("web-01"))
    }
}
