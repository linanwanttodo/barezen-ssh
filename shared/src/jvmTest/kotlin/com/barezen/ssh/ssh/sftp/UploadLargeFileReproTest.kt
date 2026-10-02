// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/UploadLargeFileReproTest.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.JvmSshClient
import com.barezen.ssh.ssh.TofuHostKeyVerifier
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.ProcessShellCommandFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * T-2a 回归：10MiB 整块边界上传必须**正常完成**。
 *
 * 缺陷历史：JvmSftpFs.upload 对空块执行 `continue` 而不推进 offset，回调持续返回同一个空块，
 * 形成 100% CPU 忙等 —— 真机表现为「远端已落盘最后一次写入的内容，进程再不返回、无异常、无输出」。
 * 根因是 LocalChunkReader 在文件末尾返回 null，而 `null ?: break` 本应终止；一旦回调返回空数组而非
 * null，旧实现就永久空转。本测试用真实 sshj + 嵌入式 sshd 覆盖该路径。
 */
class UploadLargeFileReproTest {
    private lateinit var sshd: SshServer
    private lateinit var root: File

    private fun startSshd(remoteRoot: File? = null): Int {
        val r = remoteRoot ?: File.createTempFile("sftproot_", "").apply { delete(); mkdirs() }
        root = r
        val hostKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = MappedKeyPairProvider(hostKey)
            passwordAuthenticator = object : PasswordAuthenticator {
                override fun authenticate(u: String, p: String, s: ServerSession) = u == "test" && p == "secret"
            }
            commandFactory = ProcessShellCommandFactory()
            subsystemFactories = listOf(SftpSubsystemFactory())
        }
        sshd.start()
        return sshd.port
    }

    @AfterTest fun stop() {
        runCatching { sshd.stop(true) }
        runCatching { root.deleteRecursively() }
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it) }

    /**
     * 走真实 sshj + 嵌入式 sshd 的完整上传路径。
     *
     * remoteRoot 由调用方提供（不用可变的 root 字段——它会被下一次 startSshd 覆盖），
     * 返回远端文件的绝对路径。
     */
    private fun uploadViaRealStack(remoteRoot: File, localPath: String, remoteName: String): File = runBlocking {
        root = remoteRoot
        val port = startSshd(remoteRoot)
        val session = JvmSshClient(TofuHostKeyVerifier(File.createTempFile("kh_", "")))
            .connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        try {
            val reader = LocalChunkReader(localPath)
            val sftp = session.newSftp()
            // 必须用绝对路径：嵌入式 sshd 以 JVM 工作目录解析相对路径，相对名会落到仓库目录里
            val remotePath = File(remoteRoot, remoteName).absolutePath
            try {
                sftp.upload(remotePath, reader.size()) { offset -> reader.read(offset) }
            } finally {
                reader.close()
            }
        } finally {
            session.close()
        }
        File(remoteRoot, remoteName)
    }

    @Test fun tenMebibyteUploadCompletesWithoutBusyWait() {
        // 10MiB 恰为 64KiB 的整数倍：末块读完后 offset == size，走 null 终止分支
        val size = 10 * 1024 * 1024
        val local = File.createTempFile("big_", ".bin").apply { writeBytes(ByteArray(size) { (it % 251).toByte() }) }
        val remoteRoot = File.createTempFile("sftproot_", "").apply { delete(); mkdirs() }
        val started = System.currentTimeMillis()

        val remote = uploadViaRealStack(remoteRoot, local.absolutePath, "big.bin")

        val elapsed = System.currentTimeMillis() - started
        assertTrue(remote.exists(), "远端文件不存在")
        assertEquals(size.toLong(), remote.length(), "远端大小不符")
        assertEquals(sha256(local.readBytes()), sha256(remote.readBytes()), "内容校验和不符")
        assertTrue(elapsed < 60_000, "上传耗时异常（疑似忙等）：${elapsed}ms")
        local.delete()
    }

    @Test fun nonChunkAlignedUploadCompletes() {
        val size = 200 * 1024 + 13
        val local = File.createTempFile("odd_", ".bin").apply { writeBytes(ByteArray(size) { (it % 97).toByte() }) }
        val remoteRoot = File.createTempFile("sftproot_", "").apply { delete(); mkdirs() }

        val remote = uploadViaRealStack(remoteRoot, local.absolutePath, "odd.bin")
        assertEquals(size.toLong(), remote.length(), "远端大小不符")
        assertEquals(sha256(local.readBytes()), sha256(remote.readBytes()), "内容校验和不符")
        local.delete()
    }

    @Test fun uploaderRejectsEmptyChunkInsteadOfSpinning() = runBlocking {
        val port = startSshd()  // 该用例自建 sshd，不与他人共享 root
        val session = JvmSshClient(TofuHostKeyVerifier(File.createTempFile("kh_", "")))
            .connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        try {
            val sftp = session.newSftp()
            var calls = 0
            var threw: Throwable? = null
            val watchdog = Thread { Thread.sleep(20_000); if (calls < 100) throw AssertionError("upload 未返回") }
            try {
                sftp.upload(File(root, "spin.bin").absolutePath, 64L * 1024 * 1024) {
                    calls++
                    if (calls > 5_000) throw IllegalStateException("检测到忙等：回调被调用 $calls 次")
                    if (calls == 1) ByteArray(64 * 1024) else ByteArray(0)
                }
            } catch (e: Throwable) {
                threw = e
            } finally {
                watchdog.interrupt()
            }
            assertTrue(threw != null, "空块必须快速失败，而不是忙等")
            assertTrue(calls <= 5, "不应空转，回调次数=$calls")
        } finally {
            session.close()
        }
    }
}
