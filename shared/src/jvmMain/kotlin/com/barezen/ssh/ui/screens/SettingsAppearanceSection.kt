// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsAppearanceSection.kt
package com.barezen.ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.settings.AppSettings
import com.barezen.ssh.settings.Theme

@Composable
fun AppearanceSettingsSection(settings: SettingsModel) {
    val s = settings.settings

    SettingsSectionScaffold("外观") {
        // 主题：三选项，「浅色」禁用 —— 浅色板尚未实现，不给假选项（不造数红线）
        val themeLabels = listOf("跟随系统", "深色", "浅色（未实现）")
        val themeIndex = when (s.theme) {
            Theme.FOLLOW_SYSTEM -> 0
            Theme.DARK -> 1
        }
        ChoiceRow(
            title = "主题",
            desc = "跟随系统在浅色主题实现前等同于深色",
            options = themeLabels,
            selectedIndex = themeIndex,
            enabled = true,
            disabledIndices = setOf(2),
            onSelect = { i ->
                // i == 2（浅色）为禁用项，正常点不到；这里也防御性忽略
                when (i) {
                    0 -> settings.update { it.copy(theme = Theme.FOLLOW_SYSTEM) }
                    1 -> settings.update { it.copy(theme = Theme.DARK) }
                }
            },
        )

        StaticValueRow(
            title = "界面字体",
            desc = "随包分发，暂无可选项",
            value = AppSettings.UI_FONT_DEFAULT,
        )

        ChoiceRow(
            title = "界面缩放",
            desc = null,
            options = listOf("100%", "125%", "150%"),
            selectedIndex = AppSettings.UI_SCALE_CHOICES.indexOf(s.uiScale).coerceAtLeast(0),
            onSelect = { i -> settings.update { it.copy(uiScale = AppSettings.UI_SCALE_CHOICES[i]) } },
        )

        StaticValueRow(
            title = "显示语言",
            desc = "当前仅提供简体中文",
            value = "简体中文",
        )
    }
}
