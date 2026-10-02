package com.barezen.ssh.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ui.theme.BareZenLightAccent
import com.barezen.ssh.ui.theme.BareZenLightBg
import com.barezen.ssh.ui.theme.BareZenTheme
import com.barezen.ssh.ui.theme.BareZenUiFontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeWiringTest {
    /**
     * 裸 Text 的字体来源：BareZenTheme 必须把 LocalTextStyle 指到 Noto 字族。
     * 回退形态下 `BareZenUiFontFamily` 为 @Composable getter，故在组合内一并捕获后断言。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun themeProvidesUiFontAsLocalTextStyle() = runComposeUiTest {
        var captured = androidx.compose.ui.text.TextStyle()
        var family: FontFamily? = null
        setContent {
            BareZenTheme {
                captured = LocalTextStyle.current
                family = BareZenUiFontFamily
            }
        }
        waitForIdle()
        assertEquals(family, captured.fontFamily)
    }

    /** 浅色板已生效：darkTheme = false 时 MaterialTheme 拿到的是亮色 bg 与亮色 primary。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun lightThemeIsWiredWhenDarkThemeFalse() = runComposeUiTest {
        var bg: Color? = null
        var primary: Color? = null
        setContent {
            BareZenTheme(darkTheme = false) {
                bg = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()
        assertEquals(BareZenLightBg, bg)
        assertEquals(BareZenLightAccent, primary)
    }
}
