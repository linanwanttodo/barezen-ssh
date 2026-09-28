package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectFlowTest {
    private val web01 = Server("1", "web-01", "10.0.0.11", 22, "root")

    private fun model(ssh: SshClient) = AppModel(
        repo = InMemoryServerRepository(listOf(web01)),
        ssh = ssh,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val hangingSsh = object : SshClient {
        val gate = CompletableDeferred<Unit>()
        // 悬停连接：gate 不放行则永不返回（放行仅测试误用时到达）
        override suspend fun connect(request: ConnectRequest): SshSession {
            gate.await()
            error("悬停连接被放行——测试不应到达此处")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectingDialogShowsAndCancelRestoresDisconnected() = runComposeUiTest {
        val m = model(hangingSsh)
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Connecting)
        setContent { BareZenAppContent(model = m) }
        onNodeWithText("正在连接 web-01…").assertIsDisplayed()
        onNodeWithText("取消").performClick()
        assertTrue(m.connection is ConnectionState.Disconnected)
        onNodeWithText("正在连接 web-01…").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedDialogDismissLeavesBanner() = runComposeUiTest {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("Auth fail")
        })
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Failed)
        setContent { BareZenAppContent(model = m) }
        // 对话框与横幅同文案：先 2 个节点，关掉对话框后剩横幅 1 个
        assertEquals(2, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("取消").performClick()
        assertEquals(1, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("重试").assertIsDisplayed()   // 横幅上的重试仍在
    }

    // 取消证据（编排方 2026-09-28 授权常驻）：简报测试只能看见状态与 UI，
    // 这条断言证明 cancelConnect() 真的掐掉在途协程，而非只关对话框。
    @Test fun cancelConnectCancelsInFlightJob() {
        val gate = CompletableDeferred<Unit>()
        var observedCancellation = false
        val ssh = object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession {
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    observedCancellation = true
                    throw e
                }
                error("gate 放行后不应到达——连接早已取消")
            }
        }
        val m = model(ssh)
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Connecting)
        m.cancelConnect()
        assertTrue(observedCancellation)                       // 悬停协程收到取消
        assertTrue(m.connection is ConnectionState.Disconnected)
        gate.complete(Unit)                                    // 放行：已取消的协程不得复活改写状态
        assertTrue(m.connection is ConnectionState.Disconnected)
        assertNull(m.shellSession)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectDialogShowsMemoryOnlySecurityNote() = runComposeUiTest {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("unused")
        })
        setContent { BareZenAppContent(model = m) }
        m.requestConnect(web01)                       // 弹认证对话框
        onNodeWithText("密码仅保存在内存中，不会写入本地文件。").assertIsDisplayed()
    }
}
