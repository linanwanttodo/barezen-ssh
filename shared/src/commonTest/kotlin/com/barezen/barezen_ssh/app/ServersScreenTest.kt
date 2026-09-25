// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/ServersScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import com.barezen.barezen_ssh.ui.screens.ServersScreen
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test

class ServersScreenTest {
    private fun model() = AppModel(
        repo = InMemoryServerRepository(
            listOf(
                Server("1", "web-01", "10.0.0.11", 22, "root", listOf("生产")),
                Server("2", "db-01", "10.0.0.12", 22, "root", listOf("生产", "数据库")),
            )
        ),
        ssh = object : SshClient {},
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
}
