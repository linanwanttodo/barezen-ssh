// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsRows.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置行控件集。形状照设计包 `.setting-row` —— **分隔线行，不是卡片**：
 * `padding: 16px 0` + 底边 `1px border-subtle`（末行无）；无底色、无圆角、无整圈描边。
 */

@Composable
private fun RowShell(
    title: String,
    desc: String?,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (desc != null) {
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun ToggleRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RowShell(title, desc) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 分段选择。`enabled=false` 整体禁用；`disabledIndices` 用于「未实现」的个别选项（如浅色主题）。 */
@Composable
fun ChoiceRow(
    title: String,
    desc: String?,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    disabledIndices: Set<Int> = emptySet(),
    onSelect: (Int) -> Unit,
) {
    RowShell(title, desc) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                // 禁用项：可见文案但不可点（不造假选项）
                val itemEnabled = enabled && i !in disabledIndices
                val selected = i == selectedIndex
                if (selected) {
                    Button(onClick = { onSelect(i) }, enabled = itemEnabled) { Text(label, fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = { onSelect(i) }, enabled = itemEnabled) { Text(label, fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 静态值行：**无可交互控件**。用于当前只有一个真实可选项的设置
 * （照实渲染单值，不造假备选 —— 不造数红线）。标题不进语义树，避免与值重复播报。
 */
@Composable
fun StaticValueRow(title: String, desc: String?, value: String) {
    RowShell(title, desc) {
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ActionRow(
    title: String,
    desc: String?,
    actions: List<Pair<String, () -> Unit>>,
) {
    RowShell(title, desc) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (label, onClick) ->
                OutlinedButton(onClick = onClick) { Text(label, fontSize = 12.sp) }
            }
        }
    }
}

/** 分类内容外壳：标题不进语义树（左栏选中项已播报同词）。 */
@Composable
fun SettingsSectionScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** 顶部通知（加载告警 / 保存失败）。可关闭。 */
@Composable
fun SettingsNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}
