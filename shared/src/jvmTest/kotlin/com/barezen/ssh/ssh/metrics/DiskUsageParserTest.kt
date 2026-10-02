// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/DiskUsageParserTest.kt
package com.barezen.ssh.ssh.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiskUsageParserTest {
    private val typical = """
        Filesystem     1024-blocks      Used Available Capacity Mounted on
        /dev/sda1         40188544  12345678  25772034      33% /
        tmpfs              2048000         0   2048000       0% /dev/shm
        devtmpfs            102400         0    102400       0% /dev
        overlay            40188544  12345678  25772034      33% /var/lib/docker
        /dev/sdb1         52428800  26214400  26214400      50% /mnt/data
    """.trimIndent()

    @Test fun parsesTypicalDfOutputSkippingHeaderAndPseudoFilesystems() {
        val disks = DiskUsageParser.parseDf(typical)
        assertEquals(2, disks.size)
        assertEquals(
            DiskUsage("/dev/sda1", 40_188_544, 12_345_678, 25_772_034, 33, "/"),
            disks[0],
        )
        assertEquals(
            DiskUsage("/dev/sdb1", 52_428_800, 26_214_400, 26_214_400, 50, "/mnt/data"),
            disks[1],
        )
    }

    @Test fun rootMountIsAlwaysKept() {
        val disks = DiskUsageParser.parseDf(
            "Filesystem 1024-blocks Used Available Capacity Mounted on\n" +
                "/dev/sda1 100 50 50 50% /\n",
        )
        assertEquals(1, disks.size)
        assertEquals("/", disks[0].mountedOn)
    }

    @Test fun emptyAndHeaderOnlyInputYieldEmptyList() {
        assertTrue(DiskUsageParser.parseDf("").isEmpty())
        assertTrue(DiskUsageParser.parseDf(null).isEmpty())
        assertTrue(
            DiskUsageParser.parseDf("Filesystem 1024-blocks Used Available Capacity Mounted on\n").isEmpty(),
        )
    }

    @Test fun malformedLinesAreSkippedOthersKept() {
        val out = DiskUsageParser.parseDf(
            """
            Filesystem 1024-blocks Used Available Capacity Mounted on
            /dev/sda1 notanumber 50 50 50% /
            /dev/sdb1 100 50
            /dev/sdc1 100 50 50 50% /
            """.trimIndent(),
        )
        assertEquals(1, out.size)
        assertEquals("/dev/sdc1", out[0].filesystem)
    }

    @Test fun mountPointWithSpacesIsPreserved() {
        val out = DiskUsageParser.parseDf(
            "Filesystem 1024-blocks Used Available Capacity Mounted on\n" +
                "/dev/sda1 100 50 50 50% /mnt/my data\n",
        )
        assertEquals(1, out.size)
        assertEquals("/mnt/my data", out[0].mountedOn)
    }

    @Test fun longGarbageInputYieldsEmptyList() {
        assertTrue(DiskUsageParser.parseDf("garbage ".repeat(1000)).isEmpty())
    }
}
