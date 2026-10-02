// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/JvmSshClient.kt（Task 6：sshj 实现）
package com.barezen.ssh.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JvmSshClient(
    private val hostKeyVerifier: HostKeyVerifier = TofuHostKeyVerifier(),
) : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession = withContext(Dispatchers.IO) {
        val client = SSHClient().apply {
            addHostKeyVerifier(hostKeyVerifier)
            connectTimeout = 10_000
            // 0.40.0 无 keepAliveInterval 属性（javap 核对）：连接建立前设置 KeepAlive 线程间隔（秒），
            // onConnect 时若 enabled 才会启动心跳线程，故必须在 connect 前配置。
            connection.keepAlive.keepAliveInterval = 15
        }
        try {
            client.connect(request.host, request.port)
            when (val auth = request.auth) {
                is AuthMethod.Password -> client.authPassword(request.user, auth.value)
                is AuthMethod.PrivateKey -> {
                    val keys = auth.passphrase?.let { client.loadKeys(auth.keyPath, it.toCharArray()) }
                        ?: client.loadKeys(auth.keyPath)
                    client.authPublickey(request.user, keys)
                }
            }
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            throw e
        }
        JvmSshSession(client)
    }
}

private class JvmSshSession(private val client: SSHClient) : SshSession {
    @Volatile private var session: Session? = null

    // 会话级锁：sshj SSHClient 单连接上并发开子通道不安全，exec 与 pingMs 串行化。
    private val execLock = Object()

    override fun pingMs(): Long = synchronized(execLock) {
        val started = System.nanoTime()
        // 0.40.0 的 Session.Command 无 waitFor()（javap 核对）：以有界 join 等待通道关闭。
        client.startSession().use { s ->
            s.exec("true").use { cmd -> cmd.join(10, TimeUnit.SECONDS) }
        }
        (System.nanoTime() - started) / 1_000_000
    }

    override fun exec(command: String, timeoutMs: Long): ExecResult = synchronized(execLock) {
        try {
            client.startSession().use { s ->
                s.exec(command).use { cmd ->
                    // sshj 把通道输出缓存在内存窗口里：先有界 join 等命令结束，再读流不会丢数据；
                    // 超时则关通道强制 EOF，避免读流阶段无限阻塞。
                    val timedOut = try {
                        cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
                        false
                    } catch (e: Exception) {
                        runCatching { cmd.close() }
                        true
                    }
                    val stdout = cmd.inputStream.readBytes().toString(Charsets.UTF_8)
                    val stderr = cmd.errorStream.readBytes().toString(Charsets.UTF_8)
                    val exit: Int? = cmd.exitStatus
                    ExecResult(
                        exitCode = if (timedOut || exit == null) null else exit,
                        stdout = stdout,
                        stderr = stderr,
                    )
                }
            }
        } catch (e: Exception) {
            // startSession/exec 本身失败：命令未执行，退出状态不可得
            ExecResult(null, "", e.message ?: e.toString())
        }
    }

    override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel {
        val s = client.startSession().also { session = it }
        s.allocateDefaultPTY()
        val shell: Session.Shell = s.startShell()
        // Task 7 重构：reader 线程复用 StreamPump（与原内联循环语义一致：EOF→onClosed(null)、异常→onClosed(e)）
        StreamPump(onData).start(shell.inputStream, onClosed)
        return object : ShellChannel {
            override fun write(bytes: ByteArray) {
                shell.outputStream.write(bytes)
                shell.outputStream.flush()
            }

            override fun resize(cols: Int, rows: Int) {
                // Session.Shell#changeWindowDimensions(cols, rows, w, h)，javap 核对存在
                runCatching { shell.changeWindowDimensions(cols, rows, 0, 0) }
            }

            override fun close() {
                runCatching { shell.close() }
                runCatching { session?.close() }
            }
        }
    }

