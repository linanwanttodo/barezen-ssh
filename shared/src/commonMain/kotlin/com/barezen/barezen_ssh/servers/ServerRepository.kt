// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/servers/ServerRepository.kt
package com.barezen.barezen_ssh.servers

interface ServerRepository {
    fun list(): List<Server>
    fun upsert(server: Server)
    fun delete(id: String)
}

/** 内存实现，测试/UI 原型用；线程安全（synchronized 保护内部 MutableList）。 */
class InMemoryServerRepository(initial: List<Server> = emptyList()) : ServerRepository {
    private val servers: MutableList<Server> = initial.toMutableList()

    override fun list(): List<Server> = synchronized(servers) { servers.toList() }

    override fun upsert(server: Server) {
        synchronized(servers) {
            servers.removeAll { it.id == server.id }
            servers.add(server)
        }
    }

    override fun delete(id: String) {
        synchronized(servers) { servers.removeAll { it.id == id } }
    }
}
