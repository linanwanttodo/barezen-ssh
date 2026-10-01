package com.barezen.barezen_ssh.servers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `~/.barezen/servers.json` 的读写。
 *
 * **数据安全约定（本轮修复）**：原实现的 `read()` 用 catch-all
 * （`runCatching { decode }.getOrDefault(emptyList())`）把「文件不存在」与「文件存在但读不出/解析不了」
 * 混为一谈。于是一旦 servers.json 损坏或不可读，任意一次 `upsert`/`delete` 都会以空列表为基底写入，
 * **把用户整份服务器列表静默抹掉**。现拆成两条路径：
 *
 * - **只读路径** [list]：保持韧性，读不出就返回空列表（应用要能起来），且**不改动磁盘**（无副作用）。
 * - **写路径** [upsert]/[delete]：基底列表经 [readForWrite] 取得；若文件存在却不可读，
 *   先把它**留存为 `servers.json.corrupt-<时间戳>` 副本**再以空列表继续 —— 宁可"从头开始"，
 *   也绝不原地销毁用户数据。
 *
 * 另外 [write] 改为**原子替换**（先写 `.tmp` 再 move），避免写到一半崩溃/断电留下半截 JSON，
 * 那正是最可能制造出"不可读文件"的路径。
 */
class FileServerRepository(
    private val file: File = File(System.getProperty("user.home"), ".barezen/servers.json"),
) : ServerRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()

    override fun list(): List<Server> = synchronized(lock) {
        runCatching { readStrict() }.getOrDefault(emptyList())
    }

    override fun upsert(server: Server) = synchronized(lock) {
        val cur = readForWrite().filterNot { it.id == server.id } + server
        write(cur)
    }

    override fun delete(id: String) = synchronized(lock) {
        write(readForWrite().filterNot { it.id == id })
    }

    /** 严格读：文件缺失 = 空列表；存在但读不出/解析不了 = 抛（由调用方决定如何收口）。 */
    private fun readStrict(): List<Server> {
        if (!file.exists()) return emptyList()
        return json.decodeFromString<List<Server>>(file.readText())
    }

    /** 写路径专用读：不可读时先留存副本再以空列表继续，保证写入不会抹掉旧数据。 */
    private fun readForWrite(): List<Server> = try {
        readStrict()
    } catch (cause: Exception) {
        quarantine(cause)
        emptyList()
    }

    /**
     * 把不可读的原文件改名留存为 `<name>.corrupt-<epochMillis>`。
     * 改名失败或副本未落地时**抛出**——此时宁可让写入失败，也不能覆盖掉唯一的一份用户数据。
     */
    private fun quarantine(cause: Exception) {
        val target = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        runCatching { file.renameTo(target) }
        if (!target.exists()) {
            throw IllegalStateException(
                "${file.name} 不可读，且无法留存副本；已中止写入以免丢失用户数据",
                cause,
            )
        }
    }

    /** 原子写：先写同目录 `.tmp` 再 move 覆盖，避免半截 JSON 落盘。 */
    private fun write(servers: List<Server>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(servers))
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
