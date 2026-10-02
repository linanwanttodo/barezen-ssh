// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/WindowsCredStoreTest.kt
package com.barezen.ssh.credentials

import com.barezen.ssh.credentials.WindowsCredStore.Bridge
import com.barezen.ssh.credentials.WindowsCredStore.WriteRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WindowsCredStore 参数拼装测试：只验证传给 native bridge 的参数与结果映射，
 * 不触发任何真实 advapi32 调用（沙箱为 Linux）。
 */
class WindowsCredStoreTest {

    /** 记录参数拼装结果并按脚本回放的假 bridge。 */
    private class FakeBridge : Bridge {
        val writes = mutableListOf<WriteRequest>()
        val reads = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var nativeAvailable = true
        var writeResult = true
        var readResult: CharArray? = null
        var deleteResult = true

        override fun isNativeAvailable() = nativeAvailable
        override fun write(request: WriteRequest): Boolean {
            writes.add(request)
            return writeResult
        }

        override fun read(targetName: String): CharArray? {
            reads.add(targetName)
            return readResult
        }

        override fun delete(targetName: String): Boolean {
            deletes.add(targetName)
            return deleteResult
        }
    }

    // ---- save ----

    @Test
    fun saveAssemblesWriteRequestWithTargetSecretAndPersist() {
        val bridge = FakeBridge()
        WindowsCredStore(bridge).save("server/s1/password", "win-secret".toCharArray())
        assertEquals(1, bridge.writes.size)
        val request = bridge.writes.first()
        assertEquals("server/s1/password", request.targetName)
        assertContentEquals("win-secret".toCharArray(), request.secret)
        assertEquals(WindowsCredStore.CRED_PERSIST_LOCAL_MACHINE, request.persist)
    }

    @Test
    fun saveThrowsWhenBridgeReportsFailure() {
        val bridge = FakeBridge().apply { writeResult = false }
        assertFailsWith<CredentialStoreException> {
            WindowsCredStore(bridge).save("server/s1/password", "x".toCharArray())
        }
    }

    @Test
    fun saveThrowsWhenNativeUnavailable() {
        val bridge = FakeBridge().apply { nativeAvailable = false }
        assertFailsWith<CredentialStoreException> {
            WindowsCredStore(bridge).save("server/s1/password", "x".toCharArray())
        }
        assertEquals(0, bridge.writes.size)
    }

    // ---- load ----

    @Test
    fun loadReturnsBridgeResult() {
        val bridge = FakeBridge().apply { readResult = "found".toCharArray() }
        assertContentEquals("found".toCharArray(), WindowsCredStore(bridge).load("server/s1/password"))
        assertEquals(listOf("server/s1/password"), bridge.reads)
    }

    @Test
    fun loadReturnsNullOnMiss() {
        val bridge = FakeBridge().apply { readResult = null }
        assertNull(WindowsCredStore(bridge).load("server/s1/password"))
    }

    @Test
    fun loadReturnsNullWhenNativeUnavailable() {
        val bridge = FakeBridge().apply { nativeAvailable = false }
        assertNull(WindowsCredStore(bridge).load("server/s1/password"))
        assertEquals(0, bridge.reads.size)
    }

    // ---- delete ----

    @Test
    fun deleteForwardsToBridgeAndToleratesMiss() {
        val bridge = FakeBridge().apply { deleteResult = false }
        WindowsCredStore(bridge).delete("server/s1/password")
        assertEquals(listOf("server/s1/password"), bridge.deletes)
    }

    @Test
    fun deleteNoOpWhenNativeUnavailable() {
        val bridge = FakeBridge().apply { nativeAvailable = false }
        WindowsCredStore(bridge).delete("server/s1/password")
        assertEquals(0, bridge.deletes.size)
    }

    // ---- isAvailable ----

    @Test
    fun availabilityMirrorsBridge() {
        assertTrue(WindowsCredStore(FakeBridge()).isAvailable())
        assertFalse(WindowsCredStore(FakeBridge().apply { nativeAvailable = false }).isAvailable())
    }
}
