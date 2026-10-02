// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/LocalChunkReaderBoundaryTest.kt
package com.barezen.ssh.ssh.sftp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 边界：整块大小（64KiB 的整数倍）文件读完后，read(offset) 必须返回 null 终止上传循环。
 * 这直接关系 JvmSftpFs.upload 的 `nextChunk(offset) ?: break` 终止条件。
 */
class LocalChunkReaderBoundaryTest {

    private fun readerOf(size: Int): Pair<LocalChunkReader, File> {
        val f = File.createTempFile("chunk_", ".bin").apply { writeBytes(ByteArray(size)) }
        return LocalChunkReader(f.absolutePath) to f
    }

    @Test fun exactlyOneChunkReturnsNullAfterEnd() {
        val (r, f) = readerOf(64 * 1024)
        r.use {
            assertEquals(64 * 1024, it.size())
            assertTrue(it.read(0) != null, "首块应非 null")
            assertEquals(null, it.read(64 * 1024L), "读完整块后必须返回 null 终止")
        }
        f.delete()
    }

    @Test fun exactlyTwoChunksReturnsNullAfterEnd() {
        val (r, f) = readerOf(128 * 1024)
        r.use {
            assertTrue(it.read(0) != null)
            assertTrue(it.read(64 * 1024L) != null)
            assertEquals(null, it.read(128 * 1024L), "第二块读完后必须返回 null")
        }
        f.delete()
    }

    @Test fun nonMultipleOfChunkTerminatesCorrectly() {
        val (r, f) = readerOf(10 * 1024 * 1024)
        r.use {
            var off = 0L
            var chunks = 0
            while (true) {
                val c = it.read(off) ?: break
                off += c.size
                chunks++
                if (chunks > 1000) error("未在合理块数内终止：off=$off")
            }
            assertEquals(10 * 1024 * 1024L, off)
            assertEquals(160, chunks, "10MiB / 64KiB = 160 块")
        }
        f.delete()
    }
}
