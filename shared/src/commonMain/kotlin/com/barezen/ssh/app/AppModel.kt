package com.barezen.ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.ssh.credentials.CredentialResolver
import com.barezen.ssh.credentials.NoopCredentialResolver
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.ServerRepository
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.servers.filterServers
import com.barezen.ssh.settings.NoopSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.jvm.JvmName

class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    scope: CoroutineScope,
    val settings: SettingsModel,
    /**
     * 凭据解析端口（jvmMain 钥匙串实现由壳层注入）。
     * 默认 [NoopCredentialResolver]＝不存取，行为与本类引入该参数之前完全一致。
     */
    val credentials: CredentialResolver = NoopCredentialResolver,
) {
    var current: Destination by mutableStateOf(Destination.SERVERS)
        private set
    var servers: List<Server> by mutableStateOf(repo.list())
        private set

    /**
     * 会话注册表：多会话的唯一真相源。连接生命周期只在这里被改写——
     * 下面两个属性是它的**只读派生投影**，迁移期语义与单会话时代逐字等价。
     */
    val registry: SessionRegistry = SessionRegistry(ssh, scope)

    /** 活动会话的连接状态（投影；无活动会话即未连接）。 */
    val connection: ConnectionState get() = registry.active?.state ?: ConnectionState.Disconnected

    /** 活动会话的 shell 会话（投影）；凡丢弃它的路径必须经 registry 关闭。 */
    val shellSession: SshSession? get() = registry.active?.session

    /** 连接确认对话框的目标服务器（null = 不弹）。 */
    var pendingConnect: Server? by mutableStateOf(null)
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

    /**
     * 删除服务器并清理其在钥匙串中的凭据条目（幂等，条目不存在时静默返回）——
     * 否则条目会成为孤儿，永久占据用户钥匙串且无从在 UI 里删除。
     */
    fun removeServer(id: String) {
        repo.delete(id)
        refreshServers()
        credentials.forget(id)
    }

    /**
     * 该服务器是否已有可用凭据（钥匙串可用且存在条目）——连接流程据此决定是否免输入。
     *
     * 注意：底层平台钥匙串是**同步阻塞子进程且无超时**（Linux 下 secret-tool 可能弹出解锁
     * 提示框长时间不返回），故禁止在 Composable 重组路径里调用本方法；请改用
     * [initializePendingCredential]（单次、挂在 LaunchedEffect 上）。
     */
    fun hasStoredCredential(server: Server): Boolean =
        credentials.isAvailable() && credentials.load(server) != null

    /** 待连接服务器的钥匙串预填值；null＝无预填（尚未解析或钥匙串无条目）。 */
    var pendingPrefill: AuthMethod? by mutableStateOf(null)
        private set

    /** 平台钥匙串当前是否可用（供 UI 决定显示「记住凭据」还是不可用提示）。 */
    val keychainAvailable: Boolean get() = credentials.isAvailable()

    /**
     * 解析 [pendingConnect] 在钥匙串中的预填凭据（每个待连接服务器只应调用一次）。
     *
     * 必须在 LaunchedEffect 等协程上下文中调用——见 [hasStoredCredential] 的阻塞说明。
     * 未连接目标或无条目时置 null，调用方按「无预填」处理。
     */
    fun initializePendingCredential() {
        val server = pendingConnect
        pendingPrefill = if (server == null) null else credentials.load(server)
    }

    /** 卡片「连接」/「新建终端」入口：弹出连接确认对话框。 */
    fun requestConnect(server: Server) { pendingConnect = server }
    fun dismissConnect() { pendingConnect = null; pendingPrefill = null }
    /** 确认认证后委托 [startConnect]（内部主路径，免对话框的直连入口）。 */
    fun confirmConnect(auth: AuthMethod) {
        val server = pendingConnect ?: return
        pendingConnect = null
        startConnect(server, auth)
    }

    /**
     * 建连：委托注册表新建一条会话并置为前台。
     *
     * 防重入已退化为 **per-SessionId**（由 [SessionRegistry.connect] 保证同一会话不重复建连）：
     * 多标签的意义正是**同时连接不同服务器**，全局互斥等于没有多标签。旧守卫「Connecting 期间
     * 忽略一切新请求」在单会话下防的是「并发协程互相丢弃会话」，该风险已由注册表的按 id 归属
     * 消除（每条会话各自一个 connectJob + 各自的终态槽位，末写者覆盖不再可能）。
     */
    fun startConnect(server: Server, auth: AuthMethod) {
        val id = registry.connect(server, auth)
        // 导航时机与改造前一致：只有建连**成功**才跳终端页。
        // 连接中/失败时停留在原页，覆盖层（ConnectFlowOverlays 依赖 current != TERMINAL）照常呈现。
        if (registry.sessions.firstOrNull { it.id == id }?.state is ConnectionState.Connected) {
            current = Destination.TERMINAL
        }
    }

    /** 取消进行中的连接：掐掉在途建连协程并移除该会话，投影回落未连接。 */
    fun cancelConnect() {
        registry.activeId?.let { registry.cancel(it) }
    }

    /** 启动时连接：只对「私钥认证」且 id 仍存在的服务器生效。
     *  密码认证的服务器不自动连接 —— 我们没有也不该有持久化密码。 */
    fun autoConnectIfConfigured() {
        val id = settings.settings.autoConnectServerId ?: return
        val server = servers.firstOrNull { it.id == id } ?: return   // 不存在：不连接、不弹窗、不打扰
        if (server.auth !is StoredAuth.Key) return
        startConnect(server, AuthMethod.PrivateKey((server.auth as StoredAuth.Key).keyPath))
    }

    /** 断开活动会话（会话仍在注册表中，标签位保留）。 */
    fun disconnect() {
        registry.activeId?.let { registry.disconnect(it) }
    }

    /**
     * TerminalView 接线处回传的 shell 启动失败：关掉**该会话**的底层连接并就地把状态落为失败
     * （重试走 requestConnect）。不新建会话，故失败信息与 id/title 都挂在原会话上。
     *
     * 必须携带 [SessionId] 而非只看活动会话：启动 shell 与回传失败之间，用户可能已经切到别的
     * 标签——按活动会话落失败会把**正在使用的会话**关掉，而真正出错的会话仍停在 Connected。
     * 失败态里的服务器取自注册表自存的会话，避免调用方传入的 server 与之脱节。
     */
    fun reportShellStartFailed(sessionId: SessionId, message: String) {
        registry.fail(sessionId, message)
    }

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
