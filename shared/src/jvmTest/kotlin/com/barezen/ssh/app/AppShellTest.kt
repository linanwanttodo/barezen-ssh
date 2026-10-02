// shared/src/jvmTest/kotlin/com/barezen/ssh/app/AppShellTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
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

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        // 六目的地照设计包 IA 顺序（仪表盘首位）
        listOf("仪表盘", "服务器", "终端", "文件", "端口转发", "设置").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
        // 默认目的地仍是服务器（功能优先，落点不换）
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithText("终端").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithText("文件").performClick()
        onNodeWithText("连接后可管理文件").assertIsDisplayed()
        onNodeWithText("设置").performClick()
        // 设置屏默认落在「外观」分类（本任务该分类为过渡空壳，真实内容在后续任务落地）
        onNodeWithText("外观").assertIsDisplayed()
        onNodeWithText("仪表盘").performClick()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarCollapsesToIconsAndExpandsBack() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }
        onNodeWithText("服务器").assertIsDisplayed()
        onNodeWithTag("sidebar-toggle").performClick()          // 收起
        onNodeWithText("服务器").assertDoesNotExist()           // 文字标签隐藏（精确匹配，不误中「新建服务器」）
        onNodeWithTag("sidebar-toggle").performClick()          // 展开
        onNodeWithText("服务器").assertIsDisplayed()
    }

    /**
     * 需求文档 §2「平台与语言硬约束」第 3 条：*「所有操作可 Tab 到达，Tab 顺序符合视觉顺序」*。
     * 从首个导航项起逐次 Tab，每一步的键盘焦点都应落到视觉顺序上的下一项（最后是收起按钮）。
     * 焦点环的**可见性**由 `Modifier.focusRing` 保证（`assertIsFocused` 证明焦点确实到位）。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardTabReachesEveryDestinationInVisualOrder() = runComposeUiTest {
        setContent { BareZenTheme { BareZenAppContent(model = AppModel.forUiTest()) } }

        onNodeWithText("仪表盘").requestFocus().assertIsFocused()

        listOf("服务器", "终端", "文件", "端口转发", "设置").forEach { label ->
            onRoot().performKeyInput {
                keyDown(Key.Tab)
                keyUp(Key.Tab)
            }
            onNodeWithText(label).assertIsFocused()
        }

        onRoot().performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }
        onNodeWithTag("sidebar-toggle").assertIsFocused()
    }
}
