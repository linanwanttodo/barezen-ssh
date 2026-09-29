// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/DashboardScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.ui.theme.BareZenMonoBody

/**
 * 仪表盘屏（index.html「仪表盘屏 · 占位布局（M4）」）：
 * 选择服务器/刷新均禁用；四个指标值一律「—」（不造数红线——无 SSH 主机指标真源）；
 * 两张图表卡仅标题占位 + 图标。
 */
@Composable
fun DashboardScreen() {
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
            OutlinedButton(onClick = {}, enabled = false) { Text("刷新") }
        }

        Column(
            Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 指标格四卡：标签 10sp secondary + 值「—」（BareZenMonoBody）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("CPU", Modifier.weight(1f))
                MetricCard("内存", Modifier.weight(1f))
                MetricCard("平均负载", Modifier.weight(1f))
                MetricCard("运行时间", Modifier.weight(1f))
            }

            // 两张图表卡：160dp 高、标题在顶、卡内居中图标
            ChartCard("CPU / 内存 / 网络折线图占位（M4）")
            ChartCard("磁盘用量条形图占位（M4）")
        }
    }
}

/** 指标卡：panel 皮肤（通用卡片式样），值位「—」。 */
@Composable
private fun MetricCard(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("—", style = BareZenMonoBody)
        }
    }
}

/** 图表占位卡：160dp 高、标题顶部、居中 Insights 图标（弱化）。 */
@Composable
private fun ChartCard(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        color = MaterialTheme.colorScheme.background,
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
                Icon(
                    Icons.Outlined.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
