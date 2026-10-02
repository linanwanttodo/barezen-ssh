// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/TofuHostKeyVerifierTest.kt
package com.barezen.ssh.ssh

import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TOFU 语义回归测试。验证器是应用级单例（AppModel.desktop → JvmSshClient 默认参只构造一次），
 * 因此同一实例必须在整个运行期内保持“内存快照 == 磁盘条目”：
 * - 未知主机 → 接受并追加写入（仅一次，不产生重复行）
 * - 已有条目且匹配 → 接受
 * - 已有条目但主机密钥不同 → 拒绝（含本运行期刚落盘的条目）
 */
class TofuHostKeyVerifierTest {
    private val host = "127.0.0.1"
    private val port = 2222

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun File.contentLines() = readLines().filter { it.isNotBlank() }

    /** (a) 首次信任并落盘后，同一验证器实例必须拒绝该主机换用的新密钥。 */
    @Test fun sameVerifierRejectsChangedKeyAfterAccept() {
        val kh = File.createTempFile("kh_", "")
        val v = TofuHostKeyVerifier(kh)
        val k1 = keyPair()
        val k2 = keyPair()
        assertTrue(v.verify(host, port, k1.public))   // 未知主机 → 接受并追加
        assertTrue(v.verify(host, port, k1.public))   // 已有条目且匹配 → 接受，不再追加
        assertFalse(v.verify(host, port, k2.public))  // 已有条目但密钥不同 → 拒绝
    }

    /** (b) 同一实例反复校验（模拟重连）不得追加重复行；磁盘内容只含 K1。 */
    @Test fun reverifyAppendsNoDuplicateLine() {
        val kh = File.createTempFile("kh_", "")
        val v = TofuHostKeyVerifier(kh)
        val k1 = keyPair()
        repeat(3) { assertTrue(v.verify(host, port, k1.public)) }
        val lines = kh.contentLines()
        assertEquals(1, lines.size, "known_hosts 出现重复条目: $lines")
        assertTrue(lines.single().contains(host))

        // 跨“重启”语义：新实例读盘后信任 K1、拒绝 K2，文件仍只有一行
        val k2 = keyPair()
        val restarted = TofuHostKeyVerifier(kh)
        assertTrue(restarted.verify(host, port, k1.public))
        assertFalse(restarted.verify(host, port, k2.public))
        assertEquals(1, kh.contentLines().size, "known_hosts 出现重复条目: ${kh.contentLines()}")
    }
}
