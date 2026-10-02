// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/sftp/SftpModel.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpException
import com.barezen.ssh.ssh.SftpFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** 目录浏览状态；error 非空表示最近一次列表操作失败，entries 保留上次成功结果。 */
data class SftpState(
    val cwd: String = "/",
    val entries: List<SftpEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

enum class TransferKind { UPLOAD, DOWNLOAD }

sealed interface TransferStatus {
    data object Running : TransferStatus
    data object Done : TransferStatus
    data class Failed(val message: String) : TransferStatus
}

/**
 * 传输任务快照；totalBytes 为 -1 表示大小未知（不显示百分比）。
 *
 * [completion] 在任务进入终态（Done 或 Failed）时完成，值是终态快照；
 * 供冒烟脚手架与需要「等待传输真正结束」的调用方使用（见 [SftpModel.awaitCompletion]）。
 * 进行中任务的 completion 未完成，取消同样会以 Failed(已取消) 结束它。
 */
data class TransferTask(
    val id: Long,
    val kind: TransferKind,
    val name: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val completion: Deferred<TransferTask>,
)

/**
 * SFTP 状态层：目录浏览 + 并发传输队列。
 *
 * 通过 [fsFactory] 惰性获取 [SftpFs]（便于测试注入）；阻塞调用统一切到 [io] 调度器；
 * 每个传输任务独占一个协程，取消采用标志位 + 块间检查（见 [cancel]）。
 * 进度 flow 节流：每秒最多一次或每 256KiB 一次（[TransferThrottler]）。
 */
class SftpModel(
    private val scope: CoroutineScope,
    private val fsFactory: suspend () -> SftpFs,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(SftpState())
    val state: StateFlow<SftpState> = _state.asStateFlow()

    private val _transfers = MutableStateFlow<List<TransferTask>>(emptyList())
    val transfers: StateFlow<List<TransferTask>> = _transfers.asStateFlow()

    private val fsMutex = Mutex()
    private var fs: SftpFs? = null
    private val nextId = AtomicLong(1L)
    private val cancelFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    /** 任务 id -> 终态完成信号；任务进入终态时以终态快照完成。 */
    private val completions = ConcurrentHashMap<Long, CompletableDeferred<TransferTask>>()

    init {
        home()
    }

    // ---- 目录浏览 ----

    fun refresh() = scope.launch { list(_state.value.cwd) }

    fun home() = scope.launch { list("/") }

    fun goto(path: String) = scope.launch { list(SftpPaths.join(path, "")) }

    /** 进入子目录（须为当前列表中的目录项）；[name] 为 ".." 时返回上级，根目录的上级仍是根目录。 */
    fun enter(name: String) = scope.launch {
        val cur = _state.value
        if (name == "..") {
            list(SftpPaths.parentOf(cur.cwd))
            return@launch
        }
        val entry = cur.entries.firstOrNull { it.name == name } ?: return@launch
        if (!entry.isDirectory) return@launch
        list(SftpPaths.join(cur.cwd, name))
    }

    /** 在当前目录新建文件夹，成功后刷新列表。 */
    fun mkdir(name: String) = scope.launch {
        val fs = fsOrNull() ?: return@launch
        val dir = SftpPaths.join(_state.value.cwd, name)
        try {
            withContext(io) { fs.mkdir(dir) }
            list(_state.value.cwd)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = describe(e)) }
        }
    }

    private suspend fun list(dir: String) {
        val fs = fsOrNull() ?: return
        _state.update { it.copy(loading = true, error = null) }
        try {
            val entries = withContext(io) { fs.list(dir) }
            _state.update { it.copy(cwd = dir, entries = EntrySorter.sorted(entries), loading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = describe(e)) }
        }
    }

    private suspend fun fsOrNull(): SftpFs? = fsMutex.withLock {
        fs ?: try {
            fsFactory().also { fs = it }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = describe(e)) }
            null
        }
    }

    // ---- 传输 ----

    /** 上传本地文件为当前目录下的 [remoteName]；文件缺失或远端失败时任务置为 FAILED。 */
    fun upload(localPath: String, remoteName: String) {
        val id = newTask(TransferKind.UPLOAD, remoteName)
        val flag = AtomicBoolean(false)
        cancelFlags[id] = flag
        scope.launch {
            try {
                val fs = fsOrFail()
                val reader = LocalChunkReader(localPath)
                try {
                    val total = reader.size()
                    updateTask(id) { it.copy(totalBytes = total) }
                    val throttler = TransferThrottler(THROTTLE_MS, THROTTLE_BYTES)
                    withContext(io) {
                        fs.upload(SftpPaths.join(_state.value.cwd, remoteName), total) { offset ->
                            throwIfCancelled(flag)
                            val chunk = reader.read(offset)
                            if (chunk != null && throttler.shouldEmit(clock(), chunk.size.toLong())) {
                                updateTask(id) { it.copy(transferredBytes = offset + chunk.size) }
                            }
                            chunk
                        }
                    }
                    updateTask(id) { it.copy(transferredBytes = total, status = TransferStatus.Done) }
                } finally {
                    reader.close()
                }
            } catch (e: CancellationException) {
                updateTask(id) { it.copy(status = TransferStatus.Failed(CANCELLED_MESSAGE)) }
            } catch (e: Exception) {
                updateTask(id) { it.copy(status = TransferStatus.Failed(describe(e))) }
            }
        }
    }

    /** 下载远端文件到本地路径；[totalBytes] 由调用方从目录项带入（<= 0 视为未知）。 */
    fun download(remotePath: String, localPath: String, totalBytes: Long = -1L) {
        val id = newTask(TransferKind.DOWNLOAD, SftpPaths.nameOf(remotePath))
        val flag = AtomicBoolean(false)
        cancelFlags[id] = flag
        scope.launch {
            try {
                val fs = fsOrFail()
                if (totalBytes > 0) updateTask(id) { it.copy(totalBytes = totalBytes) }
                val throttler = TransferThrottler(THROTTLE_MS, THROTTLE_BYTES)
                var transferred = 0L
                val written = withContext(io) {
                    BufferedOutputStream(FileOutputStream(localPath)).use { out ->
                        fs.download(remotePath) { chunk ->
                            throwIfCancelled(flag)
                            out.write(chunk)
                            transferred += chunk.size
                            if (throttler.shouldEmit(clock(), chunk.size.toLong())) {
                                val t = transferred
                                updateTask(id) { it.copy(transferredBytes = t) }
                            }
                        }.also { out.flush() }
                    }
                }
                val final = if (totalBytes > 0) totalBytes else written
                updateTask(id) { it.copy(transferredBytes = final, status = TransferStatus.Done) }
            } catch (e: CancellationException) {
                updateTask(id) { it.copy(status = TransferStatus.Failed(CANCELLED_MESSAGE)) }
            } catch (e: Exception) {
                updateTask(id) { it.copy(status = TransferStatus.Failed(describe(e))) }
            }
        }
    }

    /** 取消任务：置标志位，任务在下一个块边界自行中断（状态置为 FAILED 已取消）。 */
    fun cancel(id: Long) {
        cancelFlags[id]?.set(true)
    }

    private fun newTask(kind: TransferKind, name: String): Long {
        val id = nextId.getAndIncrement()
        val completion = CompletableDeferred<TransferTask>()
        completions[id] = completion
        _transfers.update {
            it + TransferTask(
                id = id,
                kind = kind,
                name = name,
                totalBytes = 0L,
                transferredBytes = 0L,
                status = TransferStatus.Running,
                completion = completion,
            )
        }
        return id
    }

    private fun updateTask(id: Long, transform: (TransferTask) -> TransferTask) {
        var settled: TransferTask? = null
        _transfers.update { list ->
            list.map {
                if (it.id != id) {
                    it
                } else {
                    val next = transform(it)
                    if (next.status !is TransferStatus.Running) settled = next
                    next
                }
            }
        }
        // 只在首次进入终态时完成信号；重复调用（如取消后再次置状态）不会覆盖
        settled?.let { s -> completions[id]?.complete(s) }
    }

    /**
     * 等待指定任务进入终态并返回该终态快照。
     *
     * - 任务已完成/失败：立即返回（completion 已完成的 Deferred 不挂起）；
     * - 任务进行中：挂起直到终态；
     * - id 不存在：返回 null（不抛异常，调用方按「未知任务」处理）。
     *
     * 取消该挂起不会影响任务本身（任务在自己的协程里跑）。
     */
    suspend fun awaitCompletion(id: Long): TransferTask? =
        completions[id]?.await()

    /** 等待当前全部任务进入终态，返回按 id 排序的终态快照列表。 */
    suspend fun awaitAll(): List<TransferTask> {
        val snapshot = _transfers.value
        return snapshot.mapNotNull { awaitCompletion(it.id) }.sortedBy { it.id }
    }

    /**
     * 删除远端条目：文件直接删，空目录用 rmdir；非空目录会被服务端拒绝并原样报错。
     */
    fun delete(entry: SftpEntry) = scope.launch {
        val fs = fsOrNull() ?: return@launch
        val path = SftpPaths.join(_state.value.cwd, entry.name)
        try {
            withContext(io) { fs.delete(path) }
            list(_state.value.cwd)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = describe(e)) }
        }
    }

    /** 重命名当前目录下的 [entry] 为 [newName]；非法名由调用方先行校验。 */
    fun rename(entry: SftpEntry, newName: String) = scope.launch {
        val fs = fsOrNull() ?: return@launch
        val dir = _state.value.cwd
        val from = SftpPaths.join(dir, entry.name)
        val to = SftpPaths.join(dir, newName.trim())
        try {
            withContext(io) { fs.rename(from, to) }
            list(dir)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = describe(e)) }
        }
    }

    private suspend fun fsOrFail(): SftpFs = fsOrNull() ?: throw SftpException(_state.value.error ?: "SFTP 未就绪")

    private fun throwIfCancelled(flag: AtomicBoolean) {
        if (flag.get()) throw CancellationException(CANCELLED_MESSAGE)
    }

    private fun describe(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.toString()

    private companion object {
        const val THROTTLE_MS = 1_000L
        const val THROTTLE_BYTES = 256L * 1024
        const val CANCELLED_MESSAGE = "已取消"
    }
}
