// shared/src/commonMain/kotlin/com/barezen/ssh/app/SettingsModel.kt
package com.barezen.ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.ssh.settings.AppSettings
import com.barezen.ssh.settings.SettingsLoad
import com.barezen.ssh.settings.SettingsRepository
import com.barezen.ssh.settings.SettingsWriteException

/**
 * 设置的内存态 + 即时落盘。
 *
 * 保存策略是**即时生效**（无脏态、无"保存"按钮）：桌面工具即时反馈更顺，
 * 而脏标记 + 丢失提示是纯附加复杂度。
 *
 * 两条不可动摇的行为：
 * 1. **写失败不回滚内存态** —— 回滚会让用户以为白改了一次。
 * 2. **凡是"没做/被改/失败"都要说出来** —— 不静默（[loadNotice] / [saveError]）。
 */
class SettingsModel(private val repo: SettingsRepository) {

    var settings: AppSettings by mutableStateOf(AppSettings.Default)
        private set

    /** 加载期告知（文件被隔离 / 值被收敛）。可关闭。 */
    var loadNotice: String? by mutableStateOf(null)
        private set

    /** 最近一次写盘失败的原因。可关闭。 */
    var saveError: String? by mutableStateOf(null)
        private set

    fun load() {
        when (val r = repo.load()) {
            is SettingsLoad.Ok -> {
                settings = r.settings
                loadNotice = r.warnings.takeIf { it.isNotEmpty() }
                    ?.joinToString("；", prefix = "已修正无效设置：")
            }
            is SettingsLoad.Recovered -> {
                settings = r.settings
                val where = r.quarantinePath?.substringAfterLast('/') ?: "（隔离失败）"
                loadNotice = "设置文件无法读取，已重置为默认值；原文件已保存为 $where。"
            }
        }
    }

    /** 改一项并立即落盘。写失败时保留内存态并暴露 [saveError]。 */
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        try {
            repo.save(next)
            saveError = null
        } catch (e: SettingsWriteException) {
            saveError = "保存失败：${e.message}。改动已生效但未能写入磁盘。"
        }
    }

    fun dismissLoadNotice() { loadNotice = null }
    fun dismissSaveError() { saveError = null }
}
