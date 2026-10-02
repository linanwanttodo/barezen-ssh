// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/UptimeParserTest.kt
package com.barezen.ssh.ssh.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UptimeParserTest {
    @Test fun parsesTypicalProcUptime() {
        assertEquals(Uptime(93720.35), UptimeParser.parse("93720.35 180000.00"))
    }

    @Test fun parsesSingleField() {
        assertEquals(Uptime(123.0), UptimeParser.parse("123.0"))
    }

    @Test fun parsesWithSurroundingWhitespace() {
        assertEquals(Uptime(42.5), UptimeParser.parse("  42.5  84.0  "))
    }

    @Test fun emptyInputReturnsNull() {
        assertNull(UptimeParser.parse(""))
        assertNull(UptimeParser.parse("   "))
        assertNull(UptimeParser.parse(null))
    }

    @Test fun truncatedInputReturnsNull() {
        assertNull(UptimeParser.parse("."))
        assertNull(UptimeParser.parse("-"))
    }

    @Test fun illegalNumbersReturnNull() {
        assertNull(UptimeParser.parse("abc 180000.00"))
        assertNull(UptimeParser.parse("12.34.56 1.0"))
    }

    @Test fun veryLongGarbageLineReturnsNull() {
        assertNull(UptimeParser.parse("9".repeat(8192) + "x"))
    }
}
