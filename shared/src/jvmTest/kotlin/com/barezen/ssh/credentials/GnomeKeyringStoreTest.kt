// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/GnomeKeyringStoreTest.kt
package com.barezen.ssh.credentials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GnomeKeyringStore（secret-tool 子进程封装）穷举测试：
 * 命令拼装、stdin 传递、成功/非零退出/空输出/多行输出。
 * 沙箱无 D-Bus，真实调用路径不在测试范围。
 */
class GnomeKeyringStoreTest {

    private fun storeWithDbus(runner: FakeCommandRunner): GnomeKeyringStore =
        GnomeKeyringStore(runner, mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/tmp/fake-bus"))

    private fun storeNoDbus(runner: FakeCommandRunner): GnomeKeyringStore =
        GnomeKeyringStore(runner, emptyMap())

    // ---- save ----

    @Test
    fun saveAssemblesSecretToolStoreCommandWithSecretOnStdin() {
        val runner = FakeCommandRunner().enqueue(0)
        storeWithDbus(runner).save("server/s1/password", "p@ss word".toCharArray())
        assertEquals(
            listOf("secret-tool", "store", "--label=BareZen-SSH", "barezen-id", "server/s1/password"),
            runner.lastCommand(),
        )
        assertEquals("p@ss word", runner.lastStdin())
    }

    @Test
    fun saveThrowsOnNonZeroExitWithStderrDetail() {
        val runner = FakeCommandRunner().enqueue(1, stderr = "cannot create item")
        val e = assertFailsWith<CredentialStoreException> {
            storeWithDbus(runner).save("server/s1/password", "x".toCharArray())
        }
        assertTrue(e.message!!.contains("1"))
        assertTrue(e.message!!.contains("cannot create item"))
    }

    // ---- load ----

    @Test
    fun loadAssemblesLookupCommandAndReturnsSecret() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "s3cret\n")
        val loaded = storeWithDbus(runner).load("server/s1/keypass")
        assertEquals(
            listOf("secret-tool", "lookup", "barezen-id", "server/s1/keypass"),
            runner.lastCommand(),
        )
        assertContentEquals("s3cret".toCharArray(), loaded)
    }

    @Test
    fun loadKeepsInnerNewlinesOnlyStripsTrailingOne() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "line1\nline2\n")
        val loaded = storeWithDbus(runner).load("server/s1/password")
        assertContentEquals("line1\nline2".toCharArray(), loaded)
    }

    @Test
    fun loadReturnsNullOnNonZeroExit() {
        val runner = FakeCommandRunner().enqueue(1, stderr = "not found")
        assertNull(storeWithDbus(runner).load("server/s1/password"))
    }

    @Test
    fun loadReturnsNullOnEmptyOutput() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "")
        assertNull(storeWithDbus(runner).load("server/s1/password"))
    }

    // ---- delete ----

    @Test
    fun deleteAssemblesClearCommand() {
        val runner = FakeCommandRunner().enqueue(0)
        storeWithDbus(runner).delete("server/s1/password")
        assertEquals(
            listOf("secret-tool", "clear", "barezen-id", "server/s1/password"),
            runner.lastCommand(),
        )
    }

    @Test
    fun deleteThrowsOnNonZeroExit() {
        val runner = FakeCommandRunner().enqueue(2, stderr = "denied")
        val e = assertFailsWith<CredentialStoreException> {
            storeWithDbus(runner).delete("server/s1/password")
        }
        assertTrue(e.message!!.contains("denied"))
    }

    // ---- isAvailable ----

    @Test
    fun unavailableWhenDbusEnvMissingEvenIfToolExists() {
        val runner = FakeCommandRunner().enqueue(0)
        assertFalse(storeNoDbus(runner).isAvailable())
        assertEquals(0, runner.commandCount())
    }

    @Test
    fun availableWhenDbusPresentAndToolOnPath() {
        val runner = FakeCommandRunner().enqueue(0)
        assertTrue(storeWithDbus(runner).isAvailable())
        assertEquals(listOf("which", "secret-tool"), runner.lastCommand())
    }

    @Test
    fun unavailableWhenToolNotOnPath() {
        val runner = FakeCommandRunner().enqueue(1)
        assertFalse(storeWithDbus(runner).isAvailable())
    }
}
