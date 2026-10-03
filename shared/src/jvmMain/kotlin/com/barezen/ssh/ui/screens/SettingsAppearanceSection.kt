// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsAppearanceSection.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.settings.AppSettings
import com.barezen.ssh.settings.Theme
import com.barezen.ssh.ui.components.BzSlider
import com.barezen.ssh.ui.theme.BareZenSpace

/**
 * 设置·外观（apple.html `#settings-appearance`）：
 * 卡一 主题分段 + 强调色色样；卡二 界面字体 / 界面缩放滑杆 / 显示语言。
 *
 * 主题选项顺序与原型一致（浅色 / 深色 / 自动），其中「自动」= 跟随系统。
 * 强调色只展示**当前主题真实在用**的 accent 单值——本项目 accent 只有一处真源
 * （Color.kt），做成多点可选就是假控件，故不渲染可点色盘（CONVENTIONS §1.7）。
 */
@Composable
fun AppearanceSettingsSection(settings: SettingsModel) {
    val s = settings.settings
    val colors = MaterialTheme.colorScheme

    SettingsSectionScaffold("外观") {
        SettingCard {
            ChoiceRow(
                title = "主题",
                desc = "应用整体颜色模式；自动 = 跟随系统",
                options = listOf("浅色", "深色", "自动"),
                selectedIndex = when (s.theme) {
                    Theme.LIGHT -> 0
                    Theme.DARK -> 1
                    Theme.FOLLOW_SYSTEM -> 2
                },
                onSelect = { i ->
                    settings.update {
                        it.copy(
                            theme = when (i) {
                                0 -> Theme.LIGHT
                                1 -> Theme.DARK
                                else -> Theme.FOLLOW_SYSTEM
                            }
                        )
                    }
                },
            )
            // 强调色：只读展示（唯一真源在 Color.kt / 亮暗两板各一值）
            Row(
                Modifier.fillMaxWidth().padding(vertical = BareZenSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("强调色", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "当前主题在用的强调色，用于主按钮与选中态",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(BareZenSpace.md))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(colors.primary))
                }
                Spacer(Modifier.width(2.dp))
                Text("系统蓝", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }

        SettingCard {
            StaticValueRow(
                title = "界面字体",
                desc = "系统界面字体",
                value = AppSettings.UI_FONT_DEFAULT,
            )
            // 缩放走滑杆（apple.css `.range-with-value`），档位仍是三个真实值
            val scaleIndex = AppSettings.UI_SCALE_CHOICES.indexOf(s.uiScale).coerceAtLeast(0)
            SliderRow(
                title = "界面缩放",
                desc = null,
                value = scaleIndex.toFloat(),
                valueMax = (AppSettings.UI_SCALE_CHOICES.size - 1).toFloat(),
                valueLabel = "${(s.uiScale * 100).toInt()}%",
                onValueChange = { idx ->
                    val i = idx.toInt().coerceIn(AppSettings.UI_SCALE_CHOICES.indices)
                    settings.update { it.copy(uiScale = AppSettings.UI_SCALE_CHOICES[i]) }
                },
                testTag = "ui-scale-slider",
            )
            StaticValueRow(
                title = "显示语言",
                desc = "当前仅提供简体中文",
                value = "简体中文",
            )
        }
    }
}

/**
 * 滑杆行（apple.css `.range-with-value`）：标题/说明在左，滑杆 + 等宽数值在右。
 * 离散档位以**档位索引**驱动，值标签显示真实值（100%/125%/150%）。
 */
@Composable
internal fun SliderRow(
    title: String,
    desc: String?,
    value: Float,
    valueMax: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    testTag: String? = null,
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
            BzSlider(
                value = value,
                onValueChange = onValueChange,
                valueLabel = valueLabel,
                valueRange = 0f..valueMax,
                testTag = testTag,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
