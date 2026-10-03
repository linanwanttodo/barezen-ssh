// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/DashboardScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.ssh.metrics.DiskUsage
import com.barezen.ssh.ssh.metrics.MetricsCollector
import com.barezen.ssh.ssh.metrics.MetricsSnapshot
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzCard
import com.barezen.ssh.ui.components.SectionTitle
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenMonoMetric
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.LocalBareZenColors
import java.util.Locale
import kotlin.math.roundToInt

/** CPU 折线图保留的采样数上限。 */
private const val CPU_HISTORY_MAX = 60

/**
 * 仪表盘屏（apple.html `#dashboard`）：屏头（副标题 + 服务器选择 + 刷新）| 四指标卡 |
 * 趋势图卡（含图例）| 磁盘用量卡。
 *
 * 未连接时四指标卡显示「—」、图表卡显示占位文案（无连接不造数）；已连接时由
 * [MetricsSnapshot] 渲染真数据。
 */
@Composable
fun DashboardScreen(
    snapshot: MetricsSnapshot? = null,
    connected: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val extras = LocalBareZenColors.current
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
        // 屏头（apple.css `.screen-header`）：左副标题，右工具组
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = BareZenSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "实时监控 SSH 主机的关键指标",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "数据来源：SSH 主机指标",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(BareZenSpace.md))
            Btn("刷新", onRefresh, kind = BtnKind.secondary, enabled = connected)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = BareZenSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
        ) {
            // 指标格四卡（apple.css `.metric-grid`）：4 等宽 + 16 间距
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BareZenSpace.lg)) {
                MetricCard(
                    label = "CPU 使用率",
                    value = formatPct(snapshot?.cpuPct),
                    trend = snapshot?.cpuPct?.let { "最近 ${it.roundToInt()}%" },
                    accent = extras.metricCpu,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "内存使用",
                    value = formatPct(snapshot?.memUsedPct),
                    trend = memorySubline(snapshot),
                    accent = extras.metricMem,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "平均负载",
                    value = formatLoad1(snapshot?.load1),
                    trend = loadSubline(snapshot),
                    accent = extras.metricLoad,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "运行时间",
                    value = formatUptimeShort(snapshot?.uptimeSeconds),
                    trend = snapshot?.uptimeSeconds?.let { "已运行 ${it.roundToInt() / 86400} 天" },
                    accent = extras.metricUptime,
                    modifier = Modifier.weight(1f),
                )
            }

            // 趋势图卡（apple.css `.chart-card` + `.chart-legend`）
            BzCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = BareZenSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("CPU / 内存 / 网络趋势")
                    Spacer(Modifier.weight(1f))
                    LegendDot("CPU", extras.metricCpu)
                    Spacer(Modifier.width(BareZenSpace.lg))
                    LegendDot("内存", extras.metricMem)
                    Spacer(Modifier.width(BareZenSpace.lg))
                    LegendDot("网络", extras.metricNet)
                }
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    if (cpuHistory.size < 2) {
                        EmptyHint(Icons.Outlined.Insights, emptyChartText)
                    } else {
                        CpuChart(cpuHistory)
                    }
                }
            }

            // 磁盘用量卡
            BzCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = BareZenSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("磁盘用量")
                    Spacer(Modifier.weight(1f))
                    val used = snapshot?.disks?.sumOf { it.usedKb } ?: 0L
                    val total = snapshot?.disks?.sumOf { it.totalKb } ?: 0L
                    Text(
                        if (total > 0) "已用 ${formatGb(used)} / ${formatGb(total)}" else "—",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = BareZenMonoBody,
                    )
                }
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    if (snapshot?.disks.isNullOrEmpty()) {
                        EmptyHint(Icons.Outlined.Insights, emptyChartText)
                    } else {
                        DiskBars(snapshot!!.disks)
                    }
                }
            }
        }
    }
}

