// shared/src/jvmMain/kotlin/com/barezen/ssh/terminal/TerminalPalette.kt
package com.barezen.ssh.terminal

import com.jediterm.core.Color
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.emulator.ColorPalette

/**
 * 终端 16 色（docs/ui-redesign/index.html §2）：独立于 UI 主题，24-bit RGB。
 * 亮色 9–14 与 1–6 同值（设计包仅列 8 色且声明 16 色均达 AA）；亮黑 8 = 暗淡 Dim（#4C4C4C 默认不达 AA，换此实算 4.83:1）；
 * 黑 0 / 白 7 / 亮白 15 沿用 JediTerm 默认。
 */
object TerminalPalette {
    const val Foreground = 0xCCCCCC
    const val Dim = 0x8A8A8A
    const val Red = 0xE45C5C
    const val Green = 0x0DBC79
    const val Yellow = 0xE5E510
    const val Blue = 0x4AA8FF
    const val Magenta = 0xD670D6
    const val Cyan = 0x33B8D9
    const val Background = 0x1E1E1E

    internal fun rgb(hex: Int): TerminalColor =
        TerminalColor.rgb((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF)

    /** ANSI 色号 → 设计包 24-bit 色；null = 沿用 JediTerm 默认（黑 0 / 白 7 / 亮白 15）。 */
    internal fun ansi(index: Int): Int? = when (index) {
        1, 9 -> Red
        2, 10 -> Green
        3, 11 -> Yellow
        4, 12 -> Blue
        5, 13 -> Magenta
        6, 14 -> Cyan
        8 -> Dim // 亮黑默认 #4C4C4C 在 #1E1E1E 上仅 1.94:1 不达 AA；Dim #8A8A8A 实算 4.83:1
        else -> null
    }
}

/**
 * 终端 16 色经 JediTerm SettingsProvider 独立注入。
 *
 * JediTerm 3.73 无 `getANSIColor`（简报预置回退）：16 色的注入点是
 * `UserSettingsProvider.getTerminalColorPalette()`——`TerminalPanel` 渲染时经
 * `ColorPalette.getForeground/getBackground` 解析 indexed 色（JediEmulator 的 30–37/40–47/90–97/100–107
 * 落到 `TerminalColor.index(0..15)`）并取此实现；256 色与真彩色不走此路径，原样透传。
 * [fallback] 承接未覆盖索引（0/7/15），即 JediTerm 默认 XTERM/WINDOWS 调色板。
 */
internal class BareZenColorPalette(private val fallback: ColorPalette) : ColorPalette() {
    override fun getForegroundByColorIndex(colorIndex: Int): Color =
        design(colorIndex) ?: fallback.getForeground(TerminalColor.index(colorIndex))

    override fun getBackgroundByColorIndex(colorIndex: Int): Color =
        design(colorIndex) ?: fallback.getBackground(TerminalColor.index(colorIndex))

    private fun design(index: Int): Color? = TerminalPalette.ansi(index)?.let { Color(it) }
}
