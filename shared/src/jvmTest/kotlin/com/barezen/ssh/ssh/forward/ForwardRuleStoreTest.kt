// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/forward/ForwardRuleStoreTest.kt
package com.barezen.ssh.ssh.forward

import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.servers.FileForwardRuleStore
import com.barezen.ssh.servers.ForwardRule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForwardRuleStoreTest {

    private fun tempFile(): File =
        Files.createTempDirectory("barezen-forward-rules").resolve("forwards.json").toFile()

    @Test
    fun missingFileYieldsEmptyList() {
        assertEquals(emptyList(), FileForwardRuleStore(tempFile()).list())
    }

    @Test
    fun upsertThenListRoundTripsAllFields() {
        val file = tempFile()
        val store = FileForwardRuleStore(file)
        val rule = ForwardRule(ForwardKind.REMOTE, 18082, "web.local", 80, autoStart = false)
        store.upsert(rule)
        assertEquals(listOf(rule), FileForwardRuleStore(file).list())
    }

    @Test
    fun upsertReplacesSameRuleKeepingLatestAutoStart() {
        val store = FileForwardRuleStore(tempFile())
        store.upsert(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432, autoStart = false))
        store.upsert(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432, autoStart = true))
        val rules = store.list()
        assertEquals(1, rules.size)
        assertTrue(rules.first().autoStart)
    }

    @Test
    fun deleteRemovesOnlyTargetRule() {
        val store = FileForwardRuleStore(tempFile())
        val keep = ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432)
        val drop = ForwardRule(ForwardKind.LOCAL, 18082, "web.local", 80)
        store.upsert(keep)
        store.upsert(drop)
        store.delete(drop)
        assertEquals(listOf(keep), store.list())
    }

    @Test
    fun deleteMissingRuleIsNoOp() {
        val store = FileForwardRuleStore(tempFile())
        store.delete(ForwardRule(ForwardKind.LOCAL, 1, "h", 2))
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun corruptFileIsQuarantinedOnWriteNotSilentlyDropped() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeText("{ not valid json")
        val store = FileForwardRuleStore(file)
        assertEquals(emptyList(), store.list())
        store.upsert(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432))
        // 坏文件被留存副本，新规则正常落盘
        assertTrue(
            file.parentFile.listFiles()!!.any { it.name.startsWith("forwards.json.corrupt-") },
        )
        assertEquals(
            listOf(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432)),
            store.list(),
        )
    }

    @Test
    fun unreadableFileReadIsToleratedWithoutSideEffects() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeText("garbage content")
        assertEquals(emptyList(), FileForwardRuleStore(file).list())
        // 只读路径无副作用：不产生 corrupt 副本
        assertNull(
            file.parentFile.listFiles()!!.firstOrNull { it.name.startsWith("forwards.json.corrupt-") },
        )
    }

    @Test
    fun atomicWriteLeavesNoTmpBehind() {
        val file = tempFile()
        val store = FileForwardRuleStore(file)
        store.upsert(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432))
        assertNull(file.parentFile.listFiles()!!.firstOrNull { it.name.endsWith(".tmp") })
    }

    @Test
    fun unknownFieldsInExistingFileAreIgnored() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeText(
            """[{"kind":"LOCAL","bindPort":18081,"targetHost":"db.internal","targetPort":5432,"futureField":1}]""",
        )
        val rules = FileForwardRuleStore(file).list()
        assertEquals(listOf(ForwardRule(ForwardKind.LOCAL, 18081, "db.internal", 5432)), rules)
    }
}
