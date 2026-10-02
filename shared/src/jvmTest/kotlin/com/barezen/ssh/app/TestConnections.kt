// shared/src/jvmTest/kotlin/com/barezen/ssh/app/TestConnections.kt
package com.barezen.ssh.app

import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.ExecResult
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.awaitCancellation

/**
 * 测试用可控 SSH 客户端：由测试构造并注入 [AppModel]，三者互斥地决定一次建连的结果。
 *
 * 之所以放在 jvmTest 而不是产品代码：它只在测试里被使用，产品路径必须始终走真实 [SshClient]。
 */
class ControllableSshClient : SshClient {
    /** 非 null 时立即抛该异常（落 Failed 态）。 */
    var failure: String? = null

    /** 非 null 时立即返回该会话（落 Connected 态）。 */
    var session: SshSession? = null

    /** 为 true 时永久挂起（保持 Connecting 态）。 */
    var hang: Boolean = false

    /** 实际发生的建连次数；防重入类断言据此证明第二次连接未被发起。 */
    var calls: Int = 0
        private set

    override suspend fun connect(request: ConnectRequest): SshSession {
        calls++
        failure?.let { throw IllegalStateException(it) }
        session?.let { return it }
        awaitCancellation()
    }
}

/** 测试用会话：pingMs 返回注入值时延，close 可观测（closeAll/close 的断言依据）。 */
class ControllableSshSession(private val latencyMs: Long) : SshSession {
    var closed: Boolean = false
        private set

    override fun pingMs(): Long = latencyMs
    override fun exec(command: String, timeoutMs: Long): ExecResult = ExecResult(0, "", "")
    override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
        error("测试会话未配置 shell")
    override fun newSftp(): SftpFs = error("测试会话未配置 SFTP")
    override fun startForward(spec: ForwardSpec): ForwardTunnel = error("测试会话未配置转发")
    override fun close() { closed = true }
}

/**
 * 测试专用：把模型置入指定连接状态，经公共 API 驱动（产品代码零测试痕迹）。
 *
 * 刻意直连 [SessionRegistry.connect] 而**不**走 [AppModel.startConnect]：后者的
 * current = TERMINAL 会让 ConnectFlowOverlays 提前返回（它只在非终端页叠加），
 * 而既有断言正是在服务器页上校验覆盖层。此处保持与改造前「直接套用状态」等价的
 * 页面归属（停在 SERVERS），只让连接状态与会话成对落位。
 *
 * 需要模型由 [ControllableSshClient] 构造——既有测试的 model 工厂已按此注入。
 */
fun AppModel.applyConnectionForTest(state: ConnectionState) {
    registry.closeAll()
    val client = ssh as ControllableSshClient
    client.failure = null
    client.session = null
    client.hang = false
    when (state) {
        ConnectionState.Disconnected -> Unit
        is ConnectionState.Connecting -> {
            client.hang = true
            registry.connect(state.server, TEST_AUTH)
        }
        is ConnectionState.Failed -> {
            client.failure = state.message
            registry.connect(state.server, TEST_AUTH)
        }
        is ConnectionState.Connected -> {
            client.session = ControllableSshSession(state.latencyMs)
            registry.connect(state.server, TEST_AUTH)
        }
    }
}

/** 注入状态时使用的占位认证：状态由 client 决定，认证内容不参与任何分支。 */
private val TEST_AUTH: AuthMethod = AuthMethod.PrivateKey("test-only")