    override fun close() {
        runCatching { session?.close() }
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    override fun newSftp(): SftpFs = JvmSftpFs(client.newSFTPClient())

    override fun startForward(spec: ForwardSpec): ForwardTunnel = when (spec.kind) {
        ForwardKind.LOCAL -> startLocalForward(spec)
        ForwardKind.REMOTE -> startRemoteForward(spec)
    }

    /** 本地转发：sshj 在 ServerSocket 上 accept，逐连接开 direct-tcpip 通道。 */
    private fun startLocalForward(spec: ForwardSpec): ForwardTunnel {
        val server = try {
            ServerSocket(spec.bindPort, 50, InetAddress.getLoopbackAddress())
        } catch (e: IOException) {
            throw IllegalStateException("本地端口 ${spec.bindPort} 绑定失败：${e.message}", e)
        }
        val params = Parameters("127.0.0.1", spec.bindPort, spec.targetHost, spec.targetPort)
        val forwarder = client.newLocalPortForwarder(params, server)
        val worker = Thread({ forwarder.listen() }, "bz-local-fwd-${spec.bindPort}").apply {
            isDaemon = true
            start()
        }
        return LocalForwardTunnel(spec, server, worker)
    }

    /** 远程转发：远端监听 bindPort，入站通道由本侧转连 targetHost:targetPort。 */
    private fun startRemoteForward(spec: ForwardSpec): ForwardTunnel {
        val bound = RemotePortForwarder.Forward(spec.bindPort)
        val remoteForwarder = client.remotePortForwarder
        try {
            remoteForwarder.bind(bound) { channel ->
                // 每个被转发的入站通道开一条本机侧连接并对拷
                val socket = Socket(spec.targetHost, spec.targetPort)
                pumpBidirectional(channel, socket)
            }
        } catch (e: Exception) {
            throw IllegalStateException("远端拒绝绑定端口 ${spec.bindPort}：${e.message}", e)
        }
        return RemoteForwardTunnel(spec, remoteForwarder, bound)
    }

    /** 通道与本地 Socket 的双向对拷：两个守护线程，任一侧 EOF 或异常即双方关闭。 */
    private fun pumpBidirectional(channel: Channel, socket: Socket) {
        fun pump(from: InputStream, to: OutputStream, onClose: () -> Unit) = Thread({
            try {
                from.copyTo(to)
                to.flush()
            } catch (_: Exception) {
                // 通道或 socket 任一侧断开属正常路径，静默收尾
            } finally {
                runCatching(onClose)
            }
        }, "bz-remote-fwd-pump").apply { isDaemon = true; start() }
        pump(channel.inputStream, socket.getOutputStream()) { runCatching { channel.close() }; runCatching { socket.close() } }
        pump(socket.getInputStream(), channel.outputStream) { runCatching { channel.close() }; runCatching { socket.close() } }
    }
}

private class LocalForwardTunnel(
    override val spec: ForwardSpec,
    private val server: ServerSocket,
    private val worker: Thread,
) : ForwardTunnel {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { server.close() }
            runCatching { worker.join(1_000) }
        }
    }
}

private class RemoteForwardTunnel(
    override val spec: ForwardSpec,
    private val forwarder: RemotePortForwarder,
    private val bound: RemotePortForwarder.Forward,
) : ForwardTunnel {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { forwarder.cancel(bound) }
        }
    }
}

/** sshj SFTPClient 适配为 [SftpFs]；底层通道随 [close] 释放。 */
private class JvmSftpFs(private val sftp: SFTPClient) : SftpFs {
    private val closed = AtomicBoolean(false)

    override fun list(dir: String): List<SftpEntry> =
        sftp.ls(dir).map { r ->
            SftpEntry(
                name = r.name,
                isDirectory = r.isDirectory,
                size = if (r.isDirectory) 0L else r.attributes.size,
                mtimeMs = r.attributes.mtime * 1_000L,
            )
        }

    override fun mkdir(dir: String) {
        sftp.mkdir(dir)
    }

    override fun delete(path: String) {
        val isDir = runCatching { sftp.lstat(path).type == FileMode.Type.DIRECTORY }.getOrDefault(false)
        if (isDir) sftp.rmdir(path) else sftp.rm(path)
    }

    override fun rename(oldPath: String, newPath: String) {
        sftp.rename(oldPath, newPath)
    }

    override fun download(remotePath: String, onChunk: (ByteArray) -> Unit): Long =
        sftp.open(remotePath, EnumSet.of(OpenMode.READ)).use { rf ->
            val buf = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val n = rf.read(total, buf, 0, buf.size)
                if (n <= 0) break
                onChunk(buf.copyOf(n))
                total += n
            }
            total
        }

    override fun upload(remotePath: String, size: Long, nextChunk: (Long) -> ByteArray?) =
        sftp.open(remotePath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { rf ->
            var offset = 0L
            while (true) {
                val chunk = nextChunk(offset) ?: break
                if (chunk.isEmpty()) continue
                rf.write(offset, chunk, 0, chunk.size)
                offset += chunk.size
            }
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) sftp.close()
    }
}
