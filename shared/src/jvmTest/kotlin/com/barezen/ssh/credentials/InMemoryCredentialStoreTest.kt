// shared/src/jvmTest/kotlin/com/barezen/ssh/credentials/InMemoryCredentialStoreTest.kt
package com.barezen.ssh.credentials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryCredentialStoreTest {

    @Test
    fun saveThenLoadRoundTrips() {
        val store = InMemoryCredentialStore()
        store.save("server/s1/password", "abc".toCharArray())
        assertContentEquals("abc".toCharArray(), store.load("server/s1/password"))
    }

    @Test
    fun loadMissingReturnsNull() {
        assertNull(InMemoryCredentialStore().load("server/none/password"))
    }

    @Test
    fun deleteRemovesEntry() {
        val store = InMemoryCredentialStore()
        store.save("server/s1/password", "abc".toCharArray())
        store.delete("server/s1/password")
        assertNull(store.load("server/s1/password"))
    }

    @Test
    fun deleteMissingIsNoOp() {
        InMemoryCredentialStore().delete("server/none/password")
    }

    @Test
    fun overwriteReplacesValue() {
        val store = InMemoryCredentialStore()
        store.save("server/s1/password", "old".toCharArray())
        store.save("server/s1/password", "new".toCharArray())
        assertContentEquals("new".toCharArray(), store.load("server/s1/password"))
    }

    @Test
    fun storedSecretIsDefensivelyCopiedOnSave() {
        val store = InMemoryCredentialStore()
        val secret = "abc".toCharArray()
        store.save("server/s1/password", secret)
        secret.fill('x')
        assertContentEquals("abc".toCharArray(), store.load("server/s1/password"))
    }

    @Test
    fun loadedSecretIsDefensivelyCopiedOnLoad() {
        val store = InMemoryCredentialStore()
        store.save("server/s1/password", "abc".toCharArray())
        store.load("server/s1/password")!!.fill('x')
        assertContentEquals("abc".toCharArray(), store.load("server/s1/password"))
    }

    @Test
    fun keyAndPasswordAndKeypassIdsAreIndependent() {
        val store = InMemoryCredentialStore()
        store.save("server/s1/password", "p".toCharArray())
        store.save("server/s1/keypass", "k".toCharArray())
        assertContentEquals("p".toCharArray(), store.load("server/s1/password"))
        assertContentEquals("k".toCharArray(), store.load("server/s1/keypass"))
        store.delete("server/s1/password")
        assertNull(store.load("server/s1/password"))
        assertContentEquals("k".toCharArray(), store.load("server/s1/keypass"))
    }

    @Test
    fun isAvailableAlwaysTrue() {
        assertTrue(InMemoryCredentialStore().isAvailable())
        assertFalse(InMemoryCredentialStore().isAvailable().not())
    }
}
