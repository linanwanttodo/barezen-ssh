package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.ui.screens.DashboardScreen
import com.barezen.ssh.ui.screens.FilesScreen
import com.barezen.ssh.ui.screens.PortsScreen
import com.barezen.ssh.ui.screens.SettingsScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderScreensTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun filesScreenSkeleton() = runComposeUiTest {
        // FilesScreen 重写后：未连接态整屏提示，全部操作禁用（不造数）
        setContent { BareZenTheme { FilesScreen() } }
        onNodeWithText("文件传输").assertIsDisplayed()
        onNodeWithText("上传").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("新建文件夹").assertIsNotEnabled()
        onNodeWithText("连接后可管理文件").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dashboardScreenHoldsMetricSlotsNotNumbers() = runComposeUiTest {
        setContent { BareZenTheme { DashboardScreen() } }
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("选择服务器").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
        listOf("CPU", "内存", "平均负载", "运行时间").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("CPU 使用率（最近 60 次采样）").assertIsDisplayed()
        onNodeWithText("磁盘用量").assertIsDisplayed()
        // 空态占位文案：两张图表卡各一处
        assertEquals(2, onAllNodesWithText("连接后显示主机指标").fetchSemanticsNodes().size)
        // 四个指标值均为 —，绝无示例数值（brief 授权的 assertAny 回退写法：恰 4 个节点）
        assertEquals(4, onAllNodesWithText("—").fetchSemanticsNodes().size)
    }

    // portsScreenHoldsRealZeroCount 已删除：PortsScreen 重写为真实功能屏，
    // 未连接态与新建表单行为由 jvmTest ui/PortsScreenTest.kt 覆盖。

    @OptIn(ExperimentalTestApi::class)
    @Test fun settingsEightCategoriesSwitchClientSide() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(AppModel.forUiTest()) } }
        listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")
            .forEach { onNodeWithText(it).assertIsDisplayed() }
        // 默认分类：外观（本任务为过渡空壳，真实 4 行由 Task 7 落地）
        onNodeWithText("外观").assertIsDisplayed()
        // 切分类（客户端状态）：存储已不再是占位页，而是过渡空壳（旧占位文案不再出现）
        onNodeWithText("存储").performClick()
        onNodeWithText("「存储」设置页占位。").assertDoesNotExist()
    }
}
