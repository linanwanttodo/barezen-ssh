// shared/src/jvmTest/kotlin/com/barezen/ssh/verify/RealSshVerification.kt
package com.barezen.ssh.verify

import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.JvmSshClient
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **真实数据通路验证**（非 mock）：连真实的 SSH 服务器（本机 MINA sshd，与项目测试
 * 同一协议栈），跑通 BareZen 自己的 [JvmSshClient]。
 *
 * 与既有单测的区别：既有测试用假 SftpFs / FakeSession 验证**协议逻辑**，
 * 这里验证**真实 socket 上的字节流**——握手、公钥认证、PTY shell、exec、SFTP、端口转发。
 *
 * 需要 BZ_SSH_PORT / BZ_SSH_KEY / BZ_SSH_HOME 三个环境变量；缺失时**跳过并打印**
 * （不是静默通过——门禁里会看到这行输出）。
 */
class RealSshVerification {

    private data class Env(val port: Int, val keyPath: String, val home: String)

    private fun env(): Env? {
        val port = System.getenv("BZ_SSH_PORT")?.toIntOrNull() ?: return null.also {
            println("[跳过] 未设置 BZ_SSH_PORT")
        }
        val key = System.getenv("BZ_SSH_KEY")
        val home = System.getenv("BZ_SSH_HOME")
        if (key.isNullOrBlank() || home.isNullOrBlank()) {
            println("[跳过] 未设置 BZ_SSH_KEY / BZ_SSH_HOME")
            return null
        }
        return Env(port, key, home)
    }

    private suspend fun connect(e: Env): SshSession = withTimeout(60_000) {
        JvmSshClient().connect(
            ConnectRequest(
                host = "127.0.0.1",
                port = e.port,
                user = "ubuntu",
                auth = AuthMethod.PrivateKey(e.keyPath, null),
            )
        )
    }

    // ---------- 步骤 1：SSH 握手 + 公钥认证 + 往返延迟 ----------
    // 注：PTY shell 交互部分**不在本文件**验证——已定位为验证服务器（MINA sshd）与
    // sshj 的 PTY 协商不兼容（shell 请求未到达服务器 shell 工厂即 EOF），裸 sshj 探针
    // 同样复现，属脚手架问题而非 BareZen 代码问题。真实 OpenSSH 服务器上待验。

    @Test
    fun step1_realSshHandshakeAuthAndLatency(): Unit = runBlocking {
        val e = env() ?: return@runBlocking
        val session = connect(e)
        println("[步骤1] SSH 握手 + 公钥认证 成功（127.0.0.1:${e.port}）")

        val latency = session.pingMs()
        assertTrue(latency >= 0, "pingMs 应返回有效值，实际=$latency")
        println("[步骤1] 状态栏用的往返延迟 = $latency ms")

        session.close()
    }

    // ---------- 步骤 3：真实 SFTP 上传/下载/列目录/删除 ----------

    @Test
    fun step3_realSftpRoundTrip(): Unit = runBlocking {
        val e = env() ?: return@runBlocking
        val session = connect(e)
        val sftp: SftpFs = session.newSftp()
        val remoteDir = File(e.home, "bz-sftp-verify").absolutePath
        // 幂等：上次跑可能已建过。mkdir 对已存在目录会抛 SFTPException，
        // 那是**重复运行**的问题，不是被测代码的缺陷——故先探后建。
        runCatching { sftp.mkdir(remoteDir) }
            .onFailure { println("[步骤3] 目录已存在，复用：$remoteDir") }
        val remoteFile = "$remoteDir/probe.bin"
        val name = remoteFile.substringAfterLast('/')

        try {
            // 上传：按 upload 的拉取式 API 分块喂真实字节
            val payload = ByteArray(96 * 1024) { (it % 251).toByte() }
            val total = payload.size.toLong()
            val chunker: (Long) -> ByteArray? = { off: Long ->
                if (off >= total) {
                    null
                } else {
                    val end = minOf(off + 32L * 1024L, total).toInt()
                    payload.copyOfRange(off.toInt(), end)
                }
            }
            // upload 返回 Unit（协议如此），字节数只能从**服务器侧**读回来核对
            sftp.upload(remoteFile, total, chunker)
            println("[步骤3] 上传调用完成：${payload.size} 字节（真实 SFTP 通道）")

            // 列目录
            val entries: List<SftpEntry> = sftp.list(remoteDir)
            val probe = entries.firstOrNull { it.name == name }
            assertNotNull(probe, "列目录应含 $name，实际=${entries.map { it.name }}")
            assertEquals(payload.size.toLong(), probe.size, "远端大小应与上传一致")
            println("[步骤3] 列目录成功：${entries.map { it.name }}，probe 大小=${probe.size}")

            // 下载：流式分块回读并逐字节比对
            val sink = ByteArrayOutputStream()
            val got = sftp.download(remoteFile) { sink.write(it) }
            assertEquals(payload.size.toLong(), got, "下载字节数应一致")
            assertTrue(payload.contentEquals(sink.toByteArray()), "下载内容应与上传完全一致")
            println("[步骤3] 下载并逐字节校验通过（${sink.size()} 字节）")

            // 重命名
            val renamed = "$remoteDir/probe-renamed.bin"
            sftp.rename(remoteFile, renamed)
            assertTrue(
                sftp.list(remoteDir).any { it.name == "probe-renamed.bin" },
                "重命名后应出现新名",
            )
            println("[步骤3] 重命名成功")

            // 删除 + 确认
            sftp.delete(renamed)
            assertTrue(
                sftp.list(remoteDir).none { it.name == "probe-renamed.bin" },
                "删除后不应再存在",
            )
            println("[步骤3] 删除并确认成功")
        } finally {
            sftp.close()
            session.close()
        }
    }

