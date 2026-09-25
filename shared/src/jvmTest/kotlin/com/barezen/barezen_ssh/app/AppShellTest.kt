package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlin.test.Test

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun railShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        onNodeWithText("服务器").assertIsDisplayed()
        onNodeWithText("终端").assertIsDisplayed()
        onNodeWithText("文件").assertIsDisplayed()
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("终端").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithText("设置").performClick()
        onNodeWithText("设置").assertExists()
        onNodeWithText("「设置」功能尚未启用。").assertIsDisplayed()
    }
}
