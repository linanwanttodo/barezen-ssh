// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/MacKeychainStoreTest.kt
package com.barezen.ssh.credentials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MacKeychainStore（security 子进程封装）穷举测试。
 * 秘密经 stdin 传入（-w 不带值），避免出现在进程列表里。
 */
class MacKeychainStoreTest {

    // ---- save ----

    @Test
    fun saveAssemblesAddGenericPasswordWithSecretOnStdin() {
        val runner = FakeCommandRunner().enqueue(0)
        MacKeychainStore(runner).save("server/s1/password", "mac-secret".toCharArray())
        assertEquals(
            listOf("security", "add-generic-password", "-U", "-s", "BareZen-SSH", "-a", "server/s1/password", "-w"),
            runner.lastCommand(),
        )
        assertEquals("mac-secret", runner.lastStdin())
    }

    @Test
    fun saveThrowsOnNonZeroExit() {
        val runner = FakeCommandRunner().enqueue(45, stderr = "The specified item already exists.")
        val e = assertFailsWith<CredentialStoreException> {
            MacKeychainStore(runner).save("server/s1/password", "x".toCharArray())
        }
        assertTrue(e.message!!.contains("45"))
    }

    // ---- load ----

    @Test
    fun loadAssemblesFindGenericPasswordAndStripsTrailingNewline() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "key-pass\n")
        val loaded = MacKeychainStore(runner).load("server/s1/keypass")
        assertEquals(
            listOf("security", "find-generic-password", "-s", "BareZen-SSH", "-a", "server/s1/keypass", "-w"),
            runner.lastCommand(),
        )
        assertContentEquals("key-pass".toCharArray(), loaded)
    }

    @Test
    fun loadReturnsNullOnNonZeroExit() {
        val runner = FakeCommandRunner().enqueue(44, stderr = "could not be found")
        assertNull(MacKeychainStore(runner).load("server/s1/password"))
    }

    @Test
    fun loadReturnsNullOnEmptyOutput() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "")
        assertNull(MacKeychainStore(runner).load("server/s1/password"))
    }

    @Test
    fun loadKeepsSecretWithSpecialCharactersIntact() {
        val runner = FakeCommandRunner().enqueue(0, stdout = "p@ss'w\"ord\\!$#")
        assertContentEquals("p@ss'w\"ord\\!$#".toCharArray(), MacKeychainStore(runner).load("id"))
    }

    // ---- delete ----

    @Test
    fun deleteAssemblesDeleteGenericPassword() {
        val runner = FakeCommandRunner().enqueue(0)
        MacKeychainStore(runner).delete("server/s1/password")
        assertEquals(
            listOf("security", "delete-generic-password", "-s", "BareZen-SSH", "-a", "server/s1/password"),
            runner.lastCommand(),
        )
    }

    @Test
    fun deleteThrowsOnNonZeroExit() {
        val runner = FakeCommandRunner().enqueue(44)
        assertFailsWith<CredentialStoreException> {
            MacKeychainStore(runner).delete("server/s1/password")
        }
    }

    // ---- isAvailable ----

    @Test
    fun availableWhenSecurityToolOnPath() {
        val runner = FakeCommandRunner().enqueue(0)
        assertTrue(MacKeychainStore(runner).isAvailable())
        assertEquals(listOf("which", "security"), runner.lastCommand())
    }

    @Test
    fun unavailableWhenSecurityToolMissing() {
        val runner = FakeCommandRunner().enqueue(1)
        assertFalse(MacKeychainStore(runner).isAvailable())
    }
}
