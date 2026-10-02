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

/** 单条远程命令的执行结果；exitCode 为 null 表示超时或通道异常，退出状态不可信。 */
data class ExecResult(val exitCode: Int?, val stdout: String, val stderr: String)

interface SshSession {
    fun pingMs(): Long
    /** 阻塞执行一条远程命令；stdout/stderr 均按 UTF-8 解码。 */
    fun exec(command: String, timeoutMs: Long = 10_000L): ExecResult
    fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel
    fun close()
}

interface SshClient {
    suspend fun connect(request: ConnectRequest): SshSession
}
