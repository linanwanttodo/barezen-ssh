// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/TransferThrottlerTest.kt
package com.barezen.ssh.ssh.sftp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferThrottlerTest {

    private fun throttler(intervalMs: Long = 1_000L, byteInterval: Long = 256L * 1024) =
        TransferThrottler(intervalMs, byteInterval)

    // ---- 首次调用恒发出 ----

    @Test fun firstCallAlwaysEmits() = assertTrue(throttler().shouldEmit(0L, 1L))

    // ---- 时间节流：间隔未到不发、到点发 ----

    @Test fun suppressesWithinTimeInterval() {
        val t = throttler()
        assertTrue(t.shouldEmit(0L, 1L))
        assertFalse(t.shouldEmit(999L, 1L))
    }

    @Test fun emitsWhenTimeIntervalReached() {
        val t = throttler()
        assertTrue(t.shouldEmit(0L, 1L))
        assertTrue(t.shouldEmit(1_000L, 1L))
    }

    @Test fun emitsExactlyAtIntervalBoundary() {
        val t = throttler(intervalMs = 500)
        assertTrue(t.shouldEmit(0L, 0L))
        assertFalse(t.shouldEmit(499L, 0L))
        assertTrue(t.shouldEmit(500L, 0L))
    }

    // ---- 字节节流：累计达 byteInterval 即发，不受时间影响 ----

    @Test fun suppressesUntilByteIntervalAccumulated() {
        val t = throttler(byteInterval = 1024)
        assertTrue(t.shouldEmit(0L, 100))
        assertFalse(t.shouldEmit(1L, 900))   // 累计 900（首发出后重置），未达 1024，时间也没到
        assertTrue(t.shouldEmit(2L, 124))    // 累计恰好 1024
    }

    @Test fun byteCounterResetsAfterEmit() {
        val t = throttler(byteInterval = 100)
        assertTrue(t.shouldEmit(0L, 100))
        assertFalse(t.shouldEmit(1L, 50))    // 重置后 50 < 100
        assertTrue(t.shouldEmit(2L, 50))     // 累计 100
    }

    @Test fun singleChunkLargerThanByteIntervalEmitsImmediately() {
        val t = throttler(byteInterval = 10)
        assertTrue(t.shouldEmit(0L, 11))
        assertTrue(t.shouldEmit(1L, 11))
    }

    // ---- 零进度块：不推进、不发出（时间未到时）----

    @Test fun zeroDeltaDoesNotEmitBeforeInterval() {
        val t = throttler()
        assertTrue(t.shouldEmit(0L, 0L))
        assertFalse(t.shouldEmit(1L, 0L))
        assertFalse(t.shouldEmit(2L, 0L))
    }
}
