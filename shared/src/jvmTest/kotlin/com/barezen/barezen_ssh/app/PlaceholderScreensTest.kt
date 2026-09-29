package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.screens.DashboardScreen
import com.barezen.barezen_ssh.ui.screens.FilesScreen
import com.barezen.barezen_ssh.ui.screens.PortsScreen
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderScreensTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun filesScreenSkeleton() = runComposeUiTest {
        setContent { BareZenTheme { FilesScreen() } }
        onNodeWithText("文件传输").assertIsDisplayed()
        onNodeWithText("上传").assertIsNotEnabled()
        onNodeWithText("下载").assertIsNotEnabled()
        onNodeWithText("本地").assertIsDisplayed()
        onNodeWithText("远程").assertIsDisplayed()
        onNodeWithText("名称").assertIsDisplayed()
        onNodeWithText("大小").assertIsDisplayed()
        onNodeWithText("修改时间").assertIsDisplayed()
        onNodeWithText("传输队列占位（M2）").assertIsDisplayed()
        onNodeWithText("占位（M2）").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dashboardScreenHoldsMetricSlotsNotNumbers() = runComposeUiTest {
        setContent { BareZenTheme { DashboardScreen() } }
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("选择服务器").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
        listOf("CPU", "内存", "平均负载", "运行时间").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("CPU / 内存 / 网络折线图占位（M4）").assertIsDisplayed()
        onNodeWithText("磁盘用量条形图占位（M4）").assertIsDisplayed()
        // 四个指标值均为 —，绝无示例数值（brief 授权的 assertAny 回退写法：恰 4 个节点）
        assertEquals(4, onAllNodesWithText("—").fetchSemanticsNodes().size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun portsScreenHoldsRealZeroCount() = runComposeUiTest {
        setContent { BareZenTheme { PortsScreen() } }
        onNodeWithText("端口转发").assertIsDisplayed()
        onNodeWithText("新建转发").assertIsNotEnabled()
        onNodeWithText("活动转发：0 条；需要先建立 SSH 连接才能启动新的转发。").assertIsDisplayed()
        onNodeWithText("转发表单占位（M3）").assertIsDisplayed()
        onNodeWithText("类型：本地监听 / 服务器监听 / SOCKS5").assertIsDisplayed()
        onNodeWithText("已保存的配置 / 活动转发列表占位（M3）").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun settingsEightCategoriesSwitchClientSide() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen() } }
        listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")
            .forEach { onNodeWithText(it).assertIsDisplayed() }
        // 默认分类：外观 4 行且全部禁用
        onNodeWithText("主题").assertIsDisplayed()
        onNodeWithText("跟随系统").assertIsNotEnabled()
        onNodeWithText("界面字体").assertIsDisplayed()
        onNodeWithText("显示语言").assertIsDisplayed()
        onNodeWithText("简体中文").assertIsNotEnabled()
        // 切分类（客户端状态）
        onNodeWithText("存储").performClick()
        onNodeWithText("「存储」设置页占位。").assertIsDisplayed()
    }
}
