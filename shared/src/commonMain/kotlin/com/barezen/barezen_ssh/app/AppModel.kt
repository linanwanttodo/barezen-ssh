package com.barezen.barezen_ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.ServerRepository
import kotlinx.coroutines.CoroutineScope

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

    fun navigate(to: Destination) { current = to }
    fun refreshServers() { servers = repo.list() }

    companion object {
        fun forUiTest(): AppModel = AppModel(
            repo = object : ServerRepository { override fun list(): List<Server> = emptyList() },
            ssh = object : SshClient {},
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
    }
}
