// shared/src/jvmTest/kotlin/com/barezen/ssh/app/HostSessionMigrationTest.kt
package com.barezen.ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 第 3 刀：FilesHost 必须跟随**活动会话**，而不是跟随布尔 connected。
 *
 * 缺陷形状：Host 的 LaunchedEffect 只以布尔 connected 为 key。两条会话**同时 Connected**
 * 时布尔值恒为 true，切换活动会话不会重跑 effect——SFTP 面板会继续绑定旧会话的句柄。
 * 故切换必须发生在组合**之后**，否则测不到该缺陷（同 DashboardScreenTest 的
 * dashboardFollowsActiveSessionOnSwitch）。
 */
class HostSessionMigrationTest {

    /** 会话语义替身：SFTP 可观测（记录被打开次数，Host 重建模型时 +1）。 */
    private class HostSession(private val label: String) : SshSession {
        /** 每个 SftpFs 实例对应一次 Host 建模型；文件名带会话标签，可区分数据源。 */
        val sftpNames = mutableListOf<String>()

        override fun pingMs(): Long = 1L
        override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
        override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
            error("本测试不挂终端")

        override fun newSftp(): SftpFs {
            val name = "$label.txt"
            sftpNames += name
            return object : SftpFs {
                override fun list(dir: String): List<SftpEntry> =
                    listOf(SftpEntry(name, isDirectory = false, size = 1L, mtimeMs = 0L))

                override fun mkdir(dir: String) = Unit
                override fun delete(path: String) = Unit
                override fun rename(oldPath: String, newPath: String) = Unit
                override fun download(remotePath: String, onChunk: (ByteArray) -> Unit): Long = 0L
                override fun upload(remotePath: String, size: Long, nextChunk: (offset: Long) -> ByteArray?) = Unit
                override fun close() = Unit
            }
        }

        override fun startForward(spec: ForwardSpec): ForwardTunnel = error("本测试不建隧道")
        override fun close() = Unit
    }

    private val web01 = Server("srv-a", "web-01", "127.0.0.1", 22, "root")
    private val db01 = Server("srv-b", "db-01", "127.0.0.1", 22, "root")

    private fun model(sessionA: SshSession, sessionB: SshSession) = AppModel(
        repo = InMemoryServerRepository(listOf(web01, db01)),
        ssh = QueuedSshClient(listOf(sessionA, sessionB)),
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    @OptIn(ExperimentalTestApi::class)
    @Test fun filesHostShowsActiveSessionListing() = runComposeUiTest {
        val sessionA = HostSession("a")
        val sessionB = HostSession("b")
        val m = model(sessionA, sessionB)
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.FILES)
        setContent { BareZenTheme { BareZenAppContent(m) } }

        // A 是活动会话：文件页列出 A 的条目（未连接提示消失）
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("a.txt").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 判别用例：组合后新开 B 并切为活动会话，文件页必须改列 B 的条目。
     *
     * 只以布尔 connected 为 key 的实现下，A、B 同时 Connected 使布尔恒为 true，
     * effect 不重跑——文件页仍列 a.txt，本断言失败。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test fun filesHostFollowsActiveSessionOnSwitch() = runComposeUiTest {
        val sessionA = HostSession("a")
        val sessionB = HostSession("b")
        val m = model(sessionA, sessionB)
        m.startConnect(web01, AuthMethod.Password("x"))
        m.navigate(Destination.FILES)
        setContent { BareZenTheme { BareZenAppContent(m) } }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("a.txt").fetchSemanticsNodes().isNotEmpty()
        }

        // 组合之后：新建第二条会话并切为活动会话（A 仍处 Connected，布尔 connected 不变）
        m.startConnect(db01, AuthMethod.Password("x"))
        m.navigate(Destination.FILES)

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("b.txt").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            emptyList(),
            onAllNodesWithText("a.txt").fetchSemanticsNodes().map { it.id },
            "切换到活动会话 B 后不得仍列出会话 A 的目录",
        )
    }

    /** 关闭活动会话后活动会话落到仍连接的 A：Host 必须重建，数据源回到 a.txt。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun filesHostRebuildsOnActiveSessionClose() = runComposeUiTest {
        val sessionA = HostSession("a")
        val sessionB = HostSession("b")
        val m = model(sessionA, sessionB)
        m.startConnect(web01, AuthMethod.Password("x"))
        m.startConnect(db01, AuthMethod.Password("x"))
        m.navigate(Destination.FILES)
        setContent { BareZenTheme { BareZenAppContent(m) } }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("b.txt").fetchSemanticsNodes().isNotEmpty()
        }

        // 关闭活动会话 B：活动会话回到仍连接的 A——布尔 connected 全程 true
        val activeId = m.registry.activeId ?: error("应有活动会话")
        m.registry.close(activeId)

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("a.txt").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
