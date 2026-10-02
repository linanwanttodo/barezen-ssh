// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/FakeSftpFs.kt
package com.barezen.ssh.ssh.sftp

import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.SftpFs
import com.barezen.ssh.ssh.SftpException

/**
 * 测试用 SftpFs：内存目录树 + 预设下载块 + 收集上传块，可注入失败与分块间钩子（不依赖 sshj）。
 */
class FakeSftpFs(
    /** 目录路径 -> 该目录下的条目（保持传入顺序，用于验证模型排序）。 */
    var tree: Map<String, List<SftpEntry>> = emptyMap(),
    var listError: SftpException? = null,
    var downloadChunks: List<ByteArray> = emptyList(),
    var downloadError: SftpException? = null,
    /** 每块交付前调用（index 从 0 起），可用于注入取消/失败时机。 */
    var beforeChunk: ((Int) -> Unit)? = null,
    /** 非空时收集上传的每块内容。 */
    var uploadSink: MutableList<ByteArray>? = null,
    var uploadError: SftpException? = null,
    var deleteError: SftpException? = null,
    var renameError: SftpException? = null,
    /** 每块写入前调用（上传块边界注入阻塞/取消时机）。 */
    var beforeUploadChunk: (() -> Unit)? = null,
) : SftpFs {
    val listedDirs = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    val uploadTargets = mutableListOf<Pair<String, Long>>()
    val mkdirs = mutableListOf<String>()
    val deletes = mutableListOf<String>()
    val renames = mutableListOf<Pair<String, String>>()
    var closed = false

    override fun list(dir: String): List<SftpEntry> {
        listedDirs += dir
        listError?.let { throw it }
        return tree[dir] ?: emptyList()
    }

    override fun mkdir(dir: String) {
        mkdirs += dir
    }

    override fun delete(path: String) {
        deletes += path
        deleteError?.let { throw it }
    }

    override fun rename(oldPath: String, newPath: String) {
        renames += oldPath to newPath
        renameError?.let { throw it }
    }

    override fun download(remotePath: String, onChunk: (ByteArray) -> Unit): Long {
        downloads += remotePath
        downloadError?.let { throw it }
        var total = 0L
        downloadChunks.forEachIndexed { index, chunk ->
            beforeChunk?.invoke(index)
            onChunk(chunk)
            total += chunk.size
        }
        return total
    }

    override fun upload(remotePath: String, size: Long, nextChunk: (offset: Long) -> ByteArray?) {
        uploadTargets += remotePath to size
        uploadError?.let { throw it }
        var offset = 0L
        while (true) {
            beforeUploadChunk?.invoke()
            val chunk = nextChunk(offset) ?: break
            uploadSink?.add(chunk)
            offset += chunk.size
        }
    }

    override fun close() {
        closed = true
    }
}
