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

/** 转发类型。动态（SOCKS5）暂不支持，接口不预留。 */
enum class ForwardKind { LOCAL, REMOTE }

/**
 * 转发规则。
 * LOCAL：本机监听 [bindPort]，连接转往远端 [targetHost]:[targetPort]；
 * REMOTE：远端监听 [bindPort]，连接转回本机侧 [targetHost]:[targetPort]。
 */
data class ForwardSpec(
    val kind: ForwardKind,
    val bindPort: Int,
    val targetHost: String,
    val targetPort: Int,
)

/** 活动隧道句柄；[close] 幂等，重复调用无副作用。 */
interface ForwardTunnel {
    val spec: ForwardSpec
    fun close()
}

/** SFTP 目录项；size 为字节，目录恒为 0；mtimeMs 毫秒时间戳。 */
data class SftpEntry(val name: String, val isDirectory: Boolean, val size: Long, val mtimeMs: Long)

/** SFTP 操作失败；message 保留服务器侧语义原样。 */
class SftpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * SFTP 文件系统。全部方法阻塞，调用方负责放到 IO 调度器；路径一律绝对路径。
 * [download]/[upload] 为流式分块，避免大文件驻留内存。
 */
interface SftpFs {
    fun list(dir: String): List<SftpEntry>
    fun mkdir(dir: String)
    /** 删除文件或空目录（目录必须为空，递归删除由调用方逐层执行）。 */
    fun delete(path: String)
    fun rename(oldPath: String, newPath: String)
    /** 流式下载：逐块回调（块大小由实现决定），返回总字节数。 */
    fun download(remotePath: String, onChunk: (ByteArray) -> Unit): Long
    /** 流式上传：按 offset 拉取下一块，返回 null 表示结束。 */
    fun upload(remotePath: String, size: Long, nextChunk: (offset: Long) -> ByteArray?)
    /** 释放底层 SFTP 通道；幂等。 */
    fun close()
}

interface SshSession {
    fun pingMs(): Long
    /** 阻塞执行一条远程命令；stdout/stderr 均按 UTF-8 解码。 */
    fun exec(command: String, timeoutMs: Long = 10_000L): ExecResult
    fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel
    /** 打开 SFTP 文件系统；与 shell/exec 复用同一连接，生命周期由调用方管理。 */
    fun newSftp(): SftpFs
    /** 启动端口转发；本机端口被占用或远端拒绝绑定时抛异常（携带原因）。 */
    fun startForward(spec: ForwardSpec): ForwardTunnel
    fun close()
}

interface SshClient {
    suspend fun connect(request: ConnectRequest): SshSession
}
