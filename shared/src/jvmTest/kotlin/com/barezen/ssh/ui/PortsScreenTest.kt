// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/PortsScreenTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.forward.ForwardEntryState
import com.barezen.ssh.ssh.forward.ForwardManager
import com.barezen.ssh.servers.ForwardRule
import com.barezen.ssh.servers.ForwardRuleStore
import com.barezen.ssh.ui.screens.PortsModel
import com.barezen.ssh.ui.screens.PortsScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 纯内存规则存储，UI 测试不落盘。 */
private class InMemoryForwardRuleStore(
    initial: List<ForwardRule> = emptyList(),
) : ForwardRuleStore {
    private val rules = CopyOnWriteArrayList(initial)
    override fun list(): List<ForwardRule> = rules.toList()
    override fun upsert(rule: ForwardRule) {
        rules.removeAll { it == rule }
        rules.add(rule)
    }

    override fun delete(rule: ForwardRule) {
        rules.remove(rule)
    }
}

private class FakeForwardTunnel(override val spec: ForwardSpec) : ForwardTunnel {
    var closed = false
    override fun close() {
        closed = true
    }
}

private fun fakeManagerFlow(vararg failPorts: Int): MutableStateFlow<ForwardManager> {
    val manager = ForwardManager { spec ->
        if (spec.bindPort in failPorts) throw IllegalStateException("端口被占用")
        FakeForwardTunnel(spec)
    }
    return MutableStateFlow(manager)
}

private fun testModel(
    store: InMemoryForwardRuleStore = InMemoryForwardRuleStore(),
    managerFlow: StateFlowAlias = MutableStateFlow(null),
): PortsModel = PortsModel(
    scope = CoroutineScope(Dispatchers.Unconfined),
    ruleStore = store,
    managerFlow = managerFlow,
)

private typealias StateFlowAlias = kotlinx.coroutines.flow.StateFlow<ForwardManager?>

@OptIn(ExperimentalTestApi::class)
class PortsScreenTest {

    // ---- 未连接态 ----

