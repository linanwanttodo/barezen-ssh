// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/JvmSshClient.kt（Task 6：sshj 实现）
package com.barezen.barezen_ssh.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.util.concurrent.TimeUnit

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

    override fun pingMs(): Long {
        val started = System.nanoTime()
        // 0.40.0 的 Session.Command 无 waitFor()（javap 核对）：以有界 join 等待通道关闭。
        client.startSession().use { s ->
            s.exec("true").use { cmd -> cmd.join(10, TimeUnit.SECONDS) }
        }
        return (System.nanoTime() - started) / 1_000_000
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
}
