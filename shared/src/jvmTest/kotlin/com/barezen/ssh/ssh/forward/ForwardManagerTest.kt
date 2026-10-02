// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/forward/ForwardManagerTest.kt
package com.barezen.ssh.ssh.forward

import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.servers.ForwardRule
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class FakeTunnel(override val spec: ForwardSpec) : ForwardTunnel {
    var closed = false
    override fun close() {
        closed = true
    }
}

private class StubFactory {
    val created = mutableListOf<ForwardSpec>()
    var failOn: ((ForwardSpec) -> Boolean)? = null

    fun tunnelFactory(): suspend (ForwardSpec) -> ForwardTunnel = { spec ->
        created.add(spec)
        if (failOn?.invoke(spec) == true) {
            throw IllegalStateException("端口被占用")
        }
        FakeTunnel(spec)
    }
}

class ForwardManagerTest {

    private fun spec(port: Int, kind: ForwardKind = ForwardKind.LOCAL) =
        ForwardSpec(kind, port, "db.internal", 5432)

    @Test
    fun applySuccessProducesActiveEntryWithTunnel() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val entry = manager.apply(spec(18081))
        assertEquals(ForwardEntryState.ACTIVE, entry.state)
        assertNull(entry.message)
        assertNotNull(entry.tunnel)
        assertEquals(spec(18081), entry.spec)
        assertEquals(listOf(entry), manager.entries.value)
    }

    @Test
    fun applyIsIdempotentForActiveDuplicate() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val first = manager.apply(spec(18081))
        val second = manager.apply(spec(18081))
        assertSame(first, second)
        assertEquals(1, stub.created.size)
        assertEquals(1, manager.entries.value.size)
    }

    @Test
    fun differentSpecsAreSeparateEntries() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        manager.apply(spec(18081))
        manager.apply(spec(18082))
        manager.apply(spec(18081, ForwardKind.REMOTE))
        assertEquals(3, manager.entries.value.size)
    }

    @Test
    fun applyFailureRecordsFailedEntryWithMessage() = runBlocking {
        val stub = StubFactory().apply { failOn = { it.bindPort == 18099 } }
        val manager = ForwardManager(stub.tunnelFactory())
        val entry = manager.apply(spec(18099))
        assertEquals(ForwardEntryState.FAILED, entry.state)
        assertEquals("端口被占用", entry.message)
        assertNull(entry.tunnel)
    }

    @Test
    fun reapplyAfterFailureRetriesFactory() = runBlocking {
        val stub = StubFactory().apply { failOn = { it.bindPort == 18099 } }
        val manager = ForwardManager(stub.tunnelFactory())
        manager.apply(spec(18099))
        stub.failOn = null
        val retried = manager.apply(spec(18099))
        assertEquals(ForwardEntryState.ACTIVE, retried.state)
        assertEquals(1, manager.entries.value.size)
        assertEquals(2, stub.created.size)
    }

    @Test
    fun removeClosesTunnelAndDropsEntry() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val entry = manager.apply(spec(18081))
        val tunnel = entry.tunnel as FakeTunnel
        manager.remove(entry)
        assertTrue(tunnel.closed)
        assertTrue(manager.entries.value.isEmpty())
    }

    @Test
    fun removeIsIdentitySafeAndRepeatable() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val a = manager.apply(spec(18081))
        val b = manager.apply(spec(18082))
        manager.remove(a)
        manager.remove(a)
        assertEquals(listOf(b), manager.entries.value)
    }

    @Test
    fun closeAllClosesEveryTunnelAndClears() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val a = manager.apply(spec(18081))
        val b = manager.apply(spec(18082, ForwardKind.REMOTE))
        manager.closeAll()
        assertTrue((a.tunnel as FakeTunnel).closed)
        assertTrue((b.tunnel as FakeTunnel).closed)
        assertTrue(manager.entries.value.isEmpty())
    }

    @Test
    fun failedEntryHasNoTunnelToLeak() = runBlocking {
        val stub = StubFactory().apply { failOn = { true } }
        val manager = ForwardManager(stub.tunnelFactory())
        val entry = manager.apply(spec(18099))
        assertNull(entry.tunnel)
        assertIs<ForwardEntry>(entry)
        assertFalse(entry.state == ForwardEntryState.ACTIVE)
    }

    @Test
    fun applyAllEnablesOnlyAutoStartRules() = runBlocking {
        val stub = StubFactory()
        val manager = ForwardManager(stub.tunnelFactory())
        val rules = listOf(
            ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432, autoStart = true),
            ForwardRule(ForwardKind.REMOTE, 18082, "web.local", 80, autoStart = false),
            ForwardRule(ForwardKind.LOCAL, 18083, "cache.internal", 6379, autoStart = true),
        )
        manager.applyAll(rules)
        assertEquals(setOf(18081, 18083), manager.entries.value.map { it.spec.bindPort }.toSet())
        assertEquals(2, stub.created.size)
    }

    @Test
    fun applyAllContinuesWhenOneRuleFails() = runBlocking {
        val stub = StubFactory().apply { failOn = { it.bindPort == 18082 } }
        val manager = ForwardManager(stub.tunnelFactory())
        val rules = listOf(
            ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432),
            ForwardRule(ForwardKind.REMOTE, 18082, "web.local", 80),
            ForwardRule(ForwardKind.LOCAL, 18083, "cache.internal", 6379),
        )
        manager.applyAll(rules)
        val byPort = manager.entries.value.associateBy { it.spec.bindPort }
        assertEquals(ForwardEntryState.ACTIVE, byPort[18081]!!.state)
        assertEquals(ForwardEntryState.FAILED, byPort[18082]!!.state)
        assertEquals(ForwardEntryState.ACTIVE, byPort[18083]!!.state)
    }
}