    @Test
    fun disconnectedFormDisabledWithHint() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store)) } }
        onNodeWithText("端口转发").assertIsDisplayed()
        onNodeWithText("连接后可启用转发").assertIsDisplayed()
        onNodeWithText("添加").assertIsNotEnabled()
        onNodeWithTag("ports-bind-port").assertIsNotEnabled()
        onNodeWithTag("ports-target-host").assertIsNotEnabled()
        onNodeWithTag("ports-target-port").assertIsNotEnabled()
        // 空规则列表如实展示
        onNodeWithText("暂无转发规则。添加后规则会保存在本机，下次连接可直接启用。").assertIsDisplayed()
        assertEquals(0, store.list().size)
    }

    // ---- 表单校验 ----

    @Test
    fun invalidBindPortShowsErrorAndDoesNotPersist() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store, fakeManagerFlow())) } }
        onNodeWithTag("ports-bind-port").performTextInput("0")
        onNodeWithTag("ports-target-host").performTextInput("db.internal")
        onNodeWithTag("ports-target-port").performTextInput("5432")
        onNodeWithText("添加").performClick()
        onNodeWithTag("ports-form-error").assertIsDisplayed()
        onNodeWithText("监听端口需为 1-65535 的数字").assertIsDisplayed()
        assertEquals(0, store.list().size)
    }

    @Test
    fun portAboveMaximumRejected() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store, fakeManagerFlow())) } }
        onNodeWithTag("ports-bind-port").performTextInput("70000")
        onNodeWithTag("ports-target-host").performTextInput("db.internal")
        onNodeWithTag("ports-target-port").performTextInput("5432")
        onNodeWithText("添加").performClick()
        onNodeWithTag("ports-form-error").assertIsDisplayed()
        assertEquals(0, store.list().size)
    }

    @Test
    fun blankTargetHostRejected() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store, fakeManagerFlow())) } }
        onNodeWithTag("ports-bind-port").performTextInput("18081")
        onNodeWithTag("ports-target-host").performTextInput("   ")
        onNodeWithTag("ports-target-port").performTextInput("5432")
        onNodeWithText("添加").performClick()
        onNodeWithText("目标主机不能为空").assertIsDisplayed()
        assertEquals(0, store.list().size)
    }

    @Test
    fun invalidTargetPortRejected() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store, fakeManagerFlow())) } }
        onNodeWithTag("ports-bind-port").performTextInput("18081")
        onNodeWithTag("ports-target-host").performTextInput("db.internal")
        onNodeWithTag("ports-target-port").performTextInput("abc")
        onNodeWithText("添加").performClick()
        onNodeWithText("目标端口需为 1-65535 的数字").assertIsDisplayed()
        assertEquals(0, store.list().size)
    }

    // ---- 添加与激活 ----

    @Test
    fun validRuleIsSavedActivatesAndShowsBadge() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        val managerFlow = fakeManagerFlow()
        setContent { BareZenTheme { PortsScreen(testModel(store, managerFlow)) } }
        onNodeWithTag("ports-bind-port").performTextInput("18081")
        onNodeWithTag("ports-target-host").performTextInput("db.internal")
        onNodeWithTag("ports-target-port").performTextInput("5432")
        onNodeWithText("添加").performClick()
        // 列表出现规则行
        onNodeWithText("本地 18081 -> db.internal:5432").assertIsDisplayed()
        onNodeWithText("已启用").assertIsDisplayed()
        // 已持久化且隧道激活
        assertEquals(1, store.list().size)
        val entries = managerFlow.value.entries.value
        assertEquals(1, entries.size)
        assertEquals(ForwardEntryState.ACTIVE, entries.first().state)
    }

    @Test
    fun remoteKindSelectableAndPersisted() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        setContent { BareZenTheme { PortsScreen(testModel(store, fakeManagerFlow())) } }
        onNodeWithText("远程").performClick()
        onNodeWithTag("ports-bind-port").performTextInput("18082")
        onNodeWithTag("ports-target-host").performTextInput("web.local")
        onNodeWithTag("ports-target-port").performTextInput("80")
        onNodeWithText("添加").performClick()
        onNodeWithText("远程 18082 -> web.local:80").assertIsDisplayed()
        assertEquals(ForwardKind.REMOTE, store.list().single().kind)
    }

    @Test
    fun dynamicKindDisabledWithUpcomingNote() = runComposeUiTest {
        setContent { BareZenTheme { PortsScreen(testModel()) } }
        onNodeWithText("动态").assertIsDisplayed()
        onNodeWithText("动态").assertIsNotEnabled()
        onNodeWithText("动态（SOCKS5）即将支持", substring = true).assertIsDisplayed()
    }

    // ---- 删除 ----

    @Test
    fun deleteRuleRemovesFromListStoreAndStopsTunnel() = runComposeUiTest {
        val rule = ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432)
        val store = InMemoryForwardRuleStore(initial = listOf(rule))
        val managerFlow = fakeManagerFlow()
        val tunnel = runBlocking {
            managerFlow.value.apply(rule.toSpec()).tunnel as FakeForwardTunnel
        }
        setContent { BareZenTheme { PortsScreen(testModel(store, managerFlow)) } }
        onNodeWithText("本地 18081 -> db.internal:5432").assertIsDisplayed()
        onNodeWithText("删除").performClick()
        assertTrue(onAllNodesWithText("本地 18081 -> db.internal:5432").fetchSemanticsNodes().isEmpty())
        assertEquals(0, store.list().size)
        assertEquals(0, managerFlow.value.entries.value.size)
        assertTrue(tunnel.closed)
    }

    // ---- 失败态 ----

    @Test
    fun failedForwardShowsFailureBadgeAndMessage() = runComposeUiTest {
        val store = InMemoryForwardRuleStore()
        val managerFlow = fakeManagerFlow(failPorts = intArrayOf(18099))
        setContent { BareZenTheme { PortsScreen(testModel(store, managerFlow)) } }
        onNodeWithTag("ports-bind-port").performTextInput("18099")
        onNodeWithTag("ports-target-host").performTextInput("db.internal")
        onNodeWithTag("ports-target-port").performTextInput("5432")
        onNodeWithText("添加").performClick()
        onNodeWithText("失败：端口被占用").assertIsDisplayed()
    }
}
