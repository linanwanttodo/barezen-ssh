// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsTerminalSection.kt
package com.barezen.ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.settings.AppSettings

/**
 * 终端分类（设计 §8.2 / §9.4）。
 *
 * 三选项处置：
 * - 选中即复制：JediTerm 3.73 提供 `copyOnSelect()`，由 [com.barezen.ssh.terminal.TerminalView] 接进 `BareZenTerminalSettings`。
 * - Shift+Insert 粘贴：**不渲染**。已用 `javap`/`grep` 核实 JediTerm 3.73 的 `TerminalPanel` 无任何 INSERT 键绑定、
 *   `emulateX11CopyPaste()` 是死方法、`pasteFromClipboard` 为 private 且无 ActionMap 入口 —— 底层做不到，
 *   故按设计 §9.4 / R7「做不到就删行，绝不渲染无效开关」删除整行（`shiftInsertPaste` 数据字段保留，留待其支持后启用）。
 * - 自动填入 sudo 密码：仅存不生效（需 P3 凭据库提供密码来源），desc 如实标注「待凭据库接入后生效」。
 */
@Composable
fun TerminalSettingsSection(settings: SettingsModel) {
    val s = settings.settings

    SettingsSectionScaffold("终端") {
        StaticValueRow(
            title = "终端字体",
            desc = "随包分发，暂无可选项",
            value = AppSettings.TERMINAL_FONT_DEFAULT,
        )
        StaticValueRow(
            title = "终端主题",
            desc = "当前仅一套终端调色板",
            value = "石墨（graphite）",
        )

        ToggleRow(
            title = "选中即复制",
            desc = "开启后，在终端里选中文本即写入剪贴板",
            checked = s.copyOnSelect,
            onCheckedChange = { v -> settings.update { it.copy(copyOnSelect = v) } },
        )

        ToggleRow(
            title = "自动填入 sudo 密码",
            desc = "（待凭据库接入后生效）",
            checked = s.sudoAutofill,
            onCheckedChange = { v -> settings.update { it.copy(sudoAutofill = v) } },
        )
    }
}
