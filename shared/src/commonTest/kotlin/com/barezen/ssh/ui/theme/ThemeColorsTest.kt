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

/**
 * 断言值 = docs/ui-redesign/apple.css 的 token（2026-10-03 全盘采用）。
 * 与 apple.css 字面值不同的四组令牌在 Color.kt 文件头有逐条对比度说明；
 * 本测试同时锁住**改动后的值**与**它们必须达 AA**这一事实。
 */
class ThemeColorsTest {
    // ---- 暗色板 ----
    @Test fun bgMatchesAppleToken() = assertEquals(Color(0xFF1C1C1E), BareZenBg)
    @Test fun surfaceMatchesAppleToken() = assertEquals(Color(0xFF141416), BareZenSurface)
    @Test fun surface2MatchesAppleToken() = assertEquals(Color(0xFF0E0E10), BareZenSurface2)
    @Test fun panelMatchesAppleToken() = assertEquals(Color(0xFF242426), BareZenPanel)
    @Test fun elevatedMatchesAppleToken() = assertEquals(Color(0xFF2C2C2E), BareZenElevated)
    @Test fun borderMatchesAppleToken() = assertEquals(Color(0xFF38383A), BareZenBorder)
    @Test fun borderStrongMatchesAppleToken() = assertEquals(Color(0xFF48484A), BareZenBorderStrong)
    @Test fun borderSubtleMatchesAppleToken() = assertEquals(Color(0xFF2A2A2C), BareZenBorderSubtle)
    @Test fun textPrimaryMatchesAppleToken() = assertEquals(Color(0xFFF5F5F7), BareZenTextPrimary)
    @Test fun textSecondaryMatchesAppleToken() = assertEquals(Color(0xFF98989D), BareZenTextSecondary)

    /**
     * textTertiary 是**为 AA 调整过的值**（apple.css 的 #6E6E73 在 bg 上仅 3.36:1）。
     * 把它调回更暗的灰会先在这条测试上看到。
     */
    @Test fun textTertiaryLiftedForWcagAa() = assertEquals(Color(0xFF94949A), BareZenTextTertiary)

    @Test fun accentMatchesAppleToken() = assertEquals(Color(0xFF0A84FF), BareZenAccent)
    /** error 族为 AA 整体提亮（同色相）：裸 error 在 elevated 上必须达 4.5。 */
    @Test fun errorLiftedForWcagAaOnLightestSurface() = assertEquals(Color(0xFFFF7B74), BareZenError)
    @Test fun warningMatchesAppleToken() = assertEquals(Color(0xFFFF9F0A), BareZenWarning)
    @Test fun successMatchesAppleToken() = assertEquals(Color(0xFF30D158), BareZenSuccess)
    @Test fun terminalBgMatchesAppleToken() = assertEquals(Color(0xFF0E0E10), BareZenTerminalBg)

    /** accent 填充上的文字是**近黑而非白**：白字压 #0A84FF 只有 3.65:1，AA 不达标。 */
    @Test fun onAccentIsNearBlackForWcagAa() = assertEquals(Color(0xFF0A0A0C), BareZenOnAccent)

    /** errorContainer 上的文字比 error 再亮一档：压 14% tint 必须达 4.5（含最亮的 elevated 底）。 */
    @Test fun onErrorContainerLiftedForWcagAa() =
        assertEquals(Color(0xFFFF9A93), BareZenOnErrorContainer)

    // ---- 亮色板 ----
    @Test fun lightBgMatchesAppleHierarchy() = assertEquals(Color(0xFFF2F2F4), BareZenLightBg)
    @Test fun lightSurfaceIsWhite() = assertEquals(Color(0xFFFFFFFF), BareZenLightSurface)
    @Test fun lightElevatedMatchesAppleHierarchy() = assertEquals(Color(0xFFE8E8ED), BareZenLightElevated)
    @Test fun lightTextPrimaryMatchesAppleToken() = assertEquals(Color(0xFF1C1C1E), BareZenLightTextPrimary)
    @Test fun lightTextTertiaryMeetsAaOnElevated() = assertEquals(Color(0xFF67676E), BareZenLightTextTertiary)
    @Test fun lightAccentMeetsAaAsTextAndFill() = assertEquals(Color(0xFF005FCC), BareZenLightAccent)
    @Test fun lightErrorMeetsAaOnTint() = assertEquals(Color(0xFFBB3330), BareZenLightError)

    // ---- M3 槽位映射 ----
    @Test fun schemeMapsDesignTokens() {
        val c = BareZenDarkColors
        assertEquals(BareZenAccent, c.primary)
        assertEquals(BareZenOnAccent, c.onPrimary)
        assertEquals(BareZenAccentSubtle, c.primaryContainer)
        assertEquals(BareZenAccentOnSubtle, c.onPrimaryContainer)
        assertEquals(BareZenError, c.error)
        assertEquals(BareZenErrorBg, c.errorContainer)
        assertEquals(BareZenOnErrorContainer, c.onErrorContainer)
        assertEquals(BareZenBg, c.background)
        assertEquals(BareZenSurface, c.surface)
        assertEquals(BareZenPanel, c.surfaceContainer)
        assertEquals(BareZenElevated, c.surfaceContainerHighest)
        assertEquals(BareZenTextSecondary, c.onSurfaceVariant)
        assertEquals(BareZenBorder, c.outline)
        assertEquals(BareZenBorderSubtle, c.outlineVariant)
    }

