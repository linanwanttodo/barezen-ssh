// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ssh/Ssh.kt（本任务先建骨架，Task 6 补全实现细节）
package com.barezen.barezen_ssh.ssh

import com.barezen.barezen_ssh.servers.Server

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: Server) : ConnectionState
    data class Connected(val server: Server, val latencyMs: Long) : ConnectionState
    data class Failed(val server: Server, val message: String) : ConnectionState
}

interface SshClient // Task 6 定义方法
