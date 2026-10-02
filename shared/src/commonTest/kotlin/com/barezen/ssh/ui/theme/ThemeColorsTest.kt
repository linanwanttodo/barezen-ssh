// shared/src/commonTest/kotlin/com/barezen/ssh/ui/theme/ThemeColorsTest.kt
package com.barezen.ssh.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 断言值 = DBX tokens.css dark 段权威值（github.com/t8y2/dbx）+ 亮色板派生值。 */
class ThemeColorsTest {
    @Test fun bgMatchesDbxTokens() = assertEquals(Color(0xFF131416), BareZenBg)
    @Test fun surfaceMatchesDbxTokens() = assertEquals(Color(0xFF1B1B1E), BareZenSurface)
    @Test fun panelMatchesDbxTokens() = assertEquals(Color(0xFF19191C), BareZenPanel)
    @Test fun elevatedMatchesDbxTokens() = assertEquals(Color(0xFF232327), BareZenElevated)
    @Test fun borderMatchesDbxTokens() = assertEquals(Color(0x476E6E72), BareZenBorder)
    @Test fun borderSubtleMatchesDbxTokens() = assertEquals(Color(0x296E6E72), BareZenBorderSubtle)
    @Test fun textPrimaryMatchesDbxTokens() = assertEquals(Color(0xFFF2F2F4), BareZenTextPrimary)
    @Test fun textSecondaryMatchesDbxTokens() = assertEquals(Color(0xFF9E9EA6), BareZenTextSecondary)
    @Test fun textTertiaryMatchesDbxTokens() = assertEquals(Color(0xFF82828A), BareZenTextTertiary)
    @Test fun accentMatchesDbxTokens() = assertEquals(Color(0xFFD0D0D6), BareZenAccent)
    @Test fun onAccentMatchesDbxTokens() = assertEquals(Color(0xFF1B1B1E), BareZenOnAccent)
    @Test fun accentSubtleMatchesDbxTokens() = assertEquals(Color(0x1AFFFFFF), BareZenAccentSubtle)
    @Test fun errorMatchesDbxTokens() = assertEquals(Color(0xFFF3625F), BareZenError)
    @Test fun errorBgMatchesDbxTokens() = assertEquals(Color(0x26F3625F), BareZenErrorBg)
    @Test fun warningMatchesDbxTokens() = assertEquals(Color(0xFFFBBF24), BareZenWarning)
    @Test fun infoMatchesDbxTokens() = assertEquals(Color(0xFF60A5FA), BareZenInfo)
    @Test fun terminalBgMatchesDbxTokens() = assertEquals(Color(0xFF1E1E1E), BareZenTerminalBg)

    // 亮色板：从同一色相派生，语义色与暗色同值
    @Test fun lightBgDerivedFromSameHue() = assertEquals(Color(0xFFF5F5F7), BareZenLightBg)
    @Test fun lightSurfaceDerivedFromSameHue() = assertEquals(Color(0xFFFFFFFF), BareZenLightSurface)
    @Test fun lightPanelDerivedFromSameHue() = assertEquals(Color(0xFFF9F9FA), BareZenLightPanel)
    @Test fun lightElevatedDerivedFromSameHue() = assertEquals(Color(0xFFFFFFFF), BareZenLightElevated)
    @Test fun lightTextPrimaryDerivedFromSameHue() = assertEquals(Color(0xFF1B1B1E), BareZenLightTextPrimary)
    @Test fun lightTextSecondaryDerivedFromSameHue() = assertEquals(Color(0xFF6E6E76), BareZenLightTextSecondary)
    @Test fun lightTextTertiaryDerivedFromSameHue() = assertEquals(Color(0xFF67676F), BareZenLightTextTertiary)
    @Test fun lightAccentDerivedFromSameHue() = assertEquals(Color(0xFF3A3A40), BareZenLightAccent)
    @Test fun lightOnAccentDerivedFromSameHue() = assertEquals(Color(0xFFFFFFFF), BareZenLightOnAccent)
    @Test fun lightBorderKeepsDbxRgb() = assertEquals(Color(0x476E6E72), BareZenLightBorder)
    @Test fun lightErrorBgUsesHigherAlpha() = assertEquals(Color(0x29F3625F), BareZenLightErrorBg)

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

    @Test fun lightSchemeMapsDerivedTokens() {
        val c = BareZenLightColors
        assertEquals(BareZenLightAccent, c.primary)
        assertEquals(BareZenLightOnAccent, c.onPrimary)
        assertEquals(BareZenError, c.error)
        assertEquals(BareZenLightErrorBg, c.errorContainer)
        assertEquals(BareZenLightBg, c.background)
        assertEquals(BareZenLightSurface, c.surface)
        assertEquals(BareZenLightPanel, c.surfaceContainerLow)
        assertEquals(BareZenLightElevated, c.surfaceContainerHigh)
        assertEquals(BareZenLightTextSecondary, c.onSurfaceVariant)
        assertEquals(BareZenLightBorder, c.outline)
    }

    /** DBX 控件 4px / 容器 6px。 */
    @Test fun shapesMatchDbxRadius() {
        val density = Density(1f)
        val size = Size(100f, 100f)
        assertEquals(6f, (BareZenShapes.extraLarge as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(6f, (BareZenShapes.large as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(4f, (BareZenShapes.medium as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(4f, (BareZenShapes.small as RoundedCornerShape).topStart.toPx(size, density))
    }

    /** WCAG 证据自动化：正文组合全部 >=4.5:1。半透明语义背景先合成到 surface 再计算。 */
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
            Triple(BareZenError, composited(BareZenSurface, BareZenErrorBg), 4.5),
        )
        cases.forEach { (fg, bg, min) ->
            assertTrue(contrastRatio(fg, bg) >= min, "$fg on $bg = ${contrastRatio(fg, bg)} < $min")
        }
    }

    /** 亮色板 AA：文本三级与主/次在亮背景上的证据。 */
    @Test fun lightTextCombinationsPassWcagAA() {
        val cases = listOf(
            Triple(BareZenLightTextPrimary, BareZenLightBg, 4.5),
            Triple(BareZenLightTextSecondary, BareZenLightBg, 4.5),
            Triple(BareZenLightTextTertiary, BareZenLightBg, 4.5),
            Triple(BareZenLightTextTertiary, BareZenLightPanel, 4.5),
            Triple(BareZenLightAccent, BareZenLightBg, 4.5),
            Triple(BareZenLightOnAccent, BareZenLightAccent, 4.5),
        )
        cases.forEach { (fg, bg, min) ->
            assertTrue(contrastRatio(fg, bg) >= min, "$fg on $bg = ${contrastRatio(fg, bg)} < $min")
        }
    }

    /** 把半透明 tint 合成到不透明 base 上（sRGB 逐通道线性插值）。 */
    private fun composited(base: Color, tint: Color): Color {
        val a = tint.alpha
        return Color(
            red = tint.red * a + base.red * (1f - a),
            green = tint.green * a + base.green * (1f - a),
            blue = tint.blue * a + base.blue * (1f - a),
        )
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
