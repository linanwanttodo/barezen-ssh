// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/FakeSshSession.kt
package com.barezen.ssh.ssh

/** 测试用 SshSession：记录 exec 调用，按 handler 返回预设结果（不依赖 sshj）。 */
class FakeSshSession(
    var handler: (String) -> ExecResult = { ExecResult(0, "", "") },
) : SshSession {
    val execCalls = mutableListOf<String>()
    var pingCount = 0
    var closed = false

    override fun exec(command: String, timeoutMs: Long): ExecResult {
        execCalls += command
        return handler(command)
    }

    override fun pingMs(): Long {
        pingCount++
        return 1L
    }

    override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
        error("unused in metrics tests")

    override fun newSftp(): SftpFs = error("FakeSshSession 未配置 SFTP")

    override fun startForward(spec: ForwardSpec): ForwardTunnel = error("FakeSshSession 未配置转发")

    override fun close() {
        closed = true
    }
}

/** 测试用 SshClient：connect 恒返回注入的会话。 */
class FakeSshClient(private val session: SshSession) : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession = session
}
