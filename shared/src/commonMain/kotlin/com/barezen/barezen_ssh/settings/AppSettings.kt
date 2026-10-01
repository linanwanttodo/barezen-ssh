// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.Serializable

/**
 * 主题。
 *
 * **故意不含 LIGHT** —— 浅色板尚未实现，持久化层不得持有一个不可能的值。
 * UI 上「浅色」渲染为禁用项，等浅色板落地时再加枚举分支。
 */
@Serializable
enum class Theme { DARK, FOLLOW_SYSTEM }

@Serializable
enum class UpdateChannel { STABLE, PREVIEW }

@Serializable
enum class ConflictPolicy { ASK, OVERWRITE, SKIP, RENAME }

/** 收敛结果：收敛后的设置 + 人类可读的警告（用于 UI 如实告知，不静默）。 */
data class Sanitized(val value: AppSettings, val warnings: List<String>)

/**
 * 全局设置。**每个字段都必须有默认值** —— 这既是「旧文件缺字段」的兼容手段，
 * 也是 `coerceInputValues` 能把未知枚举值收敛掉的前提。
 */
@Serializable
data class AppSettings(
    val schemaVersion: Int = SCHEMA_VERSION,
    // 外观
    val theme: Theme = Theme.FOLLOW_SYSTEM,
    val uiFont: String = UI_FONT_DEFAULT,
    val uiScale: Float = UI_SCALE_DEFAULT,
    val language: String = LANGUAGE_DEFAULT,
    // 终端
    val terminalFont: String = TERMINAL_FONT_DEFAULT,
    val terminalPalette: String = TERMINAL_PALETTE_DEFAULT,
    val copyOnSelect: Boolean = false,
    /**
     * Shift+Insert 粘贴。**数据字段保留**（避免 schema 变动），但 UI 不渲染该行。
     * 依据：已用 `javap`/`grep` 核实 JediTerm 3.73 的 `TerminalPanel` 无任何 INSERT 键绑定、
     * `emulateX11CopyPaste()` 是死方法、`pasteFromClipboard` 为 private 且无 ActionMap 入口 ——
     * 底层做不到，故按设计 §9.4 / R7「做不到就删行，绝不渲染无效开关」删除整行。
     * 待 JediTerm 支持或自研 Swing 键绑定后，再启用此字段并恢复该行。
     */
    val shiftInsertPaste: Boolean = true,
    val sudoAutofill: Boolean = false,
    // 连接
    val autoConnectServerId: String? = null,
    val hideAddresses: Boolean = false,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    // 更新
    val updateRepo: String? = null,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val autoCheckUpdates: Boolean = false,
    // 关于
    val feedbackUrl: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val UI_FONT_DEFAULT = "Noto Sans SC"
        const val LANGUAGE_DEFAULT = "zh-CN"
        const val TERMINAL_FONT_DEFAULT = "JetBrains Mono"
        const val TERMINAL_PALETTE_DEFAULT = "graphite"
        const val UI_SCALE_DEFAULT = 1.0f

        /** 界面缩放合法档；越界值收敛到最近的一档 */
        val UI_SCALE_CHOICES = listOf(1.0f, 1.25f, 1.5f)

        /**
         * 当前**真实存在**的可选值。只有一个元素不是偷懒 ——
         * 本项目只随包分发一款 UI 字体/一款等宽字体/一套终端调色板/一种语言，
         * 照实渲染单值，不造假备选（不造数红线）。
         */
        val UI_FONT_CHOICES = listOf(UI_FONT_DEFAULT)
        val LANGUAGE_CHOICES = listOf(LANGUAGE_DEFAULT)
        val TERMINAL_FONT_CHOICES = listOf(TERMINAL_FONT_DEFAULT)
        val TERMINAL_PALETTE_CHOICES = listOf(TERMINAL_PALETTE_DEFAULT)

        val Default = AppSettings()

        private val REPO_RE = Regex("""^[\w.-]+/[\w.-]+$""")
        internal fun isValidRepo(v: String) = REPO_RE.matches(v)
        internal fun isValidUrl(v: String) = v.startsWith("http://") || v.startsWith("https://")
    }
}

/**
 * 把越界/未知值收敛到合法域。
 *
 * **绝不抛异常、绝不产生坏布局** —— 用户手改坏了 settings.json 也不该让应用崩掉。
 * 每次收敛都留下一条警告，由 UI 如实告知。
 */
fun AppSettings.sanitized(): Sanitized {
    val warnings = mutableListOf<String>()
    var s = this

    if (s.uiScale !in AppSettings.UI_SCALE_CHOICES) {
        // 收敛到最近的合法档（不四舍五入到"更小的"，就近即可）
        val nearest = AppSettings.UI_SCALE_CHOICES.minByOrNull { kotlin.math.abs(it - s.uiScale) }
            ?: AppSettings.UI_SCALE_DEFAULT
        warnings += "界面缩放 ${s.uiScale} 无效，已改为 ${(nearest * 100).toInt()}%"
        s = s.copy(uiScale = nearest)
    }
    if (s.uiFont !in AppSettings.UI_FONT_CHOICES) {
        warnings += "界面字体「${s.uiFont}」不可用，已改为 ${AppSettings.UI_FONT_DEFAULT}"
        s = s.copy(uiFont = AppSettings.UI_FONT_DEFAULT)
    }
    if (s.language !in AppSettings.LANGUAGE_CHOICES) {
        warnings += "显示语言「${s.language}」不可用，已改为 ${AppSettings.LANGUAGE_DEFAULT}"
        s = s.copy(language = AppSettings.LANGUAGE_DEFAULT)
    }
    if (s.terminalFont !in AppSettings.TERMINAL_FONT_CHOICES) {
        warnings += "终端字体「${s.terminalFont}」不可用，已改为 ${AppSettings.TERMINAL_FONT_DEFAULT}"
        s = s.copy(terminalFont = AppSettings.TERMINAL_FONT_DEFAULT)
    }
    if (s.terminalPalette !in AppSettings.TERMINAL_PALETTE_CHOICES) {
        warnings += "终端主题「${s.terminalPalette}」不可用，已改为 ${AppSettings.TERMINAL_PALETTE_DEFAULT}"
        s = s.copy(terminalPalette = AppSettings.TERMINAL_PALETTE_DEFAULT)
    }
    s.updateRepo?.let { repo ->
        if (!AppSettings.isValidRepo(repo)) {
            warnings += "更新源「$repo」格式无效，应为 owner/repo，已清空"
            s = s.copy(updateRepo = null)
        }
    }
    s.feedbackUrl?.let { url ->
        if (!AppSettings.isValidUrl(url)) {
            warnings += "反馈入口「$url」不是 http(s) 链接，已清空"
            s = s.copy(feedbackUrl = null)
        }
    }
    if (s.autoConnectServerId?.isBlank() == true) {
        s = s.copy(autoConnectServerId = null)
    }
    return Sanitized(s, warnings)
}
