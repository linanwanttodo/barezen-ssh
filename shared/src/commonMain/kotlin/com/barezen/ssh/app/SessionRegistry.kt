package com.barezen.ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ssh.ConnectRequest
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.SshClient
import com.barezen.ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline

/** 会话唯一标识。单调递增，进程内唯一；关闭后 id 不再分配给新会话（避免旧引用误伤新会话）。 */
@JvmInline
value class SessionId(val raw: Long)

/** 单条会话的快照（不可变；UI 只消费本类型，不直接持有 [SshSession]）。 */
data class SessionSnapshot(
    val id: SessionId,
    val server: Server,
    val state: ConnectionState,
    /** 仅 [ConnectionState.Connected] 时非空；其余状态恒为 null（状态与会话同进同退）。 */
    val session: SshSession?,
    /** 标签标题：默认 server.name，同名服务器重复连接时为 "name (2)" 以避免标签无法区分。 */
    val title: String,
)

/**
 * 会话达到上限时抛出（设计 §2.2：达限**拒绝**，绝不隐式淘汰用户既有会话——
 * 淘汰会让用户正在等待的输出静默消失，且是破坏性行为）。
 */
class SessionLimitException(val max: Int) :
    Exception("最多同时打开 $max 个会话，请先关闭一个")

/**
 * 会话注册表：多会话的唯一真相源。
 *
 * 线程模型：公开方法必须在主线程（Compose 重组线程）调用——[sessions] 以 mutableStateListOf 承载，
 * 写操作即触发重组。连接协程可能在其他线程恢复，故**协程内不直接改状态**，只把结果写回
 * [PendingConnect] 的槽位，由 [pump] 在主线程消费（[SshClient.connect] 因此被要求立即返回）。
 */
