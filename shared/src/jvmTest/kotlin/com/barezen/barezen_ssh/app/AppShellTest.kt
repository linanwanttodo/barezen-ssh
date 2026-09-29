// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/AppShellTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlin.test.Test

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        // 六目的地照设计包 IA 顺序（仪表盘首位）
        listOf("仪表盘", "服务器", "终端", "文件", "端口转发", "设置").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
        // 默认目的地仍是服务器（功能优先，落点不换）
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithText("终端").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithText("文件").performClick()
        onNodeWithText("传输队列占位（M2）").assertIsDisplayed()
        onNodeWithText("设置").performClick()
        onNodeWithText("跟随系统").assertIsNotEnabled()
        onNodeWithText("仪表盘").performClick()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarCollapsesToIconsAndExpandsBack() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        onNodeWithText("服务器").assertIsDisplayed()
        onNodeWithTag("sidebar-toggle").performClick()          // 收起
        onNodeWithText("服务器").assertDoesNotExist()           // 文字标签隐藏（精确匹配，不误中「新建服务器」）
        onNodeWithTag("sidebar-toggle").performClick()          // 展开
        onNodeWithText("服务器").assertIsDisplayed()
    }
}
