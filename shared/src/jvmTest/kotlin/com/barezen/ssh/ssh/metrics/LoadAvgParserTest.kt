// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/LoadAvgParserTest.kt
package com.barezen.ssh.ssh.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadAvgParserTest {
    @Test fun parsesTypicalProcLoadavg() {
        val r = LoadAvgParser.parse("0.52 0.58 0.59 1/412 8123")
        assertEquals(LoadAvg(0.52, 0.58, 0.59), r)
    }

    @Test fun parsesThreeFieldsOnly() {
        val r = LoadAvgParser.parse("1.0 2.0 3.0")
        assertEquals(LoadAvg(1.0, 2.0, 3.0), r)
    }

    @Test fun parsesIrregularWhitespace() {
        val r = LoadAvgParser.parse("  12.34\t 5.6   7.8   9/99 12345  ")
        assertEquals(LoadAvg(12.34, 5.6, 7.8), r)
    }

    @Test fun emptyInputReturnsNull() {
        assertNull(LoadAvgParser.parse(""))
        assertNull(LoadAvgParser.parse("   "))
        assertNull(LoadAvgParser.parse(null))
    }

    @Test fun truncatedInputReturnsNull() {
        assertNull(LoadAvgParser.parse("0.52"))
        assertNull(LoadAvgParser.parse("0.52 0.58"))
    }

    @Test fun illegalNumbersReturnNull() {
        assertNull(LoadAvgParser.parse("abc 0.58 0.59 1/412 8123"))
        assertNull(LoadAvgParser.parse("0.52 x 0.59"))
        assertNull(LoadAvgParser.parse("0.52 0.58 0.59e"))
    }

    @Test fun veryLongGarbageLineReturnsNull() {
        assertNull(LoadAvgParser.parse("9".repeat(4096) + " " + "x ".repeat(100)))
    }
}