    // ---------- 步骤 3：真实 exec 指标采集（走项目自己的解析链路） ----------

    @Test
    fun step3_realExecMetricsCollection(): Unit = runBlocking {
        val e = env() ?: return@runBlocking
        val session = connect(e)

        val collector = com.barezen.ssh.ssh.metrics.MetricsCollector(session)
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
        collector.start(scope)
        try {
            val deadline = System.currentTimeMillis() + 30_000
            while (collector.snapshot.value == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
            }
            val snap = collector.snapshot.value
            assertNotNull(snap, "指标采集在 30s 内未产出任何快照（exec 通路可能不通）")
            println(
                "[步骤3] exec 指标采集成功：load1=${snap!!.load1} memUsedPct=${snap.memUsedPct} " +
                    "uptime=${snap.uptimeSeconds} 磁盘数=${snap.disks.size}",
            )
            assertTrue(snap.load1 != null, "load1 应来自真实 /proc/loadavg")
            assertTrue(snap.disks.isNotEmpty(), "df -P 应解析出磁盘用量")
            println("[步骤3] 磁盘解析：" + snap.disks.joinToString { "${it.mountedOn} ${it.capacityPct}%" })
        } finally {
            collector.stop()
            session.close()
        }
    }

    // ---------- 步骤 4：真实本地端口转发（本机 -> 远端服务，校验真实字节） ----------

    @Test
    fun step4_realLocalForwardCarriesBytes(): Unit = runBlocking {
        val e = env() ?: return@runBlocking
        val session = connect(e)

        // 远端起一个**回显**服务：收到什么就回什么（带 ACK 前缀）。
        // 用 base64 传脚本，避开多层引号转义；不依赖 nc/socat 的具体参数差异。
        val remotePort = 18099
        val py = buildString {
            append("import socket\n")
            append("s=socket.socket()\n")
            append("s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)\n")
            append("s.bind(('127.0.0.1', $remotePort))\n")
            append("s.listen(5)\n")
            append("while True:\n")
            append("    c,_=s.accept()\n")
            append("    d=c.recv(4096)\n")
            append("    c.sendall(b'BZ_ACK:'+d)\n")
            append("    c.close()\n")
        }
        val b64 = java.util.Base64.getEncoder().encodeToString(py.toByteArray())
        session.exec("echo $b64 | base64 -d > /tmp/bz-echo.py", timeoutMs = 10_000)
        session.exec("nohup python3 /tmp/bz-echo.py >/tmp/bz-echo.log 2>&1 &", timeoutMs = 10_000)
        // 等远端监听就绪：直接查远端端口，不靠盲等
        val listenDeadline = System.currentTimeMillis() + 15_000
        var listening = false
        while (!listening && System.currentTimeMillis() < listenDeadline) {
            val probe = session.exec(
                "ss -tln 2>/dev/null | grep -c $remotePort || cat /tmp/bz-echo.log",
                timeoutMs = 8_000,
            )
            listening = probe.stdout.contains("LISTEN") || probe.stdout.trim() == "1"
            if (!listening) Thread.sleep(500)
        }
        assertTrue(listening, "远端回显服务未在 $remotePort 监听")
        println("[步骤4] 远端回显服务已就绪：127.0.0.1:$remotePort")

        val bindPort = freePort()
        val spec = ForwardSpec(ForwardKind.LOCAL, bindPort, "127.0.0.1", remotePort)
        val tunnel = session.startForward(spec)
        println("[步骤4] 隧道已建立：本机 $bindPort -> 远端 127.0.0.1:$remotePort")

        try {
            val payload = "BZ_TUNNEL_PROBE_12345"
            val deadline = System.currentTimeMillis() + 25_000
            var received = ""
            var lastError: String? = null
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                try {
                    Socket("127.0.0.1", bindPort).use { sock ->
                        sock.soTimeout = 6000
                        sock.getOutputStream().write(payload.toByteArray())
                        sock.getOutputStream().flush()
                        // 回显服务收到即回，1s 内应拿到 ACK
                        Thread.sleep(800)
                        val buf = ByteArray(4096)
                        var total = 0
                        var chunk: Int
                        while (total < 4096) {
                            sock.getInputStream().read(buf, total, buf.size - total).let { chunk = it }
                            if (chunk <= 0) break
                            total += chunk
                            if (String(buf, 0, total, Charsets.UTF_8).contains(payload)) break
                        }
                        received = String(buf, 0, total, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    lastError = e.message
                    Thread.sleep(500)
                }
            }
            // **真断言**：必须真的经隧道拿回远端回显的字节，否则算失败
            assertTrue(
                received.contains("BZ_ACK") && received.contains(payload),
                "经本地转发未拿回远端回显。收到=[${received.take(120)}] 最后错误=[$lastError]",
            )
            println("[步骤4] 本地转发数据通路真实打通，回显内容=${received.trim()}")
        } finally {
            tunnel.close()
            session.close()
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
