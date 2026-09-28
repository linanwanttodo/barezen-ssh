package com.barezen.barezen_ssh.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.emulator.ColorPaletteImpl
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalPaletteTest {
    @Test fun paletteMatchesDesignPack() {
        assertEquals(0xCCCCCC, TerminalPalette.Foreground)
        assertEquals(0x8A8A8A, TerminalPalette.Dim)
        assertEquals(0xE45C5C, TerminalPalette.Red)
        assertEquals(0x0DBC79, TerminalPalette.Green)
        assertEquals(0xE5E510, TerminalPalette.Yellow)
        assertEquals(0x4AA8FF, TerminalPalette.Blue)
        assertEquals(0xD670D6, TerminalPalette.Magenta)
        assertEquals(0x33B8D9, TerminalPalette.Cyan)
        assertEquals(0x1E1E1E, TerminalPalette.Background)
    }

    @Test fun allTextColorsPassAAOnTerminalBg() {
        listOf(
            TerminalPalette.Foreground, TerminalPalette.Dim, TerminalPalette.Red,
            TerminalPalette.Green, TerminalPalette.Yellow, TerminalPalette.Blue,
            TerminalPalette.Magenta, TerminalPalette.Cyan,
        ).forEach { fg ->
            assertTrue(contrast(TerminalPalette.Background, fg) >= 4.5, "#${fg.toString(16)} 未达 AA")
        }
    }

    @Test fun ansi8WiresDimAtAAPassed() {
        val palette = BareZenColorPalette(ColorPaletteImpl.XTERM_PALETTE)
        val idx8 = palette.getForeground(TerminalColor.index(8)).getRGB() and 0xFFFFFF
        assertEquals(TerminalPalette.Dim, idx8)
        // 4.83 = WCAG 对比度实算值 round(contrast(#1E1E1E, #8A8A8A) × 100) / 100，非造数
        val measured = round(contrast(TerminalPalette.Background, TerminalPalette.Dim) * 100) / 100
        assertEquals(4.83, measured)
    }

    private fun contrast(bgHex: Int, fgHex: Int): Double {
        fun lum(hex: Int): Double {
            fun ch(v: Int): Double {
                val c = v / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * ch((hex shr 16) and 0xFF) + 0.7152 * ch((hex shr 8) and 0xFF) + 0.0722 * ch(hex and 0xFF)
        }
        val (hi, lo) = listOf(lum(bgHex), lum(fgHex)).sorted().reversed()
        return (hi + 0.05) / (lo + 0.05)
    }
}
