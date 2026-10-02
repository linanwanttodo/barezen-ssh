// shared/src/commonMain/kotlin/com/barezen/ssh/settings/SettingsRepository.kt
package com.barezen.ssh.settings

/** 设置持久化。UI 无关。 */
interface SettingsRepository {
    /** **永不抛。** 失败时内部隔离副本并回落默认值，由返回值如实报告。 */
    fun load(): SettingsLoad

    /** 失败抛 [SettingsWriteException]。 */
    fun save(settings: AppSettings)
}

sealed interface SettingsLoad {
    /** 正常读取（含"文件不存在"与"值被收敛"两种情况，后者 warnings 非空）。 */
    data class Ok(val settings: AppSettings, val warnings: List<String>) : SettingsLoad

    /**
     * 文件不可读/解析失败 → 已把原文件隔离为副本，并回落默认值。
     * [quarantinePath] 为隔离副本的绝对路径。
     */
    data class Recovered(
        val settings: AppSettings,
        val quarantinePath: String?,
        val cause: String,
    ) : SettingsLoad
}

class SettingsWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)