class SessionRegistry(
    private val ssh: SshClient,
    private val scope: CoroutineScope,
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
) {
    /** 会话列表，顺序 = 标签显示顺序（插入序）。 */
    val sessions: List<SessionSnapshot> get() = snapshots

    private val snapshots = mutableStateListOf<SessionSnapshot>()

    /** 活动（前台）会话；[activate]/[close] 写完即重组。 */
    var activeId: SessionId? by mutableStateOf(null)
        private set

    val active: SessionSnapshot? get() = activeId?.let { id -> snapshots.lastOrNull { it.id == id } }

    private var nextRaw = 0L
    private val pending = mutableMapOf<SessionId, MutableList<PendingConnect>>()
    private val connectJobs = mutableMapOf<SessionId, Job>()

    /**
     * 建连并激活。返回新会话 id；状态先落 Connecting，随后在同一帧内的 [pump] 中推进到
     * Connected / Failed（[SshClient.connect] 约定立即返回，见类注释）。
     */
    fun connect(server: Server, auth: AuthMethod): SessionId {
        if (snapshots.size >= maxSessions) throw SessionLimitException(maxSessions)
        val id = SessionId(nextRaw++)
        snapshots += SessionSnapshot(id, server, ConnectionState.Connecting(server), null, titleFor(server.name))
        activeId = id
        // 防重入：本会话只允许一个在途连接（同一 SessionId 不重复建连）；不同会话可并发
        connectJobs.remove(id)?.cancel()
        val slot = PendingConnect()
        pending.getOrPut(id) { mutableListOf() } += slot
        connectJobs[id] = scope.launch {
            try {
                slot.session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
            } catch (e: CancellationException) {
                throw e                        // 取消不落失败态：会话由 cancel/close 移除
            } catch (e: Exception) {
                slot.failure = e.message ?: e.toString()
            }
            // 结果由协程自己提交：connect() 返回时状态下可能仍在 Connecting（ssh.connect 挂起），
            // 此后只有这里能把终态写进列表——不能依赖调用方再调 pump()。
            commit(id, slot)
        }
        pump()
        return id
    }

    /** 掐掉连接中的会话：取消在途协程并移除会话（设计 §5.1「取消连接中」行）。 */
    fun cancel(id: SessionId) {
        connectJobs.remove(id)?.cancel()
        remove(id)
    }

    /** 断开会话但保留标签：关底层 SSH 会话，状态回落 Disconnected。 */
    fun disconnect(id: SessionId) {
        connectJobs.remove(id)?.cancel()
        discardPending(id)
        val index = indexOf(id)
        if (index < 0) return
        val current = snapshots[index]
        current.session?.close()
        snapshots[index] = current.copy(state = ConnectionState.Disconnected, session = null)
    }

    /**
     * shell 启动失败（接线层回传）：关掉该会话底层连接并就地把状态落为 [ConnectionState.Failed]。
     * 不新增会话——失败信息挂在原会话上，id 与 title 原地复用，标签不闪烁。
     */
    fun fail(id: SessionId, message: String) {
        connectJobs.remove(id)?.cancel()
        discardPending(id)
        val index = indexOf(id)
        if (index < 0) return
        val current = snapshots[index]
        current.session?.close()
        snapshots[index] = current.copy(
            state = ConnectionState.Failed(current.server, message),
            session = null,
        )
    }

    /** 关闭标签：释放会话资源并从列表移除；关活动标签时自动激活左邻（无左邻取新首位，空则 null）。 */
    fun close(id: SessionId) {
        connectJobs.remove(id)?.cancel()
        remove(id)
    }

    /** 关闭全部标签（应用退出亦走此处）：资源全部释放，不留后台连接。 */
    fun closeAll() {
        connectJobs.values.forEach { it.cancel() }
        connectJobs.clear()
        pending.clear()
        snapshots.forEach { it.session?.close() }
        snapshots.clear()
        activeId = null
    }

    /** 切换前台会话；未知 id 不改动活动会话（不静默清空）。 */
    fun activate(id: SessionId) {
        if (indexOf(id) >= 0) activeId = id
    }

    /**
     * Failed 会话重置为可连接态：原地复用 id 与 title，避免标签闪烁与旧引用失效。
     *
     * 不在此处发起连接——本表不保存凭据，故重连由调用方重开认证对话框后
     * 走 connect(server, auth) + close(id) 完成（第 3 刀接线时定稿）。
     */
    fun retry(id: SessionId) {
        val index = indexOf(id)
        if (index < 0) return
        val current = snapshots[index]
        current.session?.close()
        snapshots[index] = current.copy(state = ConnectionState.Connecting(current.server), session = null)
        activeId = id
    }

    /**
     * 收口所有建连结果。生产侧对"无 Compose 的终态通知"的需求由第 3 刀按调用方定形；
     * 本刀保证 [connect] 内已收口，故既有调用方无需额外驱动。
     */
    fun pump() {
        val keys = pending.keys.toList()
        for (id in keys) {
            if (pending[id]?.any { it.failure != null || it.session != null } == true) commit(id, null)
        }
    }

    /**
     * 把一条会话的建连结果写入列表（终态）。
     *
     * [only] 非空时只认该槽位（协程自己收尾的路径，避免误并其他在途连接的结果）；
     * 为 null 时按任意已完成槽位收口（[pump] 驱动的路径）。
     */
    private fun commit(id: SessionId, only: PendingConnect?) {
        val slot = only ?: pending[id]?.firstOrNull { it.failure != null || it.session != null } ?: return
        pending.remove(id)
        connectJobs.remove(id)
        val index = indexOf(id)
        if (index < 0) {
            // 会话已被取消/关闭：结果无人接收，必须就地释放，否则成为孤儿 SSH 连接
            slot.session?.close()
            return
        }
        val current = snapshots[index]
        val message = slot.failure
        if (message != null) {
            snapshots[index] = current.copy(state = ConnectionState.Failed(current.server, message), session = null)
            return
        }
        val session = slot.session ?: return
        snapshots[index] = current.copy(
            state = ConnectionState.Connected(current.server, session.pingMs()),
            session = session,
        )
    }

    private fun remove(id: SessionId) {
        val index = indexOf(id)
        if (index < 0) {
            discardPending(id)
            return
        }
        val removed = snapshots.removeAt(index)
        discardPending(id)?.session?.close()          // 结果丢失：就地释放，不留孤儿连接
        if (removed.id == activeId) activeId = snapshots.getOrNull(if (index > 0) index - 1 else 0)?.id
        removed.session?.close()
    }

    /** 丢弃该会话尚未收口的结果（已建成的会话交给调用方释放，未建成的由 [pump] 释放）。 */
    private fun discardPending(id: SessionId): PendingConnect? {
        val slots = pending.remove(id) ?: return null
        return slots.firstOrNull { it.session != null || it.failure != null }
    }

    private fun indexOf(id: SessionId): Int = snapshots.indexOfFirst { it.id == id }

    /** 同名服务器重复连接时给出可区分的标签：「web-01」「web-01 (2)」。 */
    private fun titleFor(name: String): String {
        val used = snapshots.count { it.title == name || it.title.startsWith("$name (") }
        return if (used == 0) name else "$name (${used + 1})"
    }

    /** 连接协程与主线程之间的结果槽位；简单赋值即可跨线程转移引用。 */
    private class PendingConnect {
        @Volatile var session: SshSession? = null
        @Volatile var failure: String? = null
    }

    companion object {
        /** 上限：每条会话 = 1 个 sshj SSHClient + 1 个 keep-alive 线程 + 若干通道。 */
        const val DEFAULT_MAX_SESSIONS = 8
    }
}
