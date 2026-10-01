// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.ui.theme.focusRing

/** 设置八分类（plan 决策表：外观 4 行全渲染但禁用；其余 7 分类 = 占位空态）。 */
private val SettingsCategories =
    listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")

/**
 * 设置屏（index.html「设置屏 · 分类布局」）：左列 200dp 分类导航（客户端状态切换）+
 * 右侧内容。无持久化后端——所有设置按钮禁用（占位，不造数）。
 */
@Composable
fun SettingsScreen() {
    var selected by remember { mutableStateOf(0) }

    Row(Modifier.fillMaxSize()) {
        // 左列：200dp、surface 底（与主侧栏同层）、右 1dp border
        Column(
            Modifier.width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsCategories.forEachIndexed { index, name ->
                val active = index == selected
                val catShape = RoundedCornerShape(6.dp)
                Row(
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .clip(catShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.surfaceContainer
                            else Color.Transparent
                        )
                        // 键盘焦点环（需求 §2 硬约束 3）：须在 clickable 之前才能观察到焦点
                        .focusRing(catShape)
                        .clickable { selected = index }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline))

        if (selected == 0) {
            AppearanceContent(Modifier.weight(1f).fillMaxHeight())
        } else {
            CategoryPlaceholder(SettingsCategories[selected], Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** 分类 0（外观）：标题 + 4 行禁用设置行（分隔线行，非卡片）。 */
@Composable
private fun AppearanceContent(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp)) {
        // 设计稿内容标题与左栏选中项同词；标题仅视觉呈现、不进语义树
        // （当前分类已由左栏选中项播报，避免同一词重复）。
        Text(
            "外观",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(4.dp))
        // 行间不额外拉开距离：设计包 .setting-row 自带 padding 16/0 + 底边线
        SettingRow("主题", "跟随系统 / 浅色 / 深色", "跟随系统")
        SettingRow("界面字体", "Noto Sans SC", "选择")
        SettingRow("界面缩放", null, "100%")
        SettingRow("显示语言", null, "简体中文", showDivider = false)
    }
}

/** 其余分类：居中空态（图标 + 占位文案）。 */
@Composable
private fun CategoryPlaceholder(name: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "「$name」设置页占位。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 设置行：按设计包 `.setting-row` —— **分隔线行，不是卡片**：
 * `padding: var(--space-4) 0`（16/0）+ 底边 `1px var(--border-subtle)`（末行无）；
 * 无底色、无圆角、无整圈描边。左列 label + desc，右列禁用 OutlinedButton（无持久化后端）。
 */
@Composable
private fun SettingRow(
    label: String,
    desc: String?,
    buttonLabel: String,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (desc != null) {
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = {}, enabled = false) { Text(buttonLabel) }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
