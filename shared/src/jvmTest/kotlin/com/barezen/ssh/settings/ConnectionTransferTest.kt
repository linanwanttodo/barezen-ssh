// shared/src/jvmTest/kotlin/com/barezen/ssh/settings/ConnectionTransferTest.kt
package com.barezen.ssh.settings

import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionTransferTest {

    private fun sample() = listOf(
        Server("id-1", "web-01", "10.0.0.11", 22, "root", listOf("生产"), StoredAuth.Key("/home/u/.ssh/id_ed25519")),
        Server("id-2", "db,01", "10.0.0.12", 2222, "admin", emptyList(), StoredAuth.Password),
    )

    @Test fun jsonExportOmitsKeyPathByDefault() {
        val text = ConnectionTransfer.exportJson(sample(), includeKeyPath = false)
        assertFalse(text.contains("/home/u/.ssh/id_ed25519"), "默认导出不得含私钥路径：\n$text")
        assertTrue(text.contains("\"key\""))
    }

    @Test fun jsonExportIncludesKeyPathWhenRequested() {
        val text = ConnectionTransfer.exportJson(sample(), includeKeyPath = true)
        assertTrue(text.contains("/home/u/.ssh/id_ed25519"))
    }

    @Test fun csvStartsWithUtf8Bom() {
        val bytes = ConnectionTransfer.exportCsv(sample()).toByteArray(Charsets.UTF_8)
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
    }

    @Test fun csvHasExpectedHeaderAndEscapes() {
        val csv = ConnectionTransfer.exportCsv(sample())
        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals("名称,地址,端口,用户名,标签,认证方式", lines[0])
        assertTrue(lines[1].contains("web-01"))
        // 名称里含逗号 → 必须被引号包裹
        assertTrue(lines.any { it.contains("\"db,01\"") }, "含逗号的字段需被引号包裹：\n$csv")
    }

    @Test fun jsonRoundTripRegeneratesIds() {
        val exported = ConnectionTransfer.exportJson(sample(), includeKeyPath = true)
        val imported = ConnectionTransfer.importJson(exported)
        assertEquals(2, imported.size)
        assertEquals(setOf("web-01", "db,01"), imported.map { it.name }.toSet())
        assertTrue(imported.none { it.id in setOf("id-1", "id-2") }, "导入必须重新生成 id")
    }

    @Test fun importDedupesByHostPortUser() {
        val existing = listOf(sample()[0])
        val incoming = ConnectionTransfer.importJson(
            ConnectionTransfer.exportJson(sample(), includeKeyPath = false),
        )
        val r = ConnectionTransfer.merge(existing, incoming)
        assertEquals(1, r.added.size)                       // 只剩 db,01
        assertEquals(1, r.skipped.count { it is SkipReason.Duplicate })
    }
}
