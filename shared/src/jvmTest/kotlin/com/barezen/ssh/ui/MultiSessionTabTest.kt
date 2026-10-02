// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/MultiSessionTabTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.Destination
import com.barezen.ssh.app.QueuedSshClient
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ui.screens.TerminalScreen
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 刀 5：多标签条 + 关闭语义 + 终端重挂提示（spec §4.1 / §4.4 / §5.1）。
 *
 * 本套用例（除中键用例）一律组合 [TerminalScreen] 而非整个壳层：壳层路由只在
 * startConnect 成功的瞬间跳终端页，故非终端页组合会让本屏根本不参与组合。
 *
 * 会话语义替身**刻意不实现 startShell**（本套 fake 的 startShell 一律 error）。原因：
 * 一旦某条会话真的进入 Connected 且被组合，[com.barezen.ssh.terminal.TerminalView] 会挂真
 * SwingPanel，在无显示环境的 headless 测试里抛 LocalInteropContainer not provided（见
 * TerminalScreenTest 顶部已登记的同类限制）。标签条、关闭确认、重挂提示三者的判定都不依赖
 * 终端内容，故本套用「真实走一遍 Connected 再退回 Failed」的编排得到可组合的会话列表。
 */
class MultiSessionTabTest {

    /** 只够支撑注册表状态机与会话资源的会话替身；终端相关能力一律不实现（见类注释）。 */
    private class TabSession(val label: String) : SshSession {
        override fun pingMs(): Long = 1L
        override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
        override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
            error("本测试不挂终端（headless 无 interop holder）")

        override fun newSftp(): SftpFs = error("本测试不开 SFTP")
        override fun startForward(spec: ForwardSpec): ForwardTunnel = error("本测试不建隧道")
        override fun close() = Unit
    }

    private val web01 = Server("srv-a", "web-01", "10.0.0.11", 22, "root")
    private val db01 = Server("srv-b", "db-01", "10.0.0.12", 22, "root")

    private fun model() = AppModel(
        repo = InMemoryServerRepository(listOf(web01, db01)),
        ssh = QueuedSshClient(listOf(TabSession("a"), TabSession("b"), TabSession("c"))),
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    /**
     * 在列表里造出一条「可安全组合」的会话：先真连上（拿到非空 session 与独立 id），
     * 再用 shell 启动失败的原语把它退回 Failed、shell 启动失败的原语原地复用 id 与 title。
     * 于是标签仍在列表中可渲染，而组合活动会话时不会挂真 SwingPanel。
     */
    private fun AppModel.safeSession(server: Server): Long {
        startConnect(server, AuthMethod.Password("x"))
        val id = registry.activeId ?: error("建连后必有活动会话")
        reportShellStartFailed(id, "测试占位：不挂真实终端")
        return id.raw
    }

    private fun tag(raw: Long) = "session-tab-$raw"

    // ---- §4.1 标签条渲染全部会话 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun tabStripRendersEverySessionNotOnlyActiveOne() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)
        assertEquals(2, m.registry.sessions.size)

        setContent { BareZenTheme { TerminalScreen(m) } }

        // 两条会话各有自己的标签；数据源是 registry.sessions 而不是 active 单值投影
        onNodeWithTag(tag(rawA)).assertExists()
        onNodeWithTag(tag(rawB)).assertExists()
        // 标题仍在标签上（身份是 SessionId，但用户看见的是名字）
        onNodeWithText("web-01").assertIsDisplayed()
        onNodeWithText("db-01").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun activeTabCarriesSelectedSemantics() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)      // 后连者成为活动会话

        setContent { BareZenTheme { TerminalScreen(m) } }

