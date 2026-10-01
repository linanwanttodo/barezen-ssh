package com.barezen.barezen_ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.ServerRepository
import com.barezen.barezen_ssh.servers.StoredAuth
import com.barezen.barezen_ssh.servers.filterServers
import com.barezen.barezen_ssh.settings.NoopSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    private val scope: CoroutineScope,
    val settings: SettingsModel,
) {
    var current: Destination by mutableStateOf(Destination.SERVERS)
        private set
    var servers: List<Server> by mutableStateOf(repo.list())
        private set
    var connection: ConnectionState by mutableStateOf(ConnectionState.Disconnected)
        private set
    /** 连接确认对话框的目标服务器（null = 不弹）。 */
    var pendingConnect: Server? by mutableStateOf(null)
        private set
    /** 当前活动 shell 会话；凡丢弃它的路径必须先 close（连接失败/重连/断开/收口）。 */
    var shellSession: SshSession? = null
        private set
    var query: String by mutableStateOf("")
    var selectedTag: String? by mutableStateOf(null)
    val visibleServers: List<Server> get() = filterServers(servers, query, selectedTag)

    fun navigate(to: Destination) { current = to }
    fun refreshServers() { servers = repo.list() }
    // @JvmName：避免与 var query 的 setter 在 JVM 上签名冲突（platform declaration clash）
    @JvmName("setQueryValue")
    fun setQuery(value: String) { query = value }
    fun selectTag(value: String?) { selectedTag = value }
    fun saveServer(server: Server) { repo.upsert(server); refreshServers() }
    fun removeServer(id: String) { repo.delete(id); refreshServers() }

    /** 卡片「连接」/「新建终端」入口：弹出连接确认对话框。 */
    fun requestConnect(server: Server) { pendingConnect = server }
    fun dismissConnect() { pendingConnect = null }
    /** 确认认证后委托 [startConnect]（内部主路径，免对话框的直连入口）。 */
    fun confirmConnect(auth: AuthMethod) {
        val server = pendingConnect ?: return
        pendingConnect = null
        startConnect(server, auth)
    }

    /** 进行中的连接协程；取消连接用（设计包连接中对话框「取消」）。 */
    private var connectJob: Job? = null

    fun startConnect(server: Server, auth: AuthMethod) {
        // 防重入：建连进行中忽略新请求——并发协程会互相丢弃会话（孤儿 SSH 连接+keep-alive 线程），
        // 且 shellSession/connection 两次赋值可能被末写者覆盖成不一致
        if (connection is ConnectionState.Connecting) return
        connection = ConnectionState.Connecting(server)
        connectJob = scope.launch {
            try {
                // 重连收口：旧会话先关再弃（否则连接/keep-alive 线程累积）
                shellSession?.close()
                shellSession = null
                val session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
                shellSession = session
                connection = ConnectionState.Connected(server, session.pingMs())
                current = Destination.TERMINAL
            } catch (e: CancellationException) {
                throw e                       // 取消不落失败态：由 cancelConnect 置 Disconnected
            } catch (e: Exception) {
                connection = ConnectionState.Failed(server, e.message ?: e.toString())
            } finally {
                connectJob = null
            }
        }
    }

    /** 取消进行中的连接：掐协程并回落 Disconnected（旧会话已在协程里收口，无孤儿）。 */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        connection = ConnectionState.Disconnected
    }

    /** 启动时连接：只对「私钥认证」且 id 仍存在的服务器生效。
     *  密码认证的服务器不自动连接 —— 我们没有也不该有持久化密码。 */
    fun autoConnectIfConfigured() {
        val id = settings.settings.autoConnectServerId ?: return
        val server = servers.firstOrNull { it.id == id } ?: return   // 不存在：不连接、不弹窗、不打扰
        if (server.auth !is StoredAuth.Key) return
        startConnect(server, AuthMethod.PrivateKey((server.auth as StoredAuth.Key).keyPath))
    }

    fun disconnect() {
        shellSession?.close()
        shellSession = null
        connection = ConnectionState.Disconnected
    }

    /** TerminalView 接线处回传的 shell 启动失败：关会话并置失败态（重试走 requestConnect）。 */
    fun reportShellStartFailed(server: Server, message: String) {
        shellSession?.close()
        shellSession = null
        connection = ConnectionState.Failed(server, message)
    }

    /** 测试专用：直接套用连接状态（生产路径只经 startConnect / disconnect / reportShellStartFailed）。 */
    fun applyConnectionForTest(state: ConnectionState) { connection = state }

    companion object {
        fun forUiTest(): AppModel = AppModel(
            repo = object : ServerRepository {
                override fun list(): List<Server> = emptyList()
                override fun upsert(server: Server) {}
                override fun delete(id: String) {}
            },
            ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            settings = SettingsModel(NoopSettingsRepository()).also { it.load() },
        )
    }
}
