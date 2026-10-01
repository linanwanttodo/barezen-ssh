// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransfer.kt
package com.barezen.barezen_ssh.settings

import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.StoredAuth
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * 连接导入导出（作用于**连接**，不是设置；设计 §10）。
 *
 * - 导出 JSON：不含 `id`（导入时重新生成，避免跨机器冲突）；**默认脱敏**——
 *   `auth` 只写 `{"type":"key"}`，勾选「包含私钥路径」才写 `keyPath`。
 * - 导出 CSV：UTF-8 with BOM（否则 Excel 打开中文乱码）；标签用 `;` 连接；
 *   含 `,` `"` 或换行的字段双引号包裹、内部 `"` 转义为 `""`。
 * - 去重键：`(host 小写, port, user)`；已存在 → 跳过。
 */
object ConnectionTransfer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------- 导出 JSON（不含 id 的中间 DTO） ----------

    @Serializable
    private data class ExportAuth(val type: String, val keyPath: String? = null)

    @Serializable
    private data class ExportServer(
        val name: String,
        val host: String,
        val port: Int,
        val user: String,
        val tags: List<String>,
        val auth: ExportAuth,
    )

    @Serializable
    private data class ExportFile(
        val schemaVersion: Int = 1,
        val exportedAt: String,
        val servers: List<ExportServer>,
    )

    fun exportJson(servers: List<Server>, includeKeyPath: Boolean): String {
        val dto = ExportFile(
            exportedAt = Instant.now().toString(),
            servers = servers.map { s ->
                ExportServer(
                    name = s.name,
                    host = s.host,
                    port = s.port,
                    user = s.user,
                    tags = s.tags,
                    auth = when (val a = s.auth) {
                        is StoredAuth.Key -> ExportAuth(
                            type = "key",
                            keyPath = a.keyPath.takeIf { includeKeyPath },
                        )
                        StoredAuth.Password -> ExportAuth(type = "password")
                    },
                )
            },
        )
        return json.encodeToString(ExportFile.serializer(), dto)
    }

    // ---------- 导入 JSON ----------

    fun importJson(text: String): List<Server> {
        val file = json.decodeFromString(ExportFile.serializer(), text)
        return file.servers.map { s ->
            Server(
                id = newId(),
                name = s.name,
                host = s.host,
                port = s.port,
                user = s.user,
                tags = s.tags,
                auth = when (s.auth.type) {
                    "key" -> s.auth.keyPath?.let { StoredAuth.Key(it) } ?: StoredAuth.Password
                    else -> StoredAuth.Password
                },
            )
        }
    }

    // ---------- 导出 CSV ----------

    fun exportCsv(servers: List<Server>): String {
        val header = "名称,地址,端口,用户名,标签,认证方式"
        val rows = servers.map { s ->
            listOf(
                s.name,
                s.host,
                s.port.toString(),
                s.user,
                s.tags.joinToString(";"),
                when (s.auth) {
                    is StoredAuth.Key -> "私钥"
                    StoredAuth.Password -> "密码"
                },
            ).joinToString(",") { csvCell(it) }
        }
        // UTF-8 with BOM：否则 Excel 打开中文列名/标签乱码
        return "\uFEFF" + (listOf(header) + rows).joinToString("\n")
    }

    /** 含 `,` `"` 或换行的字段双引号包裹，内部 `"` 转义为 `""`。 */
    private fun csvCell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v

    // ---------- 去重合并 ----------

    /** 按 `(host 小写, port, user)` 去重：incoming 里与 existing 同键的跳过。 */
    fun merge(existing: List<Server>, incoming: List<Server>): ImportResult {
        val seen = existing.map { dedupeKey(it) }.toMutableSet()
        val added = mutableListOf<Server>()
        val skipped = mutableListOf<SkipReason>()
        incoming.forEach { s ->
            val key = dedupeKey(s)
            if (key in seen) {
                skipped += SkipReason.Duplicate
            } else {
                seen += key
                added += s
            }
        }
        return ImportResult(added, skipped)
    }

    private fun dedupeKey(s: Server): Triple<String, Int, String> =
        Triple(s.host.lowercase(), s.port, s.user)

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newId(): String = kotlin.uuid.Uuid.random().toString()
}
