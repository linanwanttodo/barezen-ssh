// shared/src/commonMain/kotlin/com/barezen/ssh/ssh/Ssh.kt
package com.barezen.ssh.ssh

import com.barezen.ssh.servers.Server

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: Server) : ConnectionState
    data class Connected(val server: Server, val latencyMs: Long) : ConnectionState
    data class Failed(val server: Server, val message: String) : ConnectionState
}

sealed interface AuthMethod {
    data class Password(val value: String) : AuthMethod
    data class PrivateKey(val keyPath: String, val passphrase: String? = null) : AuthMethod
}

data class ConnectRequest(val host: String, val port: Int, val user: String, val auth: AuthMethod)

interface ShellChannel {
    fun write(bytes: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun close()
}

interface SshSession {
    fun pingMs(): Long
    fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel
    fun close()
}

interface SshClient {
    suspend fun connect(request: ConnectRequest): SshSession
}
