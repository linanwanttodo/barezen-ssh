// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/metrics/MetricsCollectorTest.kt
package com.barezen.ssh.ssh.metrics

import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.FakeSshSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsCollectorTest {

    private val canned = mapOf(
        "cat /proc/loadavg" to "0.52 0.58 0.59 1/412 8123",
        "cat /proc/meminfo" to
            "MemTotal:        8388608 kB\nMemFree:         2097152 kB\n" +
            "MemAvailable:    4194304 kB\nBuffers:           95520 kB\nCached:          1234567 kB\n",
        "cat /proc/uptime" to "93720.35 180000.00",
        "df -P" to
            "Filesystem     1024-blocks      Used Available Capacity Mounted on\n" +
            "/dev/sda1         40188544  12345678  25772034      33% /\n" +
            "tmpfs              2048000         0   2048000       0% /dev/shm\n" +
            "/dev/sdb1         52428800  26214400  26214400      50% /mnt/data\n",
    )

    private fun fakeSession(statLine: String, failCommand: String? = null) = FakeSshSession { cmd ->
        when (cmd) {
            failCommand -> ExecResult(1, "", "boom")
            "cat /proc/stat" -> ExecResult(0, "$statLine\ncpu0 1 2 3 4 5 6 7 8 9\n", "")
            else -> ExecResult(0, canned[cmd] ?: "", "")
        }
    }

    private var collector: MetricsCollector? = null

    @AfterTest fun tearDown() {
        collector?.stop()
    }

    @Test fun firstCycleAssemblesSnapshotWithNullCpu() = runTest {
        val session = fakeSession("cpu  100 0 100 800 0 0 0 0 0 0")
        collector = MetricsCollector(session, intervalMs = 60_000, ioDispatcher = Dispatchers.Unconfined)
        collector!!.start(backgroundScope)
        runCurrent()

        val s = collector!!.snapshot.value
        assertTrue(s != null)
        assertEquals(0.52, s!!.load1)
        assertEquals(0.58, s.load5)
        assertEquals(0.59, s.load15)
        assertEquals(8_388_608L, s.memTotalKb)
        assertEquals(50.0, s.memUsedPct)   // (8388608-4194304)/8388608 = 50%
        assertEquals(93_720.35, s.uptimeSeconds)
        assertNull(s.cpuPct)               // 单采样：CPU 占用率不可得
        assertEquals(2, s.disks.size)      // tmpfs 被跳过，根分区保留
        assertEquals("/", s.disks[0].mountedOn)
        assertEquals(listOf("cat /proc/loadavg", "cat /proc/meminfo", "cat /proc/uptime", "cat /proc/stat", "df -P"), session.execCalls.take(5))
    }

    @Test fun secondCycleComputesCpuPercent() = runTest {
        var statCalls = 0
        val session = FakeSshSession { cmd ->
            when (cmd) {
                "cat /proc/stat" -> if (statCalls++ == 0) {
                    ExecResult(0, "cpu  100 0 100 800 0 0 0 0 0 0\n", "")
                } else {
                    ExecResult(0, "cpu  100 0 112 800 0 0 0 0 0 0\n", "")
                }
                else -> ExecResult(0, canned[cmd] ?: "", "")
            }
        }
        collector = MetricsCollector(session, intervalMs = 60_000, ioDispatcher = Dispatchers.Unconfined)
        collector!!.start(backgroundScope)
        runCurrent()
        assertNull(collector!!.snapshot.value?.cpuPct)

        advanceTimeBy(60_000)   // 虚拟时间推进一个轮询周期
        runCurrent()
        assertEquals(100.0, collector!!.snapshot.value?.cpuPct)
    }

    @Test fun commandFailureNullsSnapshotAndRecovers() = runTest {
        var statCalls = 0
        val session = FakeSshSession { cmd ->
            when (cmd) {
                "df -P" -> if (statCalls++ == 1) {
                    ExecResult(1, "", "df: permission denied")
                } else {
                    ExecResult(0, canned[cmd] ?: "", "")
                }
                else -> ExecResult(0, canned[cmd] ?: "", "")
            }
        }
        collector = MetricsCollector(session, intervalMs = 60_000, ioDispatcher = Dispatchers.Unconfined)
        collector!!.start(backgroundScope)
        runCurrent()
        assertTrue(collector!!.snapshot.value != null)

        advanceTimeBy(60_000)   // 第二周期 df 失败：整体置 null
        runCurrent()
        assertNull(collector!!.snapshot.value)

        advanceTimeBy(60_000)   // 第三周期恢复：不退出轮询
        runCurrent()
        assertTrue(collector!!.snapshot.value != null)
    }

    @Test fun parseFailureKeepsSnapshotWithNullFields() = runTest {
        val session = FakeSshSession { cmd ->
            when (cmd) {
                "cat /proc/loadavg" -> ExecResult(0, "not-numbers at all", "")
                else -> ExecResult(0, canned[cmd] ?: "", "")
            }
        }
        collector = MetricsCollector(session, intervalMs = 60_000, ioDispatcher = Dispatchers.Unconfined)
        collector!!.start(backgroundScope)
        runCurrent()

        val s = collector!!.snapshot.value
        assertTrue(s != null)              // 解析失败不整体失败
        assertNull(s!!.load1)
        assertNull(s.load5)
        assertNull(s.load15)
        assertEquals(50.0, s.memUsedPct)
    }

    @Test fun refreshTriggersImmediateCycle() = runTest {
        val session = fakeSession("cpu  100 0 100 800 0 0 0 0 0 0")
        collector = MetricsCollector(session, intervalMs = 3_600_000, ioDispatcher = Dispatchers.Unconfined)
        collector!!.start(backgroundScope)
        runCurrent()
        val firstCount = session.execCalls.size

        collector!!.refresh()
        runCurrent()
        assertTrue(session.execCalls.size > firstCount, "refresh 应立即触发一轮采集")
    }

    @Test fun stopStopsPollingAndClearsSnapshot() = runTest {
        val session = fakeSession("cpu  100 0 100 800 0 0 0 0 0 0")
        val c = MetricsCollector(session, intervalMs = 60_000, ioDispatcher = Dispatchers.Unconfined)
        collector = c
        c.start(backgroundScope)
        runCurrent()
        assertTrue(c.snapshot.value != null)

        val count = session.execCalls.size
        c.stop()
        assertNull(c.snapshot.value)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(count, session.execCalls.size, "stop 后不得继续轮询")
    }
}
