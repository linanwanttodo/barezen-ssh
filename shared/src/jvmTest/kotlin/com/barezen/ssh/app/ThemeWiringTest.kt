package com.barezen.ssh.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
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
}
