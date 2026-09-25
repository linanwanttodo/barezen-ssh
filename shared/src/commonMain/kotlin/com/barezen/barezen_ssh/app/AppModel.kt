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
import com.barezen.barezen_ssh.servers.filterServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.jvm.JvmName

class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    private val scope: CoroutineScope,
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

    fun startConnect(server: Server, auth: AuthMethod) {
        connection = ConnectionState.Connecting(server)
        scope.launch {
            try {
                // 重连收口：旧会话先关再弃（否则连接/keep-alive 线程累积）
                shellSession?.close()
                shellSession = null
                val session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
                shellSession = session
                connection = ConnectionState.Connected(server, session.pingMs())
                current = Destination.TERMINAL
            } catch (e: Exception) {
                connection = ConnectionState.Failed(server, e.message ?: e.toString())
            }
        }
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
        )
    }
}
