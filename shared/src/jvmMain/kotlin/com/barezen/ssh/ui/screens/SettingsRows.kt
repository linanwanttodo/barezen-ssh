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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.ui.components.Banner
import com.barezen.ssh.ui.components.BadgeTone
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzCard
import com.barezen.ssh.ui.components.BzSwitch
import com.barezen.ssh.ui.components.SegmentedControl
import com.barezen.ssh.ui.theme.BareZenSpace

/**
 * 设置行控件集（apple.css `.setting-card` + `.setting-row`）：
 * 行是 **panel 卡片内的一条横向分隔行**（padding 16/0 + 底边 1px border-subtle，末行无），
 * 卡片本身才是有底色与描边的容器。行控件一律靠右，行高与 32dp 控件对齐。
 */

@Composable
private fun RowShell(
    title: String,
    desc: String?,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = BareZenSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (desc != null) {
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(BareZenSpace.md))
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
    enabled: Boolean = true,
) {
    RowShell(title, desc) {
        BzSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 分段选择。`disabledIndices` 用于「未实现」的个别选项（如浅色主题）。 */
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
        SegmentedControl(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            disabledIndices = disabledIndices,
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(BareZenSpace.sm)) {
            actions.forEach { (label, onClick) ->
                Btn(label, onClick, kind = BtnKind.secondary, small = true)
            }
        }
    }
}

/** 分类内容外壳（apple.css `.settings-content`）：标题 18/700 + 32 内边距。 */
@Composable
fun SettingsSectionScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(BareZenSpace.xxxl)) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {}.padding(bottom = BareZenSpace.xl),
        )
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 设置卡片容器（apple.css `.setting-card`）：panel 底 + 描边 + 圆角 10，
 * 上下内边距 0（由内部行的 padding 控制），行间 1px 分隔。
 */
@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    BzCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = BareZenSpace.xl), content = content)
    }
}

/** 顶部通知（加载告警 / 保存失败）。可关闭。 */
@Composable
fun SettingsNotice(text: String, onDismiss: () -> Unit) {
    Banner(
        text = text,
        tone = BadgeTone.error,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Btn("知道了", onDismiss, kind = BtnKind.ghost, small = true)
    }
}