        onNodeWithTag(tag(rawB)).assertIsSelected()
        onNodeWithTag(tag(rawA)).assertIsNotSelected()
    }

    /** 同名服务器连两条：标题会重名，故一律以 testTag 定位（CONVENTIONS §4.3）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun duplicateTitlesStayDistinguishableByTag() = runComposeUiTest {
        val m = model()
        val first = m.safeSession(web01)
        val second = m.safeSession(web01)
        assertEquals(listOf("web-01", "web-01 (2)"), m.registry.sessions.map { it.title })

        setContent { BareZenTheme { TerminalScreen(m) } }

        onNodeWithTag(tag(first)).assertExists()
        onNodeWithTag(tag(second)).assertExists()
    }

    // ---- §4.1 交互：单击切换、关闭按钮、中键关闭 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun clickingInactiveTabActivatesIt() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag(tag(rawB)).assertIsSelected()

        onNodeWithTag(tag(rawA)).performClick()

        assertEquals(rawA, m.registry.activeId?.raw, "单击标签必须切换前台会话")
        onNodeWithTag(tag(rawA)).assertIsSelected()
        onNodeWithTag(tag(rawB)).assertIsNotSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun closeButtonRemovesThatTabOnly() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag("session-tab-close-$rawA").performClick()

        assertEquals(listOf(rawB), m.registry.sessions.map { it.id.raw }, "关闭按钮只关它自己那一条")
        onNodeWithTag(tag(rawA)).assertDoesNotExist()
        onNodeWithTag(tag(rawB)).assertExists()
    }

    /**
     * 中键点击 = 关闭（需求域 3 第 5 条）。
     *
     * 可测性经查证成立：ui-test 的 MouseInjectionScope.press(MouseButton.Tertiary) 经
     * InputDispatcher.enqueueMousePress(2) → PointerButton(2) 下发，与产品侧判定的
     * event.buttons.isTertiaryPressed 是同一条链路（按键掩码 Tertiary = 1 shl 2）。
     *
     * 平台注记（CMP 1.12.1 ui-test）：press 从光标当前位置注入，performMouseInput
     * 不会自动移到节点中心（实测事件落在窗口原点），必须先 moveTo(center)。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun middleClickClosesTab() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag(tag(rawA)).performMouseInput {
            moveTo(center)
            press(androidx.compose.ui.test.MouseButton.Tertiary)
            release(androidx.compose.ui.test.MouseButton.Tertiary)
        }

        assertEquals(listOf(rawB), m.registry.sessions.map { it.id.raw }, "中键点击标签必须关闭该标签")
    }

    // ---- §5.1 关闭活动标签 -> 左邻；关最后一个 -> 空态 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingActiveTabActivatesLeftNeighbour() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)
        assertEquals(rawB, m.registry.activeId?.raw)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag("session-tab-close-$rawB").performClick()

        assertEquals(rawA, m.registry.activeId?.raw, "关闭活动标签必须落到左邻")
        onNodeWithTag(tag(rawA)).assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingLastTabReturnsToEmptyState() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag("session-tab-close-$rawA").performClick()

        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTag(tag(rawA)).fetchSemanticsNodes().size)
    }

    // ---- §4.5：无进行中传输时不弹确认（有进行中传输的确认框见终报遗留项） ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingTabWithoutRunningTransfersShowsNoConfirmDialog() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag("session-tab-close-$rawA").performClick()

        assertEquals(
            emptyList(),
            onAllNodesWithTag("session-close-confirm").fetchSemanticsNodes().map { it.id },
            "无可损失的进行中传输时关闭标签不得弹确认框（spec §5.2 Q7）",
        )
    }

    // ---- §4.1 右侧：+ 可用并跳服务器列表；助手仍是禁用占位 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun plusButtonNavigatesToServersList() = runComposeUiTest {
        val m = model()
        m.navigate(Destination.TERMINAL)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithContentDescription("新建终端").performClick()

        assertEquals(Destination.SERVERS, m.current, "「+」必须回到服务器列表让用户选机（spec §4.1 决策 Q5）")
    }

    // ---- §4.4 重挂提示 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun switchingTabsShowsRestartNotice() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        // 未发生切换：不提示
        assertEquals(0, onAllNodesWithTag("terminal-restart-notice").fetchSemanticsNodes().size)

        onNodeWithTag(tag(rawA)).performClick()

        onNodeWithTag("terminal-restart-notice").assertIsDisplayed()
        onNodeWithText("会话已重新打开：服务端会新起一个 shell，历史滚动缓冲不可保留。")
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun restartNoticeIsDismissedOnNextSwitchAndNotShownAgainForSameSession() = runComposeUiTest {
        val m = model()
        val rawA = m.safeSession(web01)
        val rawB = m.safeSession(db01)

        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithTag(tag(rawA)).performClick()          // A：一次性提示
        onNodeWithTag("terminal-restart-notice").assertIsDisplayed()

        onNodeWithTag(tag(rawB)).performClick()          // B：提示重新给出
        onNodeWithTag("terminal-restart-notice").assertIsDisplayed()

        onNodeWithTag(tag(rawA)).performClick()          // 回到 A：已提示过，不再骚扰
        assertEquals(
            0,
            onAllNodesWithTag("terminal-restart-notice").fetchSemanticsNodes().size,
            "同一条会话只提示一次，反复切换不得重复弹同一条提示",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun singleSessionNeverShowsRestartNotice() = runComposeUiTest {
        val m = model()
        m.safeSession(web01)

        setContent { BareZenTheme { TerminalScreen(m) } }

        assertTrue(
            onAllNodesWithTag("terminal-restart-notice").fetchSemanticsNodes().isEmpty(),
            "没有任何切换就不该出现重挂提示——不能给用户一份没有发生的坏消息",
        )
    }
}
