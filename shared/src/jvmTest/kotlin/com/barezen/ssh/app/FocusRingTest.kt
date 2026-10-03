// shared/src/jvmTest/kotlin/com/barezen/ssh/app/FocusRingTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenAccentOnSubtle
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 需求文档 §2「平台与语言硬约束」第 3 条的后半句：*「焦点环可见」*。
 *
 * `AppShellTest.keyboardTabReachesEveryDestinationInVisualOrder` 证明 **Tab 能到达**且顺序符合视觉顺序；
 * 但那只证明可达性——裸 `Modifier.clickable` 本身就是可聚焦的，测试照样会过。
 * 本测试证明**焦点环真的被画出来**：对同一个「未选中」条目，聚焦前后各截图，数焦点环色像素。
 *
 * 取未选中项而非选中项，是因为选中项自带强调色像素，会污染计数。
 *
 * 焦点环用的是 `BareZenAccentOnSubtle`（#44A0FC）而非 `BareZenAccent`（#0A84FF）：
 * 后者是**填充色**，压深色底做细描边时对比不足（见 Color.kt 文件头「结构性约束」），
 * 焦点环必须用文字级的亮蓝才看得见。
 */
class FocusRingTest {

    /** 统计与焦点环色足够接近的像素数（容忍描边抗锯齿与背景的混色）。 */
    private fun accentPixels(bitmap: ImageBitmap, region: Rect? = null): Int {
        val px = bitmap.toPixelMap()
        val x0 = region?.left?.toInt()?.coerceAtLeast(0) ?: 0
        val y0 = region?.top?.toInt()?.coerceAtLeast(0) ?: 0
        val x1 = region?.right?.toInt()?.coerceAtMost(px.width) ?: px.width
        val y1 = region?.bottom?.toInt()?.coerceAtMost(px.height) ?: px.height
        var n = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = px[x, y]
                if (abs(c.red - BareZenAccentOnSubtle.red) < 0.12f &&
                    abs(c.green - BareZenAccentOnSubtle.green) < 0.12f &&
                    abs(c.blue - BareZenAccentOnSubtle.blue) < 0.12f
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
        onNodeWithTag("sidebar-nav-FILES").requestFocus().assertIsFocused()
        waitForIdle()

        val after = accentPixels(onRoot().captureToImage())
        assertTrue(
            after > before,
            "聚焦后整屏焦点环像素应增加（=画出焦点环）；before=$before after=$after",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun focusedSettingsCategoryDrawsVisibleFocusRing() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        onNodeWithTag("sidebar-nav-SETTINGS").performClick()   // 进设置屏（左列默认选中「外观」）
        waitForIdle()

        // 只统计**该分类项自身矩形内**的焦点环像素。
        // 整屏统计在这里不成立：performClick 之后焦点仍留在主导航「设置」上，它自己的
        // 焦点环像素比左列分类项的更多，于是「聚焦分类项」反而让整屏计数下降——
        // 那是基线污染，不是没画环。限定到节点自身区域后，断言只反映这一项。
        val region = onNodeWithTag("settings-nav-4").fetchSemanticsNode().boundsInRoot
        val before = accentPixels(onRoot().captureToImage(), region)

        // 取未选中的「凭据」分类（用 tag 定位：分类名与主导航 label 可能重名）
        onNodeWithTag("settings-nav-4").requestFocus().assertIsFocused()
        waitForIdle()

        val after = accentPixels(onRoot().captureToImage(), region)
        assertTrue(
            after > before,
            "聚焦设置分类项后该项区域内焦点环像素应增加（=画出焦点环）；before=$before after=$after",
        )
    }
}
