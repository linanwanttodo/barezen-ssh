// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/ComponentsTest.kt
package com.barezen.ssh.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ui.components.Badge
import com.barezen.ssh.ui.components.BadgeTone
import com.barezen.ssh.ui.components.Banner
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzSwitch
import com.barezen.ssh.ui.components.FilterChipPill
import com.barezen.ssh.ui.components.SegmentedControl
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 控件库契约（2026-10-03 重设计新增）。
 * 锁的是**行为与禁用语义**，不是像素——视觉达标由 `ThemeColorsTest` 的 AA 用例保证。
 */
@OptIn(ExperimentalTestApi::class)
class ComponentsTest {

    @Test fun disabledBtnIsNotEnabled() = runComposeUiTest {
        // 禁用即不可点：能力未接入时给「看得见但点不动」，而不是藏起来（CONVENTIONS §1.7）
        setContent {
            BareZenTheme { Btn("新建服务器", {}, kind = BtnKind.primary, enabled = false) }
        }
        onNodeWithText("新建服务器").assertIsNotEnabled()
    }

    @Test fun disabledBtnDoesNotFireOnClick() = runComposeUiTest {
        var fired = 0
        setContent {
            BareZenTheme {
                Btn("全部重连", { fired++ }, kind = BtnKind.ghost, enabled = false)
            }
        }
        onNodeWithText("全部重连").performClick()
        assertEquals(0, fired, "禁用按钮不得触发回调")
    }

    @Test fun enabledBtnFiresOnClick() = runComposeUiTest {
        var fired = 0
        setContent { BareZenTheme { Btn("连接", { fired++ }) } }
        onNodeWithText("连接").performClick()
        assertEquals(1, fired)
    }

    @Test fun segmentedControlDisablesOnlyMarkedOption() = runComposeUiTest {
        val picked = mutableListOf<Int>()
        setContent {
            BareZenTheme {
                SegmentedControl(
                    options = listOf("本地监听", "服务器监听", "SOCKS5"),
                    selectedIndex = 0,
                    disabledIndices = setOf(2),
                    onSelect = { picked += it },
                )
            }
        }
        // SOCKS5 底层不支持 -> 可见但禁用；其余两项可选
        onNodeWithText("SOCKS5").assertIsNotEnabled()
        onNodeWithText("服务器监听").performClick()
        assertEquals(listOf(1), picked)
    }

    @Test fun segmentedControlSkipsDisabledOptionOnClick() = runComposeUiTest {
        val picked = mutableListOf<Int>()
        setContent {
            BareZenTheme {
                SegmentedControl(
                    options = listOf("A", "B", "C"),
                    selectedIndex = 0,
                    disabledIndices = setOf(1),
                    onSelect = { picked += it },
                )
            }
        }
        onNodeWithText("B").performClick()
        assertTrue(picked.isEmpty(), "禁用项不得回传选择")
    }

    @Test fun switchFlipsCheckedState() = runComposeUiTest {
        var checked = false
        setContent { BareZenTheme { BzSwitch(checked, { checked = it }) } }
        assertTrue(!checked)
        onNodeWithTag("bz-switch").performClick()
        assertTrue(checked, "点击开关应翻转状态")
    }

    @Test fun bannerRendersTextAndAction() = runComposeUiTest {
        setContent {
            BareZenTheme {
                Banner(
                    text = "3 台服务器未连接",
                    tone = BadgeTone.warning,
                ) {
                    Btn("全部重连", {}, kind = BtnKind.ghost, small = true, enabled = false)
                }
            }
        }
        onNodeWithText("3 台服务器未连接").assertIsDisplayed()
        onNodeWithText("全部重连").assertIsNotEnabled()
    }

    @Test fun badgeRendersAllTones() = runComposeUiTest {
        setContent {
            BareZenTheme {
                Column {
                    Badge("本地", tone = BadgeTone.neutral)
                    Badge("已启用", tone = BadgeTone.success)
                    Badge("失败", tone = BadgeTone.error)
                    FilterChipPill("全部", true, {})
                }
            }
        }
        onNodeWithText("本地").assertIsDisplayed()
        onNodeWithText("已启用").assertIsDisplayed()
        onNodeWithText("失败").assertIsDisplayed()
        onNodeWithText("全部").assertIsDisplayed()
    }
}
