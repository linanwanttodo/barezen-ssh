// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/CpuUsageCalculatorTest.kt
package com.barezen.ssh.ssh.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CpuUsageCalculatorTest {
    @Test fun busyCpuApproachesHundredPercent() {
        // 两次采样间只有 system 增加：busy 12 / 总 12 = 100%
        val pct = CpuUsageCalculator.delta(
            "cpu  100 0 100 800 0 0 0 0 0 0",
            "cpu  100 0 112 800 0 0 0 0 0 0",
        )
        assertEquals(100.0, pct)
    }

    @Test fun idleCpuStaysNearZero() {
        // 两次采样间只有 idle 增加：busy 0 / 总 100 = 0%
        val pct = CpuUsageCalculator.delta(
            "cpu  100 0 100 800 0 0 0 0 0 0",
            "cpu  100 0 100 900 0 0 0 0 0 0",
        )
        assertEquals(0.0, pct)
    }

    @Test fun mixedUsageComputesRatio() {
        // user +10、idle +100：busy 10 / 总 110 ≈ 9.09%
        val pct = CpuUsageCalculator.delta(
            "cpu  100 0 100 800 0 0 0 0 0 0",
            "cpu  110 0 100 900 0 0 0 0 0 0",
        )
        assertTrue(pct != null && Math.abs(pct - 10.0 * 100 / 110) < 1e-9)
    }

    @Test fun iowaitCountsAsIdle() {
        // 仅 iowait 增加：busy 0 / 总 50 = 0%
        val pct = CpuUsageCalculator.delta(
            "cpu  100 0 100 800 0 0 0 0 0 0",
            "cpu  100 0 100 800 50 0 0 0 0 0",
        )
        assertEquals(0.0, pct)
    }

    @Test fun singleSampleReturnsNull() {
        assertNull(CpuUsageCalculator.delta(null, "cpu  100 0 100 800 0 0 0"))
        assertNull(CpuUsageCalculator.delta("cpu  100 0 100 800 0 0 0", null))
    }

    @Test fun malformedLinesReturnNull() {
        assertNull(CpuUsageCalculator.delta("cat  100 0 100 800", "cpu  100 0 100 800"))
        assertNull(CpuUsageCalculator.delta("cpu  x 0 100 800", "cpu  100 0 100 800"))
        assertNull(CpuUsageCalculator.delta("cpu", "cpu  100 0 100 800"))
        assertNull(CpuUsageCalculator.delta("", ""))
    }

    @Test fun nonAdvancingCountersReturnNull() {
        // 计数器倒退（总数为负）与零增长都无法得出占用率
        assertNull(CpuUsageCalculator.delta("cpu  100 0 100 800", "cpu  50 0 50 400"))
        assertNull(CpuUsageCalculator.delta("cpu  100 0 100 800", "cpu  100 0 100 800"))
    }

    @Test fun resultIsClampedToHundredPercent() {
        // idle 大幅倒退时 busy 超过总数（总增量仍为正），须夹在 100 以内
        val pct = CpuUsageCalculator.delta(
            "cpu  100 0 100 800 0 0 0 0 0 0",
            "cpu  500 0 500 500 0 0 0 0 0 0",
        )
        assertTrue(pct != null && pct <= 100.0)
    }
}
