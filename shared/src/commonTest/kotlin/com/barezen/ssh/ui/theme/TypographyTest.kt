package com.barezen.ssh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 断言值 = docs/ui-redesign/apple.css 的字号阶。
 * 回退形态：CMP 1.12.1 的 `Font(FontResource,…)` 为 @Composable，字族与字号阶以
 * `@Composable get()` 暴露，故在组合内捕获后断言（brief 回退注记）。
 */
class TypographyTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun typeScaleMatchesDesignPack() = runComposeUiTest {
        var captured: Typography? = null
        var uiFamily: FontFamily? = null
        setContent {
            BareZenTheme {
                captured = MaterialTheme.typography
                uiFamily = BareZenUiFontFamily
            }
        }
        waitForIdle()
        val t = assertNotNull(captured)
        // apple.css 字号阶：设置标题 18/700，正文 13，辅助 12，标签 11
        assertEquals(20.sp, t.titleLarge.fontSize)
        assertEquals(26.sp, t.titleLarge.lineHeight)
        assertEquals(FontWeight.Bold, t.titleLarge.fontWeight)
        assertEquals(17.sp, t.titleMedium.fontSize)
        assertEquals(23.sp, t.titleMedium.lineHeight)
        assertEquals(FontWeight.SemiBold, t.titleMedium.fontWeight)
        assertEquals(15.sp, t.titleSmall.fontSize)
        assertEquals(20.sp, t.titleSmall.lineHeight)
        assertEquals(FontWeight.SemiBold, t.titleSmall.fontWeight)
        assertEquals(13.sp, t.bodyLarge.fontSize)
        assertEquals(20.sp, t.bodyLarge.lineHeight)
        assertEquals(13.sp, t.bodyMedium.fontSize)
        assertEquals(20.sp, t.bodyMedium.lineHeight)
        assertEquals(12.sp, t.bodySmall.fontSize)
        assertEquals(13.sp, t.labelLarge.fontSize)
        assertEquals(12.sp, t.labelMedium.fontSize)
        assertEquals(11.sp, t.labelSmall.fontSize)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)
        assertEquals(uiFamily, t.titleLarge.fontFamily)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun monoRolesUseJetBrainsMono() = runComposeUiTest {
        var body: TextStyle? = null
        var small: TextStyle? = null
        var monoFamily: FontFamily? = null
        setContent {
            BareZenTheme {
                body = BareZenMonoBody
                small = BareZenMonoSmall
                monoFamily = BareZenMonoFontFamily
            }
        }
        waitForIdle()
        val b = assertNotNull(body)
        assertEquals(13.sp, b.fontSize)
        assertEquals(20.sp, b.lineHeight)
        assertEquals(monoFamily, b.fontFamily)
        val s = assertNotNull(small)
        assertEquals(11.sp, s.fontSize)
        assertEquals(monoFamily, s.fontFamily)
    }
}
