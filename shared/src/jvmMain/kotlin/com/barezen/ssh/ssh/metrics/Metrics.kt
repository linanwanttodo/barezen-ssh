// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/metrics/Metrics.kt
package com.barezen.ssh.ssh.metrics

/** /proc/loadavg 解析结果：1/5/15 分钟平均负载。 */
data class LoadAvg(val load1: Double, val load5: Double, val load15: Double)

/** /proc/meminfo 解析结果：总量与可用内存（kB）。 */
data class MemInfo(val totalKb: Long, val availableKb: Long)

/** /proc/uptime 解析结果：开机秒数。 */
data class Uptime(val seconds: Double)

/** df -P 单行解析结果：一个挂载点的磁盘用量。 */
data class DiskUsage(
    val filesystem: String,
    val totalKb: Long,
    val usedKb: Long,
    val availableKb: Long,
    val capacityPct: Int,
    val mountedOn: String,
)

/** 一次采集周期的主机指标快照；任一字段解析失败置 null（不整体失败）。 */
data class MetricsSnapshot(
    val load1: Double?,
    val load5: Double?,
    val load15: Double?,
    val memUsedPct: Double?,
    val memTotalKb: Long?,
    val uptimeSeconds: Double?,
    val cpuPct: Double?,
    val disks: List<DiskUsage>,
    val epochMs: Long,
)
