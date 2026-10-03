// shared/src/jvmTest/kotlin/com/barezen/ssh/app/AppShellTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        // 六目的地照设计包 IA 顺序（仪表盘首位）。用 testTag 定位：
        // 标题栏同步渲染同名屏名，按纯文本查会命中两个节点。
        val navs = listOf(
            "DASHBOARD" to "仪表盘", "SERVERS" to "服务器", "TERMINAL" to "终端",
            "FILES" to "文件", "PORTS" to "端口转发", "SETTINGS" to "设置",
        )
        navs.forEach { (key, _) ->
            onNodeWithTag("sidebar-nav-$key").assertIsDisplayed()
        }
        // 默认目的地仍是服务器（功能优先，落点不换）
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithTag("sidebar-nav-TERMINAL").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithTag("sidebar-nav-FILES").performClick()
        onNodeWithText("在本地与远程主机之间拖拽传输文件").assertIsDisplayed()
        onNodeWithTag("sidebar-nav-SETTINGS").performClick()
        // 设置屏默认落在「外观」分类
        onNodeWithText("外观").assertIsDisplayed()
        onNodeWithTag("sidebar-nav-DASHBOARD").performClick()
        onNodeWithText("实时监控 SSH 主机的关键指标").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarCollapsesToIconsAndExpandsBack() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        // 展开态：导航项既有文字也有 tag
        onNodeWithTag("sidebar-nav-SERVERS").assertIsDisplayed()
        assertTrue(onAllNodesWithText("服务器").fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("sidebar-toggle").performClick()          // 收起
        // 收起态：导航项仍在（仅剩图标），但**文字标签不再渲染**
        onNodeWithTag("sidebar-nav-SERVERS").assertIsDisplayed()
        assertEquals(1, onAllNodesWithText("服务器").fetchSemanticsNodes().size)  // 只剩标题栏的屏名
        onNodeWithTag("sidebar-toggle").performClick()          // 展开
        assertTrue(onAllNodesWithText("服务器").fetchSemanticsNodes().size >= 2) // 导航标签 + 屏名
    }

    /**
     * 需求文档 §2「平台与语言硬约束」第 3 条：*「所有操作可 Tab 到达，Tab 顺序符合视觉顺序」*。
     * 从首个导航项起逐次 Tab，每一步的键盘焦点都应落到视觉顺序上的下一项（最后是收起按钮）。
     * 焦点环的**可见性**由 `Modifier.focusRing` 保证（`assertIsFocused` 证明焦点确实到位）。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardTabReachesEveryDestinationInVisualOrder() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }

        onNodeWithTag("sidebar-nav-DASHBOARD").requestFocus().assertIsFocused()

        listOf("SERVERS", "TERMINAL", "FILES", "PORTS", "SETTINGS").forEach { key ->
            onRoot().performKeyInput {
                keyDown(Key.Tab)
                keyUp(Key.Tab)
            }
            onNodeWithTag("sidebar-nav-$key").assertIsFocused()
        }

        onRoot().performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }
        onNodeWithTag("sidebar-toggle").assertIsFocused()
    }
}
