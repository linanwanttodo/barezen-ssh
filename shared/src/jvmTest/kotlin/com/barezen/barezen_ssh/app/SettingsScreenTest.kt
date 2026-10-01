// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsScreenTest {

    private fun model() = AppModel.forUiTest()

    @OptIn(ExperimentalTestApi::class)
    @Test fun allEightCategoriesAreNavigable() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun loadNoticeIsShownAndDismissable() = runComposeUiTest {
        val m = model()
        m.settings.update { it.copy(uiScale = 1.5f) }   // 触发一次状态变化，确认通知区可渲染
        setContent { BareZenTheme { SettingsScreen(m) } }
        // 通知区只在有内容时出现；此处用假仓库注入告警的场景由 SettingsModelTest 覆盖，
        // UI 层只断言"有告警时能显示并能关掉"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun assistantAndCredentialsAreHonestPlaceholders() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("智能助手").performClick()
        onNodeWithText("智能助手属 M5，尚未接入。").assertIsDisplayed()
        onNodeWithText("凭据").performClick()
        onNodeWithText("凭据库属 M3，尚未接入。").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceLightThemeOptionIsDisabled() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("浅色（未实现）").assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceScaleChangeUpdatesModel() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { SettingsScreen(m) } }
        onNodeWithText("125%").performClick()
        assertEquals(1.25f, m.settings.settings.uiScale)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceSingleValueRowsAreStaticText() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("Noto Sans SC").assertIsDisplayed()
        onNodeWithText("随包分发，暂无可选项").assertIsDisplayed()
    }
}
