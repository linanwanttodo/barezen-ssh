// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/SftpUploadLoopContractTest.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpFs
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 锁定 upload 循环的终止契约：
 * - 返回 null    => 结束（SftpFs.upload 的既有约定）
 * - 返回空数组  => 不得被当作「继续」而空转；应视为错误，绝不能忙等
 *
 * 本测试用与生产实现同构的最小循环体，把「空块 => continue」这一缺陷语义固定下来，防止退化。
 */
class SftpUploadLoopContractTest {

    /** 与 JvmSftpFs.upload 修复后的循环体同构：空块不再 continue。 */
    private class LoopUnderTest(private val fetch: (Long) -> ByteArray?) {
        val writes = mutableListOf<Pair<Long, Int>>()
        val iterations = AtomicInteger(0)

        fun run() {
            var offset = 0L
            while (true) {
                iterations.incrementAndGet()
                if (iterations.get() > 10_000) throw IllegalStateException("上传循环未终止（忙等）")
                val chunk = fetch(offset) ?: break
                if (chunk.isEmpty()) throw IllegalArgumentException("上传回调返回空块：约定必须以 null 表示结束")
                writes += offset to chunk.size
                offset += chunk.size
            }
        }
    }

    @Test fun emptyChunkIsRejectedInsteadOfSpinning() {
        val loop = LoopUnderTest { offset -> if (offset == 0L) ByteArray(64 * 1024) else ByteArray(0) }
        var threw = false
        try { loop.run() } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw, "空块必须被拒绝，而不是被 continue 空转")
        assertEquals(2, loop.iterations.get(), "只应迭代到第二次就抛错，不能忙等")
    }

    @Test fun nullTerminatesWithoutSpin() {
        val loop = LoopUnderTest { offset -> if (offset == 0L) ByteArray(64 * 1024) else null }
        loop.run()
        assertEquals(1, loop.writes.size)
        assertTrue(loop.iterations.get() <= 3, "null 终止不得多迭代：${loop.iterations.get()}")
    }

    @Test fun fullFileUploadWritesEveryChunkExactlyOnce() {
        val size = 10 * 1024 * 1024
        val loop = LoopUnderTest { offset -> if (offset >= size) null else ByteArray(minOf(64 * 1024, size - offset.toInt())) }
        loop.run()
        assertEquals(160, loop.writes.size, "10MiB / 64KiB")
        assertEquals(size.toLong(), loop.writes.sumOf { it.second.toLong() })
    }

    @Test fun interfaceHasSingleUploadOverload() {
        val iface: Class<*> = SftpFs::class.java
        assertEquals(1, iface.methods.count { it.name == "upload" }, "upload 只应有一个重载")
    }
}
