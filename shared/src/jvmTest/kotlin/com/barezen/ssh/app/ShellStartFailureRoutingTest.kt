// shared/src/jvmTest/kotlin/com/barezen/ssh/app/ShellStartFailureRoutingTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.servers.InMemoryServerRepository
import com.barezen.ssh.servers.Server
import com.barezen.ssh.settings.NoopSettingsRepository
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.FakeSshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * shell 启动失败必须落到**上报的那条会话**上，而不是「当前活动会话」。
 *
 * 真实的错位窗口：用户在标签 B 上启动 shell 失败，回传前先切到了标签 A——
 * 若失败按活动会话落位，会把用户正在使用的 A 关掉，B 却仍停在 Connected。
 */
class ShellStartFailureRoutingTest {

    private val a = Server("srv-a", "web-01", "127.0.0.1", 22, "root")
    private val b = Server("srv-b", "db-01", "127.0.0.1", 22, "root")

    private fun model(sessionA: FakeSshSession, sessionB: FakeSshSession) = AppModel(
        repo = InMemoryServerRepository(listOf(a, b)),
        ssh = QueuedSshClient(listOf(sessionA, sessionB)),
        scope = CoroutineScope(Dispatchers.Unconfined),
        settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
    )

    @Test fun failureLandsOnReportedSessionNotOnActiveOne() = runBlocking {
        val sessionA = FakeSshSession()
        val sessionB = FakeSshSession()
        val m = model(sessionA, sessionB)
        m.startConnect(a, AuthMethod.Password("x"))
        m.startConnect(b, AuthMethod.Password("x"))
        val idA = m.registry.sessions[0].id
        val idB = m.registry.sessions[1].id
        assertEquals(idB, m.registry.activeId, "后连的会话是前台")

        // B 的 shell 启动失败，但回传前用户切回了 A
        m.registry.activate(idA)
        m.reportShellStartFailed(idB, "shell 启动失败：权限不足")

        val snapshotA = m.registry.sessions.first { it.id == idA }
        val snapshotB = m.registry.sessions.first { it.id == idB }
        assertEquals(
            ConnectionState.Connected(a, 1L),
            snapshotA.state,
            "上报的失败不得波及用户当前正在使用的会话",
        )
        assertSame(sessionA, snapshotA.session)
        val failedB = assertIs<ConnectionState.Failed>(snapshotB.state)
        assertEquals("shell 启动失败：权限不足", failedB.message)
        assertNull(snapshotB.session, "失败会话不得保留底层连接")
        assertEquals(true, sessionB.closed, "失败会话的底层连接必须真的关掉")
    }

    @Test fun unknownSessionIdIsIgnoredWithoutTouchingAnySession() = runBlocking {
        val sessionA = FakeSshSession()
        val m = AppModel(
            repo = InMemoryServerRepository(listOf(a)),
            ssh = QueuedSshClient(listOf(sessionA)),
            scope = CoroutineScope(Dispatchers.Unconfined),
            settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        )
        m.startConnect(a, AuthMethod.Password("x"))

        m.reportShellStartFailed(SessionId(999L), "shell 启动失败")

        assertEquals(ConnectionState.Connected(a, 1L), m.registry.sessions.single().state)
        assertSame(sessionA, m.registry.sessions.single().session)
    }
}
