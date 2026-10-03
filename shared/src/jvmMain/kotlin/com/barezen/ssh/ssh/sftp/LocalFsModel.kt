// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/sftp/LocalFsModel.kt
package com.barezen.ssh.ssh.sftp

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地文件系统浏览模型。
 *
 * 与 [SftpModel] 平级：两者各自管一侧的目录状态，文件页把两个 pane 并排呈现。
 * 这里**只做浏览**（列出目录、进出目录），不做传输——传输由 [SftpModel] 的
 * upload/download 按绝对路径发起，避免两处各自实现一遍流式读写。
 *
 * 所有磁盘 IO 都在 [io] 上跑（默认 Dispatchers.IO），不在组合线程读目录。
 */
class LocalFsModel(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    initialDir: File = File(System.getProperty("user.home")),
) {
    private val _state = MutableStateFlow(LocalFsState(cwd = initialDir.absolutePath))
    val state: StateFlow<LocalFsState> = _state.asStateFlow()

    init {
        // 首次组合就加载：否则本地 pane 要等用户点一次刷新才出内容
        scope.launch { load(initialDir) }
    }

    /** 进入子目录；[name] 传 ".." 表示上级。非法名（分隔符 / .. / .）在前端就被拒绝。 */
    fun enter(name: String) {
        scope.launch {
            val current = File(_state.value.cwd)
            val next = if (name == "..") current.parentFile ?: return@launch else File(current, name)
            if (!next.isDirectory || !next.canRead()) {
                // 进不去就如实说，不静默留在原处假装成功
                _state.value = _state.value.copy(error = "无法打开目录：${next.absolutePath}")
                return@launch
            }
            load(next)
        }
    }

    /** 跳到指定绝对路径（如「打开目录」对话框选回来的）。 */
    fun openPath(path: String) {
        scope.launch {
            val f = File(path)
            if (!f.isDirectory) {
                _state.value = _state.value.copy(error = "不是目录：$path")
                return@launch
            }
            load(f)
        }
    }

    fun refresh() {
        scope.launch { load(File(_state.value.cwd)) }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun load(dir: File) {
        val result: Result<List<LocalEntry>> = withContext(io) {
            val children = dir.listFiles()
                ?: return@withContext Result.failure(
                    IllegalStateException("无法读取目录：${dir.absolutePath}")
                )
            Result.success(children.map { f ->
                // symlink 指向的目录也算目录（能进去）
                val isDir = f.isDirectory
                LocalEntry(
                    name = f.name,
                    isDirectory = isDir,
                    size = if (isDir) 0L else f.length(),
                    mtimeMs = f.lastModified(),
                )
            }.sortedWith(
                // 与远端 EntrySorter 同规则：目录在前、隐藏文件垫后、名称不区分大小写
                compareByDescending<LocalEntry> { it.isDirectory }
                    .thenBy { it.name.startsWith(".") }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name }
            ))
        }
        _state.value = if (result.isFailure) {
            _state.value.copy(error = result.exceptionOrNull()?.message)
        } else {
            LocalFsState(cwd = dir.absolutePath, entries = result.getOrDefault(emptyList()))
        }
    }
}

/** 本地目录浏览状态。 */
data class LocalFsState(
    val cwd: String,
    val entries: List<LocalEntry> = emptyList(),
    val error: String? = null,
) {
    /** 根目录没有上级（Windows 盘符根与 POSIX / 都不能再往上）。 */
    val canGoUp: Boolean
        get() {
            val f = File(cwd)
            return f.parentFile != null && f.parentFile.absolutePath != cwd
        }
}

/** 本地条目。与远端 [com.barezen.ssh.ssh.SftpEntry] 字段对齐，便于复用同一套列表行。 */
data class LocalEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val mtimeMs: Long,
)
