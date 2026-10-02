// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/JvmSshClientTest.kt
package com.barezen.ssh.ssh

import org.apache.sshd.common.keyprovider.MappedKeyPairProvider
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.ProcessShellCommandFactory
import org.apache.sshd.server.shell.ShellFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class JvmSshClientTest {
    private lateinit var sshd: SshServer

    /** 每个测试注入独立的临时 known_hosts，避免写真实 ~/.ssh/known_hosts。 */
    private fun tofu() = TofuHostKeyVerifier(File.createTempFile("kh_", ""))

    private fun startSshd(): Int {
        // 2.14.0 无 GeneratorSecurityKeyPairProvider（javap 核对）：
        // 用 MappedKeyPairProvider 承载内存中生成的主机密钥对，同样不落盘。
        val hostKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = MappedKeyPairProvider(hostKey)
            passwordAuthenticator = object : PasswordAuthenticator {
                override fun authenticate(u: String, p: String, s: ServerSession) = u == "test" && p == "secret"
            }
            shellFactory = ShellFactory { EchoShell() }
            // JvmSshClient.pingMs 走 exec("true")；setUpDefaultServer 默认不带 commandFactory
            commandFactory = ProcessShellCommandFactory()
        }
        sshd.start()
        return sshd.port
    }

    @AfterTest fun stop() {
        runCatching { sshd.stop(true) }
    }

    @Test fun connectAndPingSucceeds() = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        val session = JvmSshClient(tofu()).connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        assertTrue(session.pingMs() >= 0)
        session.close()
    }

    @Test fun wrongPasswordFails(): Unit = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        assertFails {
            JvmSshClient(tofu()).connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("wrong")))
        }
    }

    /** 应用级单例验证器（与 AppModel.desktop 的单例形态一致）：同一运行期重连不得追加重复条目。 */
    @Test fun reconnectDoesNotDuplicateKnownHostLine(): Unit = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        val kh = File.createTempFile("kh_", "")
        val client = JvmSshClient(TofuHostKeyVerifier(kh))
        val request = ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret"))
        client.connect(request).close()
        client.connect(request).close()
        val lines = kh.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size, "known_hosts 出现重复条目: $lines")
        assertTrue(lines.single().contains("127.0.0.1"))
    }

    @Test fun shellReceivesDataAndSendsInput() = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        val session = JvmSshClient(tofu()).connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        val received = LinkedBlockingQueue<ByteArray>()
        val shell = session.startShell(onData = { received.add(it) }, onClosed = {})
        shell.write("ping\n".toByteArray())
        val first = received.poll(5, TimeUnit.SECONDS)
        assertTrue(first != null && String(first).contains("READY"))
        shell.close()
        session.close()
    }
}

/** 简单回显 shell：启动即输出 READY，随后把输入原样回显。 */
private class EchoShell : Command {
    private var input: InputStream = ByteArrayInputStream(ByteArray(0))
    private lateinit var output: OutputStream

    override fun setInputStream(`in`: InputStream?) {
        input = `in` ?: ByteArrayInputStream(ByteArray(0))
    }

    override fun setOutputStream(out: OutputStream?) {
        output = out ?: OutputStream.nullOutputStream()
    }

    override fun setErrorStream(err: OutputStream?) {}

    override fun setExitCallback(callback: ExitCallback?) {}

    override fun start(session: ChannelSession?, env: Environment?) {
        output.write("READY\n".toByteArray())
        output.flush()
        Thread {
            val buf = ByteArray(1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        }.apply {
            isDaemon = true
            name = "echo-shell-reader"
            start()
        }
    }

    override fun destroy(session: ChannelSession?) {}
}
