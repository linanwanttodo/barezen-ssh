// shared/src/jvmTest/kotlin/com/barezen/ssh/app/DashboardScreenTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.FakeSshClient
import com.barezen.ssh.ssh.FakeSshSession
import com.barezen.ssh.ssh.metrics.DiskUsage
import com.barezen.ssh.ssh.metrics.MetricsSnapshot
import com.barezen.ssh.ui.screens.DashboardScreen
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertTrue

class DashboardScreenTest {

    private val liveSnapshot = MetricsSnapshot(
        load1 = 0.52, load5 = 0.58, load15 = 0.59,
        memUsedPct = 38.0, memTotalKb = 8_388_608L,
        uptimeSeconds = 93_720.35,
        cpuPct = 12.4,
        disks = listOf(
            DiskUsage("/dev/sda1", 40_188_544, 12_345_678, 25_772_034, 33, "/"),
            DiskUsage("/dev/sdb1", 52_428_800, 26_214_400, 26_214_400, 50, "/mnt/data"),
        ),
        epochMs = 1_000L,
    )

    // ---- 未连接态：占位与禁用（红线：无连接不造数）----

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedShowsPlaceholdersAndDisabledButtons() = runComposeUiTest {
        setContent { BareZenTheme { DashboardScreen() } }
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("选择服务器").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
        listOf("CPU", "内存", "平均负载", "运行时间").forEach { onNodeWithText(it).assertIsDisplayed() }
        // 四个指标值均为 —，绝无示例数值
        assertTrue(onAllNodesWithText("—").fetchSemanticsNodes().size == 4)
        // 图表卡为空态占位文案（两张图表卡各一处）
        assertTrue(onAllNodesWithText("连接后显示主机指标").fetchSemanticsNodes().size == 2)
        onNodeWithText("CPU 使用率（最近 60 次采样）").assertIsDisplayed()
        onNodeWithText("磁盘用量").assertIsDisplayed()
    }

    // ---- 已连接态：真数据渲染 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedShowsLiveValues() = runComposeUiTest {
        setContent { BareZenTheme { DashboardScreen(liveSnapshot, connected = true, onRefresh = {}) } }
        onNodeWithText("12%").assertIsDisplayed()                     // CPU 12.4 取整
        onNodeWithText("38%").assertIsDisplayed()                     // 内存占用
        onNodeWithText("已用 3.0 GB / 总 8.0 GB").assertIsDisplayed() // 内存副行
        onNodeWithText("0.52").assertIsDisplayed()                    // load1
        onNodeWithText("5 分钟 0.58 / 15 分钟 0.59").assertIsDisplayed()
        onNodeWithText("1 天 2 小时").assertIsDisplayed()             // 93720s
        onNodeWithText("刷新").assertIsEnabled()
        // 磁盘条：挂载点与用量
        onNodeWithText("/").assertIsDisplayed()
        onNodeWithText("33%").assertIsDisplayed()
        onNodeWithText("/mnt/data").assertIsDisplayed()
        onNodeWithText("50%").assertIsDisplayed()
        // 单个 CPU 采样尚不足画折线：空态提示
        onNodeWithText("等待主机指标").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedCollectionFallsBackToPlaceholderValue() = runComposeUiTest {
        // 采集失败（snapshot 为 null）但已连接：值位回退 —，不得造数
        setContent { BareZenTheme { DashboardScreen(null, connected = true, onRefresh = {}) } }
        assertTrue(onAllNodesWithText("—").fetchSemanticsNodes().size == 4)
        onNodeWithText("刷新").assertIsEnabled()
    }

    // ---- AppShell 接线：FakeSshSession 采集真数据上屏 ----

    private fun wiredModel(): AppModel {
        val canned = mapOf(
            "cat /proc/loadavg" to "0.52 0.58 0.59 1/412 8123",
            "cat /proc/meminfo" to "MemTotal:        8388608 kB\nMemAvailable:    4194304 kB\n",
            "cat /proc/uptime" to "93720.35 180000.00",
            "cat /proc/stat" to "cpu  100 0 100 800 0 0 0 0 0 0\ncpu0 1 2 3 4 5 6 7 8 9\n",
            "df -P" to "Filesystem 1024-blocks Used Available Capacity Mounted on\n" +
                "/dev/sda1 40188544 12345678 25772034 33% /\n",
        )
        val session = FakeSshSession { cmd -> ExecResult(0, canned[cmd] ?: "", "") }
        return AppModel(
            repo = InMemoryServerRepository(
                listOf(com.barezen.ssh.servers.Server("srv-1", "web-01", "127.0.0.1", 22, "test")),
            ),
            ssh = FakeSshClient(session),
            scope = CoroutineScope(Dispatchers.Unconfined),
            settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun wiringShowsLiveMetricsAfterConnect() = runComposeUiTest {
        val model = wiredModel()
        model.startConnect(
            com.barezen.ssh.servers.Server("srv-1", "web-01", "127.0.0.1", 22, "test"),
            AuthMethod.Password("secret"),
        )
        // startConnect 成功后落在 TERMINAL，手动导航回仪表盘
        model.navigate(Destination.DASHBOARD)
        setContent { BareZenTheme { BareZenAppContent(model) } }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("0.52").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("0.52").assertIsDisplayed()
        onNodeWithText("1 天 2 小时").assertIsDisplayed()
        onNodeWithText("33%").assertIsDisplayed()
    }
}
