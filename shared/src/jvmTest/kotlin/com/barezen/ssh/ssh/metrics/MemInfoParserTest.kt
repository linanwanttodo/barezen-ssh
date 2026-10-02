// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/MemInfoParserTest.kt
package com.barezen.ssh.ssh.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemInfoParserTest {
    private val full = """
        MemTotal:        8388608 kB
        MemFree:         2097152 kB
        MemAvailable:    4194304 kB
        Buffers:           95520 kB
        Cached:          1234567 kB
        SwapTotal:             0 kB
    """.trimIndent()

    @Test fun parsesTypicalMeminfo() {
        assertEquals(MemInfo(8_388_608, 4_194_304), MemInfoParser.parse(full))
    }

    @Test fun parsesLargerValues() {
        val r = MemInfoParser.parse("MemTotal: 16384000000 kB\nMemAvailable: 8000000000 kB\n")
        assertEquals(MemInfo(16_384_000_000, 8_000_000_000), r)
    }

    @Test fun emptyInputReturnsNull() {
        assertNull(MemInfoParser.parse(""))
        assertNull(MemInfoParser.parse(null))
    }

    @Test fun missingLinesReturnNull() {
        assertNull(MemInfoParser.parse("MemTotal: 100 kB\n"))
        assertNull(MemInfoParser.parse("MemAvailable: 50 kB\n"))
        assertNull(MemInfoParser.parse("Buffers: 1 kB\nCached: 2 kB\n"))
    }

    @Test fun illegalNumbersReturnNull() {
        assertNull(MemInfoParser.parse("MemTotal: abc kB\nMemAvailable: 50 kB\n"))
        assertNull(MemInfoParser.parse("MemTotal: 100 kB\nMemAvailable: xyz\n"))
    }

    @Test fun truncatedLinesReturnNull() {
        assertNull(MemInfoParser.parse("MemTotal:\nMemAvailable:\n"))
        assertNull(MemInfoParser.parse("MemTotal\nMemAvailable\n"))
    }

    @Test fun veryLongGarbageInputReturnsNull() {
        assertNull(MemInfoParser.parse("x".repeat(8192)))
    }
}
