// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `~/.barezen/settings.json` 的读写。
 *
 * 两条路径刻意分开：
 * - [load] 只读，**不对磁盘产生副作用**（除了"文件坏掉时把它改名隔离"这一必要动作）。
 * - [save] 写前先确保不会覆盖掉一份**读不出来的**用户配置 —— 先隔离再写。
 *
 * 写入是**原子替换**（先写同目录 `.tmp` 再 move）：避免写到一半崩溃留下半截 JSON，
 * 那正是制造出"不可读文件"的最可能路径。
 */
class FileSettingsRepository(
    private val file: File = File(System.getProperty("user.home"), ".barezen/settings.json"),
) : SettingsRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // 前向兼容：新版本写的字段，旧版本读到不炸
        encodeDefaults = true      // 默认值也写盘；否则改回默认值时字段消失，导出/diff 莫名其妙
        coerceInputValues = true   // 未知枚举值 → 该属性默认值（而不是抛）
        isLenient = false
    }

    override fun load(): SettingsLoad {
        if (!file.exists()) return SettingsLoad.Ok(AppSettings.Default, emptyList())
        return try {
            val parsed = json.decodeFromString<AppSettings>(file.readText())
            val s = parsed.sanitized()
            SettingsLoad.Ok(s.value, s.warnings)
        } catch (cause: Exception) {
            // 读不出来 → 隔离（留证据）再回落默认值。绝不静默重置。
            val quarantined = quarantine(cause)
            SettingsLoad.Recovered(AppSettings.Default, quarantined, cause.message ?: cause.toString())
        }
    }

    override fun save(settings: AppSettings) {
        // 写之前先确认现有文件是可读的；不可读就先隔离，避免覆盖掉唯一一份用户配置。
        if (file.exists()) {
            try {
                json.decodeFromString<AppSettings>(file.readText())
            } catch (cause: Exception) {
                quarantine(cause)
            }
        }
        try {
            writeAtomically(settings)
        } catch (cause: Exception) {
            throw SettingsWriteException("设置写入失败：${cause.message}", cause)
        }
    }

    /**
     * 把不可读的原文件改名留存为 `<name>.corrupt-<epochMillis>`，返回副本绝对路径。
     * 改名失败或副本没落地时**抛异常中止** —— 宁可让这次操作失败，也不能丢掉用户的配置线索。
     */
    private fun quarantine(cause: Exception): String {
        val target = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        runCatching { file.renameTo(target) }
        if (!target.exists()) {
            throw SettingsWriteException("${file.name} 不可读，且无法留存副本；已中止以免丢失配置", cause)
        }
        return target.absolutePath
    }

    private fun writeAtomically(settings: AppSettings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(settings))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
