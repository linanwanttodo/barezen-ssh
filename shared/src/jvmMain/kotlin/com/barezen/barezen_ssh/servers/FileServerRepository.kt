package com.barezen.barezen_ssh.servers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileServerRepository(
    private val file: File = File(System.getProperty("user.home"), ".barezen/servers.json"),
) : ServerRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()

    override fun list(): List<Server> = synchronized(lock) { read() }
    override fun upsert(server: Server) = synchronized(lock) {
        val cur = read().filterNot { it.id == server.id } + server
        write(cur)
    }
    override fun delete(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }

    private fun read(): List<Server> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString<List<Server>>(file.readText()) }.getOrDefault(emptyList())

    private fun write(servers: List<Server>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(servers))
    }
}
