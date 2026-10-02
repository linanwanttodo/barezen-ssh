// shared/src/jvmMain/kotlin/com/barezen/ssh/servers/ForwardRuleStore.kt
package com.barezen.ssh.servers

import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 持久化转发规则（`~/.barezen/forwards.json`）。规则以四元组
 * (kind, bindPort, targetHost, targetPort) 为身份；autoStart 决定连接建立时是否自动启用。
 */
@Serializable
data class ForwardRule(
    val kind: ForwardKind,
    val bindPort: Int,
    val targetHost: String,
    val targetPort: Int,
    val autoStart: Boolean = true,
) {
    fun toSpec(): ForwardSpec = ForwardSpec(kind, bindPort, targetHost, targetPort)

    companion object {
        fun of(spec: ForwardSpec, autoStart: Boolean = true): ForwardRule =
            ForwardRule(spec.kind, spec.bindPort, spec.targetHost, spec.targetPort, autoStart)
    }
}

interface ForwardRuleStore {
    fun list(): List<ForwardRule>
    fun upsert(rule: ForwardRule)
    fun delete(rule: ForwardRule)
}

/**
 * `~/.barezen/forwards.json` 的读写。数据安全约定与 [FileServerRepository] 一致：
 * - [list] 只读无副作用，读不出返回空列表（应用要能起来）；
 * - 写路径发现文件不可读时先留存 `forwards.json.corrupt-<时间戳>` 副本再继续，
 *   绝不静默抹掉用户数据；
 * - 写入原子替换（先写 `.tmp` 再 move），避免半截 JSON 落盘。
 */
class FileForwardRuleStore(
    private val file: File = File(System.getProperty("user.home"), ".barezen/forwards.json"),
) : ForwardRuleStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val lock = Any()

    override fun list(): List<ForwardRule> = synchronized(lock) {
        runCatching { readStrict() }.getOrDefault(emptyList())
    }

    override fun upsert(rule: ForwardRule) = synchronized(lock) {
        val current = readForWrite().filterNot { it.kind == rule.kind && it.specMatches(rule) }
        write(current + rule)
    }

    override fun delete(rule: ForwardRule) = synchronized(lock) {
        write(readForWrite().filterNot { it.kind == rule.kind && it.specMatches(rule) })
    }

    private fun ForwardRule.specMatches(other: ForwardRule): Boolean =
        bindPort == other.bindPort && targetHost == other.targetHost && targetPort == other.targetPort

    /** 严格读：文件缺失 = 空列表；存在但读不出/解析不了 = 抛（由调用方决定收口方式）。 */
    private fun readStrict(): List<ForwardRule> {
        if (!file.exists()) return emptyList()
        return json.decodeFromString<List<ForwardRule>>(file.readText())
    }

    /** 写路径专用读：不可读时先留存副本再以空列表继续，保证写入不会抹掉旧数据。 */
    private fun readForWrite(): List<ForwardRule> = try {
        readStrict()
    } catch (cause: Exception) {
        quarantine(cause)
        emptyList()
    }

    /**
     * 把不可读的原文件改名留存为 `<name>.corrupt-<epochMillis>`。
     * 副本未落地时抛出——宁可写入失败，也不覆盖唯一一份用户数据。
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

    /** 原子写：先写同目录 `.tmp` 再 move 覆盖。 */
    private fun write(rules: List<ForwardRule>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(rules))
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
