// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/servers/FileServerRepositoryTest.kt
package com.barezen.barezen_ssh.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileServerRepositoryTest {
    private fun tmpRepo(): Pair<FileServerRepository, File> {
        val f = File.createTempFile("servers", ".json").apply { delete() }
        return FileServerRepository(f) to f
    }

    @Test fun roundTripPersistsAllFields() {
        val (repo, _) = tmpRepo()
        val s = Server("1", "web-01", "10.0.0.11", 2222, "root", listOf("生产"), StoredAuth.Key("/home/u/.ssh/id_ed25519"))
        repo.upsert(s)
        assertEquals(listOf(s), repo.list())
    }

    @Test fun upsertReplacesById() {
        val (repo, _) = tmpRepo()
        repo.upsert(Server("1", "a", "h1", 22, "root"))
        repo.upsert(Server("1", "a-renamed", "h1", 22, "root"))
        assertEquals(1, repo.list().size)
        assertEquals("a-renamed", repo.list().single().name)
    }

    @Test fun deleteRemoves() {
        val (repo, _) = tmpRepo()
        repo.upsert(Server("1", "a", "h1", 22, "root"))
        repo.delete("1")
        assertEquals(emptyList(), repo.list())
    }

    @Test fun corruptFileYieldsEmptyList() {
        val f = File.createTempFile("servers", ".json").apply { writeText("{ not json") }
        assertEquals(emptyList(), FileServerRepository(f).list())
    }

    /**
     * 防数据丢失：文件存在但解析不了时，写入**绝不能原地覆盖**。
     * 原实现的 read() 用 catch-all 返回空列表，随后 upsert 只写入新服务器 →
     * 用户整份 servers.json 被静默抹掉。此处要求把原文件留存到 `.corrupt-*` 兄弟文件。
     */
    @Test fun writePreservesUnreadableFileInsteadOfOverwritingIt() {
        val f = File.createTempFile("servers", ".json").apply { writeText("{ not json") }
        val original = f.readText()
        val repo = FileServerRepository(f)

        repo.upsert(Server("2", "new", "h2", 22, "root"))

        // 新写入生效……
        assertEquals(listOf("2"), repo.list().map { it.id })
        // ……且原内容必须仍能从磁盘找回
        val preserved = f.parentFile.listFiles().orEmpty()
            .any { it.name.startsWith("${f.name}.corrupt-") && it.readText() == original }
        assertTrue(preserved, "不可读的 servers.json 被覆盖销毁；应留存为 .corrupt-* 副本")
    }

    /** 只读路径不得有副作用：list() 不允许改名或改写磁盘。 */
    @Test fun listOnUnreadableFileHasNoSideEffect() {
        val f = File.createTempFile("servers", ".json").apply { writeText("{ not json") }
        val original = f.readText()

        assertEquals(emptyList(), FileServerRepository(f).list())

        assertEquals(original, f.readText(), "list() 不应改写文件")
        assertTrue(
            f.parentFile.listFiles().orEmpty().none { it.name.startsWith("${f.name}.corrupt-") },
            "list() 不应产生 .corrupt-* 副本（只读路径无副作用）",
        )
    }

    /** 原子写：正常写入后不留 .tmp 残渣。 */
    @Test fun writeLeavesNoTempResidue() {
        val (repo, f) = tmpRepo()
        repo.upsert(Server("1", "a", "h1", 22, "root"))
        assertTrue(
            f.parentFile.listFiles().orEmpty().none { it.name.startsWith("${f.name}.tmp") },
            "写入后残留了临时文件",
        )
    }
}
