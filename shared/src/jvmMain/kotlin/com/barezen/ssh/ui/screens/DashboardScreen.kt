// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/DashboardScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.metrics.DiskUsage
import com.barezen.ssh.ssh.metrics.MetricsCollector
import com.barezen.ssh.ssh.metrics.MetricsSnapshot
import com.barezen.ssh.ui.theme.BareZenMonoBody
import java.util.Locale
import kotlin.math.roundToInt

/** CPU 折线图保留的采样数上限。 */
private const val CPU_HISTORY_MAX = 60

/**
 * 仪表盘屏：未连接时四指标卡显示「—」、图表卡显示占位文案（无连接不造数）；
 * 已连接时由 [MetricsSnapshot] 渲染真数据。「刷新」仅在连接后可用。
 */
@Composable
fun DashboardScreen(
    snapshot: MetricsSnapshot? = null,
    connected: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    // CPU 折线：跨快照累积最近 60 个采样（epochMs 变化即新快照）
    val cpuHistory = remember { mutableStateListOf<Double>() }
    LaunchedEffect(snapshot?.epochMs) {
        snapshot?.cpuPct?.let { v ->
            cpuHistory.add(v)
            while (cpuHistory.size > CPU_HISTORY_MAX) cpuHistory.removeAt(0)
        }
    }
    val emptyChartText = if (connected) "等待主机指标" else "连接后显示主机指标"

    Column(Modifier.fillMaxSize()) {
        // 屏头（通用式样：56dp、horizontal 24）
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("仪表盘", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = {}, enabled = false) { Text("选择服务器") }
            Spacer(Modifier.weight(1f))
            Text(
                "数据来源：SSH 主机指标",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRefresh, enabled = connected) { Text("刷新") }
        }

        Column(
            Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 指标格四卡：标签 10sp secondary + 值（BareZenMonoBody）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("CPU", formatPct(snapshot?.cpuPct), modifier = Modifier.weight(1f))
                MetricCard("内存", formatPct(snapshot?.memUsedPct), memorySubline(snapshot), modifier = Modifier.weight(1f))
                MetricCard("平均负载", formatLoad1(snapshot?.load1), loadSubline(snapshot), modifier = Modifier.weight(1f))
                MetricCard("运行时间", formatUptime(snapshot?.uptimeSeconds), modifier = Modifier.weight(1f))
            }

            // 两张图表卡：按权重等高填满剩余高度（高度约束见原占位版注释，不叠 heightIn）
            ChartCard(
                title = "CPU 使用率（最近 60 次采样）",
                empty = cpuHistory.size < 2,
                emptyText = emptyChartText,
                modifier = Modifier.weight(1f),
            ) {
                CpuChart(cpuHistory)
            }
            ChartCard(
                title = "磁盘用量",
                empty = snapshot?.disks.isNullOrEmpty(),
                emptyText = emptyChartText,
                modifier = Modifier.weight(1f),
            ) {
                DiskBars(snapshot!!.disks)
            }
        }
    }
}

/**
 * AppShell 接线层：跟随连接状态创建/销毁 [MetricsCollector]（轮询经 SshSession.exec 采集），
 * 并把快照交给 [DashboardScreen]。会话切换（重连）会经过 Connecting，收集器随之重建。
 */
@Composable
fun DashboardHost(model: AppModel) {
    val connected = model.connection is ConnectionState.Connected
    val scope = rememberCoroutineScope()
    var collector by remember { mutableStateOf<MetricsCollector?>(null) }

    LaunchedEffect(connected) {
        val session = model.shellSession
        if (connected && session != null) {
            val c = MetricsCollector(session)
            collector = c
            c.start(scope)
        } else {
            collector?.stop()
            collector = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { collector?.stop() }
    }

    val snapshot = collector?.snapshot?.collectAsState()?.value
    DashboardScreen(snapshot = snapshot, connected = connected) { collector?.refresh() }
}

// ---- 指标卡 ----

/** 指标卡：surface 皮肤（设计包 .metric-card background: var(--surface)），值位缺数据时「—」。 */
@Composable
private fun MetricCard(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = BareZenMonoBody)
            if (sub != null) {
                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- 格式化（null 一律回退「—」，不造数）----

private fun formatPct(v: Double?): String = if (v == null) "—" else "${v.roundToInt()}%"

private fun formatLoad1(v: Double?): String = if (v == null) "—" else String.format(Locale.ROOT, "%.2f", v)

private fun formatUptime(seconds: Double?): String {
    if (seconds == null || seconds < 0) return "—"
    val s = seconds.toLong()
    val days = s / 86_400
    val hours = (s % 86_400) / 3_600
    val minutes = (s % 3_600) / 60
    return when {
        days > 0 -> "$days 天 $hours 小时"
        hours > 0 -> "$hours 小时 $minutes 分钟"
        else -> "$minutes 分钟"
    }
}

private fun formatGb(kb: Long): String = String.format(Locale.ROOT, "%.1f GB", kb / 1024.0 / 1024.0)

private fun memorySubline(snapshot: MetricsSnapshot?): String? {
    val total = snapshot?.memTotalKb ?: return null
    val usedPct = snapshot.memUsedPct ?: return null
    val usedKb = (total * usedPct / 100.0).toLong()
    return "已用 ${formatGb(usedKb)} / 总 ${formatGb(total)}"
}

private fun loadSubline(snapshot: MetricsSnapshot?): String? {
    val l5 = snapshot?.load5 ?: return null
    val l15 = snapshot.load15 ?: return null
    return "5 分钟 ${String.format(Locale.ROOT, "%.2f", l5)} / 15 分钟 ${String.format(Locale.ROOT, "%.2f", l15)}"
}

// ---- 图表卡与图形 ----

/** 图表卡：surface 底（设计包 .chart-card）、标题顶部；空态为居中图标 + 占位文案。 */
@Composable
private fun ChartCard(
    title: String,
    empty: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        // 高度由调用方 weight(1f) 决定；不叠 heightIn（在精确约束下无效，见调用处注释）
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (empty) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Insights,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            emptyText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    content()
                }
            }
        }
    }
}

/** CPU 折线：最近 60 个采样，0-100% 映射全高，附 0/50/100 三条基线。 */
@Composable
private fun CpuChart(history: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        listOf(0f, 0.5f, 1f).forEach { g ->
            val y = size.height * g
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (history.size >= 2) {
            val stepX = size.width / (CPU_HISTORY_MAX - 1)
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height * (1f - (v / 100.0).toFloat().coerceIn(0f, 1f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** 磁盘用量：每个挂载点一行，挂载点 + 比例条 + 百分比（不引入图表库）。 */
@Composable
private fun DiskBars(disks: List<DiskUsage>) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        disks.take(6).forEach { d ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    d.mountedOn,
                    fontSize = 12.sp,
                    style = BareZenMonoBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                Box(
                    Modifier.weight(1f)
                        .height(8.dp)
                        .background(trackColor, RoundedCornerShape(4.dp)),
                ) {
                    val fraction = (d.capacityPct / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(barColor, RoundedCornerShape(4.dp)),
                    )
                }
                Text("${d.capacityPct}%", fontSize = 12.sp, style = BareZenMonoBody)
            }
        }
    }
}
