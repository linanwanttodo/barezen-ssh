// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/servers/FileServerRepositoryTest.kt
package com.barezen.barezen_ssh.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
