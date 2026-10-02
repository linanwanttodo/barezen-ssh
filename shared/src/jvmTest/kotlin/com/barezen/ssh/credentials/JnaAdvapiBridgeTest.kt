// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/JnaAdvapiBridgeTest.kt
package com.barezen.ssh.credentials

import com.sun.jna.Memory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JNA 桥测试。Linux 测试环境无法真调 advapi32——本类只覆盖可在任意平台复现的部分：
 * 结构体字段拼装、secret 编解码往返、非 Windows 的不可用语义。真调用路径（CredWriteW/
 * CredReadW/CredDeleteW）需 Windows 实机，属用户侧验证清单（STATUS §2.1）。
 */
class JnaAdvapiBridgeTest {

    @Test
    fun nonWindowsReportsUnavailable() {
        val bridge = JnaAdvapiBridge.create()
        // 在 Linux/macOS 上必须不可用；在 Windows 上加载成功即为 true（真实状态）
        if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) {
            assertTrue(bridge.isNativeAvailable)
        } else {
            assertFalse(bridge.isNativeAvailable, "非 Windows 平台不得假装 native 可用")
        }
    }

    @Test
    fun unavailableBridgeIsInert() {
        val bridge = JnaAdvapiBridge.create()
        if (bridge.isNativeAvailable) return // Windows 实机上本用例不适用
        assertFalse(bridge.write(WindowsCredStore.WriteRequest("t", charArrayOf('x'), 2)))
        assertNull(bridge.read("t"))
        assertFalse(bridge.delete("t"))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val secret = charArrayOf('p', 'a', 's', 's', '密', '\\', 'u')
        val blob = JnaAdvapiBridge.encodeSecret(secret)
        // UTF-16LE：每字符 2 字节（BMP 内）
        assertEquals(secret.size * 2, blob.size)
        assertContentEquals(secret, JnaAdvapiBridge.decodeSecret(blob))
    }

    @Test
    fun emptyAndNullSecretsEncodeToEmpty() {
        assertEquals(0, JnaAdvapiBridge.encodeSecret(null).size)
        assertEquals(0, JnaAdvapiBridge.encodeSecret(charArrayOf()).size)
        assertEquals(0, JnaAdvapiBridge.decodeSecret(null).size)
        assertEquals(0, JnaAdvapiBridge.decodeSecret(byteArrayOf()).size)
    }

    @Test
    fun credentialStructureCarriesWriteRequest() {
        val req = WindowsCredStore.WriteRequest("barezen/test", charArrayOf('k', 'e', 'y'), WindowsCredStore.CRED_PERSIST_LOCAL_MACHINE)
        val blob = JnaAdvapiBridge.encodeSecret(req.secret)
        val cred = JnaAdvapiBridge.CREDENTIALW().apply {
            Flags = 0
            Type = WindowsCredStore.CRED_TYPE_GENERIC
            TargetName = req.targetName
            Comment = "BareZen-SSH"
            CredentialBlobSize = blob.size
            CredentialBlob = Memory(blob.size.toLong()).also { it.write(0, blob, 0, blob.size) }
            Persist = req.persist
            AttributeCount = 0
        }
        assertEquals(WindowsCredStore.CRED_TYPE_GENERIC, cred.Type)
        assertEquals("barezen/test", cred.TargetName)
        assertEquals(blob.size, cred.CredentialBlobSize)
        assertEquals(WindowsCredStore.CRED_PERSIST_LOCAL_MACHINE, cred.Persist)
        assertEquals(0, cred.AttributeCount)
        // 读回：指针字节 → secret 还原
        val back = cred.CredentialBlob.getByteArray(0, cred.CredentialBlobSize)
        assertContentEquals(req.secret, JnaAdvapiBridge.decodeSecret(back))
    }

    @Test
    fun structureFieldOrderMatchesWincred() {
        // CREDENTIALW 的 JNA 字段序必须与 wincred.h 一致（错序 = 内存布局错 = Windows 上读写坏）
        // getFieldOrder 是 protected：用匿名子类在子类作用域内读出
        val credOrder = object : JnaAdvapiBridge.CREDENTIALW() {
            fun expose(): List<String> = getFieldOrder()
        }.expose()
        assertEquals(
            listOf(
                "Flags", "Type", "TargetName", "Comment", "LastWritten",
                "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount",
                "Attributes", "TargetAlias", "UserName",
            ),
            credOrder,
        )
        val ftOrder = object : JnaAdvapiBridge.FILETIME() {
            fun expose(): List<String> = getFieldOrder()
        }.expose()
        assertEquals(listOf("dwLowDateTime", "dwHighDateTime"), ftOrder)
    }
}
