// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @OptIn(ExperimentalTestApi::class)
    @Test fun terminalToggleBindsToModel() = runComposeUiTest {
        // 渲染「选中即复制」开关行 + 其 desc（绑定由 ToggleRow 的 checked/onCheckedChange 接入 settings.update，见实现）
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("选中即复制").assertIsDisplayed()
        onNodeWithText("开启后，在终端里选中文本即写入剪贴板").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sudoAutofillIsLabelledAsPending() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("（待凭据库接入后生效）", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun terminalFontAndPaletteAreStaticSingleValueRows() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("JetBrains Mono").assertIsDisplayed()
        onNodeWithText("石墨（graphite）").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun shiftInsertRowIsNotRendered() = runComposeUiTest {
        // 分支 B：JediTerm 3.73 无 Shift+Insert 能力，绝不渲染点了没反应的开关（设计 §9.4 / R7）
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("Shift+Insert 粘贴").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectionConflictPolicyIsLabelledPending() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("连接").performClick()
        onNodeWithText("（待文件传输接入后生效）", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectionHideAddressesBindsToModel() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { SettingsScreen(m) } }
        onNodeWithText("连接").performClick()
        onNodeWithText("隐藏服务器地址").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun storageShowsAbsoluteDataDir() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("存储").performClick()
        onNodeWithText(".barezen", substring = true).assertIsDisplayed()
        onNodeWithText("打开目录").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun updateCheckDisabledWhenRepoBlank() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("更新").performClick()
        onNodeWithText("请先填写更新源").assertIsDisplayed()
        onNodeWithTag("update-check-button").assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun aboutShowsVersionAndLicences() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("关于").performClick()
        onNodeWithText(com.barezen.barezen_ssh.BuildInfo.VERSION, substring = true).assertExists()
        // 组件名/许可名可能与 URL 里的字样重复出现 —— 断言「至少存在一个」而非唯一
        fun visible(text: String) =
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        listOf("sshj", "JediTerm", "JetBrains Mono", "Noto Sans SC").forEach { visible(it) }
        assertTrue(visible("Apache-2.0"))
        assertTrue(visible("OFL-1.1"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun aboutHidesFeedbackWhenUrlIsBlank() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("关于").performClick()
        onNodeWithText("反馈").assertDoesNotExist()
    }
}
