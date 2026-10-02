// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/metrics/MetricsCollector.kt
package com.barezen.ssh.ssh.metrics

import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * 主机指标轮询协调器：周期经 [SshSession.exec] 采集 /proc 与 df -P，
 * 组装 [MetricsSnapshot] 暴露在 [snapshot]。
 * 约定：采集命令失败（exitCode 非 0 或 null）整体置 null 并继续轮询；
 * 单项解析失败仅该字段置 null；stop 后清空快照。
 */
class MetricsCollector(
    private val session: SshSession,
    private val intervalMs: Long = 5_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _snapshot = MutableStateFlow<MetricsSnapshot?>(null)
    val snapshot: StateFlow<MetricsSnapshot?> = _snapshot

    private var job: Job? = null
    private val refreshTick = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var lastCpuLine: String? = null

    /** 在 scope 内启动轮询；已在运行则幂等忽略。首轮立即采集。 */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    /** 停止轮询并清空快照（CPU 采样基线一并复位）。 */
    fun stop() {
        job?.cancel()
        job = null
        lastCpuLine = null
        _snapshot.value = null
    }

    /** 立即触发一轮采集（打断当前等待，不影响轮询节奏）。 */
    fun refresh() {
        refreshTick.trySend(Unit)
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            _snapshot.value = collectOnce()
            // 等满一个周期；期间收到 refresh 信号则提前进入下一轮
            withTimeoutOrNull(intervalMs.milliseconds) { refreshTick.receive() }
        }
    }

    private suspend fun collectOnce(): MetricsSnapshot? {
        val loadavg = execText("cat /proc/loadavg") ?: return null
        val meminfo = execText("cat /proc/meminfo") ?: return null
        val uptime = execText("cat /proc/uptime") ?: return null
        val stat = execText("cat /proc/stat") ?: return null
        val df = execText("df -P") ?: return null

        val load = LoadAvgParser.parse(loadavg)
        val mem = MemInfoParser.parse(meminfo)
        val up = UptimeParser.parse(uptime)
        val disks = DiskUsageParser.parseDf(df)

        // CPU 占用率：相邻两次 /proc/stat 首行差值；首轮只有基线，置 null
        val cpuLine = stat.lineSequence().firstOrNull { it.startsWith("cpu ") }?.trim()
        val cpuPct = lastCpuLine?.let { last ->
            cpuLine?.let { cur -> CpuUsageCalculator.delta(last, cur) }
        }
        if (cpuLine != null) lastCpuLine = cpuLine

        val memUsedPct = mem?.let { m ->
            if (m.totalKb > 0) (m.totalKb - m.availableKb) * 100.0 / m.totalKb else null
        }

        return MetricsSnapshot(
            load1 = load?.load1,
            load5 = load?.load5,
            load15 = load?.load15,
            memUsedPct = memUsedPct,
            memTotalKb = mem?.totalKb,
            uptimeSeconds = up?.seconds,
            cpuPct = cpuPct,
            disks = disks,
            epochMs = System.currentTimeMillis(),
        )
    }

    /** 单条采集命令：exitCode 非 0 或超时（null）视为失败。 */
    private suspend fun execText(command: String): String? = withContext(ioDispatcher) {
        val r = session.exec(command)
        if (r.exitCode == 0) r.stdout else null
    }
}
