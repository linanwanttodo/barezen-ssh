package com.barezen.barezen_ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.ServerRepository
import com.barezen.barezen_ssh.servers.filterServers
import kotlinx.coroutines.CoroutineScope
import kotlin.jvm.JvmName

class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    private val scope: CoroutineScope,
) {
    var current: Destination by mutableStateOf(Destination.SERVERS)
        private set
    var servers: List<Server> by mutableStateOf(repo.list())
        private set
    var connection: ConnectionState by mutableStateOf(ConnectionState.Disconnected)
        private set
    var query: String by mutableStateOf("")
    var selectedTag: String? by mutableStateOf(null)
    val visibleServers: List<Server> get() = filterServers(servers, query, selectedTag)

    fun navigate(to: Destination) { current = to }
    fun refreshServers() { servers = repo.list() }
    // @JvmName：避免与 var query 的 setter 在 JVM 上签名冲突（platform declaration clash）
    @JvmName("setQueryValue")
    fun setQuery(value: String) { query = value }
    fun selectTag(value: String?) { selectedTag = value }
    fun saveServer(server: Server) { repo.upsert(server); refreshServers() }
    fun removeServer(id: String) { repo.delete(id); refreshServers() }

    companion object {
        fun forUiTest(): AppModel = AppModel(
            repo = object : ServerRepository {
                override fun list(): List<Server> = emptyList()
                override fun upsert(server: Server) {}
                override fun delete(id: String) {}
            },
            ssh = object : SshClient {},
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
    }
}
