// shared/src/jvmMain/kotlin/com/barezen/ssh/settings/OpenSshConfigParser.kt
package com.barezen.ssh.settings

import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth

sealed interface SkipReason {
    data object NoUser : SkipReason
    data object WildcardHost : SkipReason
    data object Duplicate : SkipReason
}

data class ImportResult(
    val added: List<Server>,
    val skipped: List<SkipReason>,
    /** 遇到 Include 指令时为 true —— 必须如实告知用户我们没跟随它 */
    val includeSkipped: Boolean = false,
)

/**
 * `~/.ssh/config` 解析器。
 *
 * 规则（逐条有测试）：
 * - 每个 `Host` 块的每个别名各生成一台服务器；`HostName` 缺省时用第一个别名
 * - 缺 `User` → 跳过（计入 NoUser）；`Port` 缺省 22；`IdentityFile` 缺省=密码认证
 * - `Host` 值含 `*` `?` `!` → 跳过（WildcardHost）
 * - `Include` → 跳过并置 includeSkipped
 * - `#` 注释/空行/未知关键字 → 忽略
 */
object OpenSshConfigParser {

    private val WILDCARD = Regex("""[*?!]""")

    fun parse(text: String): ImportResult {
        val added = mutableListOf<Server>()
        val skipped = mutableListOf<SkipReason>()
        var includeSkipped = false

        var aliases: List<String> = emptyList()
        var hostName: String? = null
        var user: String? = null
        var port: Int = 22
        var identity: String? = null

        fun flush() {
            if (aliases.isEmpty()) return
            if (aliases.any { WILDCARD.containsMatchIn(it) }) {
                skipped += SkipReason.WildcardHost
            } else {
                val u = user
                if (u.isNullOrBlank()) {
                    skipped += SkipReason.NoUser
                } else {
                    val host = hostName?.takeIf { it.isNotBlank() } ?: aliases.first()
                    val auth = identity?.takeIf { it.isNotBlank() }
                        ?.let { StoredAuth.Key(it) } ?: StoredAuth.Password
                    aliases.forEach { alias ->
                        added += Server(
                            id = newId(),
                            name = alias,
                            host = host,
                            port = port,
                            user = u,
                            tags = emptyList(),
                            auth = auth,
                        )
                    }
                }
            }
            aliases = emptyList(); hostName = null; user = null; port = 22; identity = null
        }

        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val key = line.substringBefore(' ').trim()
            val value = line.substringAfter(' ', "").trim()
            when (key.lowercase()) {
                "host" -> { flush(); aliases = value.split(Regex("\\s+")).filter { it.isNotBlank() } }
                "hostname" -> hostName = value
                "user" -> user = value
                "port" -> port = value.toIntOrNull() ?: 22
                "identityfile" -> identity = value
                "include" -> includeSkipped = true
                else -> Unit   // 未知关键字忽略
            }
        }
        flush()
        return ImportResult(added, skipped, includeSkipped)
    }

    // 与 ServersScreen.kt 的既有惯例一致（kotlin.uuid.Uuid + OptIn）
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newId(): String = kotlin.uuid.Uuid.random().toString()
}