/**
 * AppShell 接线层：跟随**活动会话**创建/销毁 [MetricsCollector]，并把快照交给 [DashboardScreen]。
 *
 * key 用会话 id 而非布尔 connected：两条会话**同时 Connected** 时布尔值恒为 true，
 * 只以它为 key 的 LaunchedEffect 不会重跑，切换活动会话后仍会显示旧会话的指标。
 */
@Composable
fun DashboardHost(model: AppModel) {
    val active = model.registry.active
    val scope = rememberCoroutineScope()
    var collector by remember { mutableStateOf<MetricsCollector?>(null) }

    LaunchedEffect(active?.id) {
        val session = active?.session
        if (active != null && session != null) {
            val c = MetricsCollector(session)
            collector = c
            c.start(scope)
        } else {
            collector?.stop()
            collector = null
        }
    }
    DisposableEffect(active?.id) {
        onDispose { collector?.stop() }
    }

    val snapshot = collector?.snapshot?.collectAsState()?.value
    DashboardScreen(snapshot = snapshot, connected = active != null) { collector?.refresh() }
}

// ---- 指标卡 ----

/**
 * 指标卡（apple.css `.metric-card`）：panel 底 + 描边 + 圆角 10；顶部 3dp 色条按指标分色
 * （`--metric-accent`）；值位等宽 30sp/600，缺数据一律「—」（不造数）。
 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    trend: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    // 整卡 clip：顶部色条必须被卡片的圆角裁切，否则 3dp 直角条会从圆角外露出去。
    Box(modifier.clip(shape)) {
        // 顶部色条（apple.css `.metric-card::before`，3dp、按指标分色、85% 不透明）
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(accent.copy(alpha = 0.85f)),
        )
        Surface(
            color = colors.surfaceContainer,
            shape = shape,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    value,
                    style = BareZenMonoMetric,
                    color = colors.onSurface,
                )
                Text(
                    trend ?: "—",
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 图例（apple.css `.legend-item`）：8dp 圆点 + 11sp 文字。 */
@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 格式化（null 一律回退「—」，不造数）----

private fun formatPct(v: Double?): String = if (v == null) "—" else "${v.roundToInt()}%"

private fun formatLoad1(v: Double?): String = if (v == null) "—" else String.format(Locale.ROOT, "%.2f", v)

/** 运行时间走「天」为单位的紧凑写法（apple.html 显示 `45天`）。 */
private fun formatUptimeShort(seconds: Double?): String {
    if (seconds == null || seconds < 0) return "—"
    val days = (seconds / 86_400).toLong()
    return if (days > 0) "$days" else "${seconds.toLong() / 3600}"
}

private fun memorySubline(snapshot: MetricsSnapshot?): String? {
    val usedPct = snapshot?.memUsedPct ?: return null
    return "已用 ${usedPct.roundToInt()}%"
}

private fun loadSubline(snapshot: MetricsSnapshot?): String? {
    val l5 = snapshot?.load5 ?: return null
    return "5 分钟 ${String.format(Locale.ROOT, "%.2f", l5)}"
}

/** KB -> GB，一位小数（磁盘卡头部汇总用）。 */
private fun formatGb(kb: Long): String = String.format(Locale.ROOT, "%.1f G", kb / 1024.0 / 1024.0)

// ---- 图表 ----

/** CPU 折线：最近 60 个采样，0-100% 映射全高，附 0/50/100 三条基线。 */
@Composable
private fun CpuChart(history: List<Double>) {
    val extras = LocalBareZenColors.current
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
            drawPath(path, color = extras.metricCpu, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** 磁盘用量：每个挂载点一行，挂载点 + 比例条 + 百分比（不引入图表库）。 */
@Composable
private fun DiskBars(disks: List<DiskUsage>) {
    val extras = LocalBareZenColors.current
    val trackColor = MaterialTheme.colorScheme.outline
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
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(trackColor, RoundedCornerShape(3.dp)),
                ) {
                    val fraction = (d.capacityPct / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(extras.metricLoad, RoundedCornerShape(3.dp)),
                    )
                }
                Text("${d.capacityPct}%", fontSize = 12.sp, style = BareZenMonoBody)
            }
        }
    }
}
