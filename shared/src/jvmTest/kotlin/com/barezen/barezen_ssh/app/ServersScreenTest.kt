// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/ServersScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import com.barezen.barezen_ssh.ui.screens.ServersScreen
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals

class ServersScreenTest {
    private fun model() = AppModel(
        repo = InMemoryServerRepository(
            listOf(
                Server("1", "web-01", "10.0.0.11", 22, "root", listOf("生产")),
                Server("2", "db-01", "10.0.0.12", 22, "root", listOf("生产", "数据库")),
            )
        ),
        ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
        scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test fun searchFiltersCards() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("web-01").assertExists()
        onNodeWithText("db-01").assertExists()
        onNodeWithTag("server-search").performTextInput("db-01")
        // 输入后搜索框的 EditableText 也是 "db-01"，onNodeWithText("db-01") 会同时命中输入框与卡片
        // （Expected exactly 1 node but found 2），故以卡片地址断言 db-01 卡片仍在。
        onNodeWithText("10.0.0.12:22").assertExists()
        onNodeWithText("web-01").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun addDialogSavesServer() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("新建服务器").performClick()
        onNodeWithText("名称").performTextInput("cache-01")
        onNodeWithText("地址").performTextInput("10.0.0.13")
        onNodeWithText("保存").performClick()
        onNodeWithText("cache-01").assertExists()
        assert(m.visibleServers.any { it.name == "cache-01" })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun vocabularyAndReconnectPlaceholder() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        // 简报原断言 onNodeWithText("打开文件传输").assertIsDisplayed() 不可满足：
        // 设计包要求每张卡都带该按钮（mock 三卡皆有），model() 有 2 张卡 → 该文案恒为 2 节点，
        // onNodeWithText 单节点查找必抛「Expected exactly 1 node but found 2」。
        // 按裁决「brief-vs-reality 以设计包/实际代码为准」改为断言两张卡全部换上新词。
        assertEquals(2, onAllNodesWithText("打开文件传输").fetchSemanticsNodes().size)
        onNodeWithText("打开文件管理").assertDoesNotExist()    // 旧词清零
        onNodeWithText("全部重连").assertIsNotEnabled()        // 占位 M2：禁用
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyStateShowsAddFirstServer() = runComposeUiTest {
        val m = AppModel(
            repo = InMemoryServerRepository(emptyList()),
            ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithText("添加第一台服务器以开始使用").assertIsDisplayed()
        onNodeWithText("新建服务器").assertIsDisplayed()
    }

    // 编排方裁决（甲）：认证对话框唯一渲染方是壳（AppShell.kt:100–107），屏内组合看不到它；
    // setContent 改为组合壳（直屏组合会让请求挂起在 pendingConnect 上无人渲染）。
    // 屏内另渲染一份会与壳双挂，且必砸 Task 5 已批准测试（connectDialogShowsMemoryOnlySecurityNote 双节点）。
    @OptIn(ExperimentalTestApi::class)
    @Test fun failedBannerShowsErrorAndRetryOpensDialog() = runComposeUiTest {
        val m = model()
        val failed = Server("1", "web-01", "10.0.0.11", 22, "root")
        m.applyConnectionForTest(ConnectionState.Failed(failed, "Auth fail"))
        setContent { BareZenTheme { BareZenAppContent(model = m) } }
        // Task 5 起失败态横幅与覆盖层同现（ConnectFlowTest 断言 2→1），先关覆盖层再走原意
        assertEquals(2, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("取消").performClick()      // 只有覆盖层有「取消」，横幅没有
        onNodeWithText("连接失败：Auth fail").assertIsDisplayed()
        onNodeWithText("重试").performClick()
        onNodeWithText("连接到 web-01").assertIsDisplayed()     // 重试 = 重开认证对话框
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedCardShowsStatPlaceholders() = runComposeUiTest {
        val m = model()
        val up = Server("1", "web-01", "10.0.0.11", 22, "root")
        m.applyConnectionForTest(ConnectionState.Connected(up, 24))
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("负载").assertIsDisplayed()
        onNodeWithText("内存").assertIsDisplayed()
        onNodeWithText("运行").assertIsDisplayed()
        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()
        // 三个指标值均为「—」（M4 占位，不造数）
        assertEquals(3, onAllNodesWithText("—").fetchSemanticsNodes().size)
    }
}
