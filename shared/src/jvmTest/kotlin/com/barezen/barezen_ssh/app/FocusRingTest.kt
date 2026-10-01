// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/FocusRingTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import com.barezen.barezen_ssh.ui.theme.BareZenAccent
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 需求文档 §2「平台与语言硬约束」第 3 条的后半句：*「焦点环可见」*。
 *
 * `AppShellTest.keyboardTabReachesEveryDestinationInVisualOrder` 证明 **Tab 能到达**且顺序符合视觉顺序；
 * 但那只证明可达性——裸 `Modifier.clickable` 本身就是可聚焦的，测试照样会过。
 * 本测试证明**焦点环真的被画出来**：对同一个「未选中」条目，聚焦前后各截图，数 accent(primary) 像素。
 *
 * 取未选中项而非选中项，是因为选中项的文字本身就是 primary 色，会污染计数。
 */
class FocusRingTest {

    /** 统计与 `BareZenAccent` 足够接近的像素数（容忍描边抗锯齿与背景的混色）。 */
    private fun accentPixels(bitmap: ImageBitmap): Int {
        val px = bitmap.toPixelMap()
        var n = 0
        for (y in 0 until px.height) {
            for (x in 0 until px.width) {
                val c = px[x, y]
                if (abs(c.red - BareZenAccent.red) < 0.12f &&
                    abs(c.green - BareZenAccent.green) < 0.12f &&
                    abs(c.blue - BareZenAccent.blue) < 0.12f
                ) n++
            }
        }
        return n
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun focusedNavItemDrawsVisibleFocusRing() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        waitForIdle()

        // 默认目的地是「服务器」（它自身文字是 accent 色），故在**整屏**上比 delta，
        // 避免被选中项自带的 accent 像素污染。
        val before = accentPixels(onRoot().captureToImage())

        // 取未选中的「文件」
        onNodeWithText("文件").requestFocus().assertIsFocused()
        waitForIdle()

        val after = accentPixels(onRoot().captureToImage())
        assertTrue(
            after > before,
            "聚焦后整屏 accent 像素应增加（=画出焦点环）；before=$before after=$after",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun focusedSettingsCategoryDrawsVisibleFocusRing() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        onNodeWithText("设置").performClick()          // 进设置屏（左列默认选中「外观」）
        waitForIdle()

        val before = accentPixels(onRoot().captureToImage())

        // 取未选中的「凭据」——不与主导航任何 label 重名，避免节点歧义
        onNodeWithText("凭据").requestFocus().assertIsFocused()
        waitForIdle()

        val after = accentPixels(onRoot().captureToImage())
        assertTrue(
            after > before,
            "聚焦设置分类项后整屏 accent 像素应增加（=画出焦点环）；before=$before after=$after",
        )
    }
}
