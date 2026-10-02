// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/FileSizeFormatterTest.kt
package com.barezen.ssh.ssh.sftp

import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeFormatterTest {

    // ---- 字节区（< 1024 显示整数 B）----

    @Test fun zero() = assertEquals("0 B", FileSizeFormatter.format(0L))
    @Test fun singleByte() = assertEquals("1 B", FileSizeFormatter.format(1L))
    @Test fun bytesUnderKb() = assertEquals("512 B", FileSizeFormatter.format(512L))
    @Test fun justUnderKb() = assertEquals("1023 B", FileSizeFormatter.format(1023L))

    // ---- KB ----

    @Test fun exactKb() = assertEquals("1 KB", FileSizeFormatter.format(1024L))
    @Test fun kbFraction() = assertEquals("1.5 KB", FileSizeFormatter.format(1536L))
    @Test fun kbJustUnderMb() = assertEquals("1023.9 KB", FileSizeFormatter.format(1023L * 1024 + 922))

    // ---- MB ----

    @Test fun exactMb() = assertEquals("1 MB", FileSizeFormatter.format(1024L * 1024))
    @Test fun mbFraction() = assertEquals("2.3 MB", FileSizeFormatter.format((2.3 * 1024 * 1024).toLong()))
    @Test fun justUnderMbRoundsUpWithinKbBucket() = assertEquals("1024 KB", FileSizeFormatter.format(1024L * 1024 - 1))

    // ---- GB ----

    @Test fun exactGb() = assertEquals("1 GB", FileSizeFormatter.format(1024L * 1024 * 1024))
    @Test fun gbFraction() = assertEquals("1.5 GB", FileSizeFormatter.format((1.5 * 1024 * 1024 * 1024).toLong()))

    // ---- 超大值封顶在 GB ----

    @Test fun terabyteCappedAtGb() = assertEquals("1024 GB", FileSizeFormatter.format(1024L * 1024 * 1024 * 1024))

    // ---- 非法输入 ----

    @Test fun negativeTreatedAsZero() = assertEquals("0 B", FileSizeFormatter.format(-1L))
}
