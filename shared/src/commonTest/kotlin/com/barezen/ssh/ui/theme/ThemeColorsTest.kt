// shared/src/commonTest/kotlin/com/barezen/ssh/ui/theme/ThemeColorsTest.kt
package com.barezen.ssh.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 断言值 = docs/ui-redesign/index.html §1 色板 + styles.css --tokens 原文。 */
class ThemeColorsTest {
    @Test fun bgMatchesDesignPack() = assertEquals(Color(0xFF0B0D0E), BareZenBg)
    @Test fun surfaceMatchesDesignPack() = assertEquals(Color(0xFF131516), BareZenSurface)
    @Test fun panelMatchesDesignPack() = assertEquals(Color(0xFF1A1D1E), BareZenPanel)
    @Test fun elevatedMatchesDesignPack() = assertEquals(Color(0xFF232728), BareZenElevated)
    @Test fun borderMatchesDesignPack() = assertEquals(Color(0xFF2E3335), BareZenBorder)
    @Test fun borderSubtleMatchesDesignPack() = assertEquals(Color(0xFF24292B), BareZenBorderSubtle)
    @Test fun textPrimaryMatchesDesignPack() = assertEquals(Color(0xFFEEF1F3), BareZenTextPrimary)
    @Test fun textSecondaryMatchesDesignPack() = assertEquals(Color(0xFF9CA5A9), BareZenTextSecondary)
    @Test fun textTertiaryMatchesDesignPack() = assertEquals(Color(0xFF8B9498), BareZenTextTertiary)
    @Test fun accentMatchesDesignPack() = assertEquals(Color(0xFF52B788), BareZenAccent)
    @Test fun onAccentMatchesDesignPack() = assertEquals(Color(0xFF101413), BareZenOnAccent)
    @Test fun accentSubtleMatchesDesignPack() = assertEquals(Color(0xFF1A2E26), BareZenAccentSubtle)
    @Test fun errorMatchesDesignPack() = assertEquals(Color(0xFFE56A6A), BareZenError)
    @Test fun errorBgMatchesDesignPack() = assertEquals(Color(0xFF2A1A1A), BareZenErrorBg)
    @Test fun warningMatchesDesignPack() = assertEquals(Color(0xFFE5A044), BareZenWarning)
    @Test fun infoMatchesDesignPack() = assertEquals(Color(0xFF5CA8D8), BareZenInfo)
    @Test fun terminalBgMatchesDesignPack() = assertEquals(Color(0xFF1E1E1E), BareZenTerminalBg)

    @Test fun schemeMapsDesignTokens() {
        val c = BareZenDarkColors
        assertEquals(BareZenAccent, c.primary)
        assertEquals(BareZenOnAccent, c.onPrimary)
        assertEquals(BareZenAccentSubtle, c.secondaryContainer)
        assertEquals(BareZenAccent, c.onSecondaryContainer)
        assertEquals(BareZenError, c.error)
        assertEquals(BareZenErrorBg, c.errorContainer)
        assertEquals(BareZenBg, c.background)
        assertEquals(BareZenSurface, c.surface)
        assertEquals(BareZenPanel, c.surfaceContainerLow)
        assertEquals(BareZenElevated, c.surfaceContainerHigh)
        assertEquals(BareZenTextSecondary, c.onSurfaceVariant)
        assertEquals(BareZenBorder, c.outline)
        assertEquals(BareZenBorderSubtle, c.outlineVariant)
    }

    /** 设计包 §contrast 的证据自动化：正文组合全部 ≥4.5:1。 */
    @Test fun textCombinationsPassWcagAA() {
        val cases = listOf(
            Triple(BareZenTextPrimary, BareZenBg, 4.5),
            Triple(BareZenTextSecondary, BareZenBg, 4.5),
            Triple(BareZenTextTertiary, BareZenBg, 4.5),
            Triple(BareZenAccent, BareZenBg, 4.5),
            Triple(BareZenError, BareZenBg, 4.5),
            Triple(BareZenWarning, BareZenBg, 4.5),
            Triple(BareZenInfo, BareZenBg, 4.5),
            Triple(BareZenTextSecondary, BareZenPanel, 4.5),
            Triple(BareZenTextTertiary, BareZenPanel, 4.5),
            Triple(BareZenAccent, BareZenPanel, 4.5),
            Triple(BareZenOnAccent, BareZenAccent, 4.5),
            Triple(BareZenError, BareZenErrorBg, 4.5),
        )
        cases.forEach { (fg, bg, min) ->
            assertTrue(contrastRatio(fg, bg) >= min, "$fg on $bg = ${contrastRatio(fg, bg)} < $min")
        }
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun ch(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(color.red) + 0.7152 * ch(color.green) + 0.0722 * ch(color.blue)
    }
}