    @Test fun lightSchemeMapsDerivedTokens() {
        val c = BareZenLightColors
        assertEquals(BareZenLightAccent, c.primary)
        assertEquals(BareZenLightOnAccent, c.onPrimary)
        assertEquals(BareZenLightError, c.error)
        assertEquals(BareZenLightErrorBg, c.errorContainer)
        assertEquals(BareZenLightOnErrorDeep, c.onErrorContainer)
        assertEquals(BareZenLightBg, c.background)
        assertEquals(BareZenLightSurface, c.surface)
        assertEquals(BareZenLightTextSecondary, c.onSurfaceVariant)
    }

    /** apple.css 圆角：控件 8 / 容器 10 / 大容器 12 / 紧凑 6。 */
    @Test fun shapesMatchAppleRadius() {
        val density = Density(1f)
        val size = Size(100f, 100f)
        assertEquals(12f, (BareZenShapes.extraLarge as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(10f, (BareZenShapes.large as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(8f, (BareZenShapes.medium as RoundedCornerShape).topStart.toPx(size, density))
        assertEquals(6f, (BareZenShapes.small as RoundedCornerShape).topStart.toPx(size, density))
    }

    /**
     * WCAG 证据（暗色）：**所有文字/底色组合** >= 4.5:1。
     * 背景集合取本主题实际出现的不透明底色——bg / surface / surface-2 / panel / elevated，
     * 前景取四级文字 + 语义色。新配色漏掉任一组合都会在这里转红。
     */
    @Test fun darkTextPassesWcagAaOnEverySurface() {
        val backgrounds = listOf(BareZenBg, BareZenSurface, BareZenSurface2, BareZenPanel, BareZenElevated)
        // 注意：这里**不含** BareZenAccent。accent 是填充/图标色，当文字用会在 panel 上
        // 只有 4.25:1（见文件头「结构性约束」）；文字角色由 BareZenAccentOnSubtle 承担。
        val foregrounds = listOf(
            BareZenTextPrimary, BareZenTextSecondary, BareZenTextTertiary,
            BareZenError, BareZenWarning, BareZenSuccess, BareZenAccentOnSubtle,
        )
        for (bg in backgrounds) {
            for (fg in foregrounds) {
                val ratio = contrastRatio(fg, bg)
                assertTrue(ratio >= 4.5, "$fg on $bg = $ratio < 4.5")
            }
        }
    }

    /** 文字压在半透明 tint 上：先把 tint 合成到实际底色再算。 */
    @Test fun darkTextPassesWcagAaOnTintedBackgrounds() {
        val cases = listOf(
            // errorContainer 里的文字在**每一个**可能承载它的底色上都要达 AA
            Triple(
                BareZenOnErrorContainer,
                composited(BareZenBg, BareZenErrorBg),
                "onErrorContainer on errorBg over bg",
            ),
            Triple(
                BareZenOnErrorContainer,
                composited(BareZenPanel, BareZenErrorBg),
                "onErrorContainer on errorBg over panel",
            ),
            Triple(
                BareZenOnErrorContainer,
                composited(BareZenElevated, BareZenErrorBg),
                "onErrorContainer on errorBg over elevated",
            ),
            Triple(BareZenOnAccent, BareZenAccent, "onPrimary on accent fill"),
            Triple(
                BareZenAccentOnSubtle,
                composited(BareZenBg, BareZenAccentSubtle),
                "accentOnSubtle on primaryContainer",
            ),
            Triple(
                BareZenAccentOnSubtle,
                composited(BareZenPanel, BareZenAccentSubtle),
                "accentOnSubtle on panel tint",
            ),
        )
        for ((fg, bg, label) in cases) {
            val ratio = contrastRatio(fg, bg)
            assertTrue(ratio >= 4.5, "$label = $ratio < 4.5")
        }
    }

    /** 亮色板 AA：四级文字 + 语义色在白 / bg / elevated 三个底上。 */
    @Test fun lightTextPassesWcagAaOnEverySurface() {
        val backgrounds = listOf(BareZenLightBg, BareZenLightSurface, BareZenLightElevated)
        val foregrounds = listOf(
            BareZenLightTextPrimary, BareZenLightTextSecondary, BareZenLightTextTertiary,
            BareZenLightAccent, BareZenLightError, BareZenLightWarning, BareZenLightSuccess,
        )
        for (bg in backgrounds) {
            for (fg in foregrounds) {
                val ratio = contrastRatio(fg, bg)
                assertTrue(ratio >= 4.5, "$fg on $bg = $ratio < 4.5")
            }
        }
        assertTrue(
            contrastRatio(
                BareZenLightOnErrorDeep,
                composited(BareZenLightSurface, BareZenLightErrorBg),
            ) >= 4.5,
            "onErrorContainer on light errorBg",
        )
        assertTrue(
            contrastRatio(BareZenLightOnAccent, BareZenLightAccent) >= 4.5,
            "onPrimary on light accent fill",
        )
    }

    /**
     * 仪表盘数据系列：作为 3dp 色条 / 8dp 图例点等**非文本 UI 构件**，
     * 适用 3:1 门槛（WCAG 1.4.11）。四色在 panel 底上均达标。
     */
    @Test fun metricAccentsPassNonTextContrast() {
        val series = listOf(
            BareZenMetricCpu, BareZenMetricMem, BareZenMetricLoad, BareZenMetricUptime,
        )
        for (c in series) {
            val onPanel = contrastRatio(c, BareZenPanel)
            assertTrue(onPanel >= 3.0, "metric $c on panel = $onPanel < 3.0")
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
