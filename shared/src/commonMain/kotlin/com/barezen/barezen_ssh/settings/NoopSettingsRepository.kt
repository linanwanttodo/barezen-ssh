// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/NoopSettingsRepository.kt
package com.barezen.barezen_ssh.settings

/**
 * 测试用：不碰磁盘的内存仓库。
 *
 * 与 [com.barezen.barezen_ssh.servers.InMemoryServerRepository] 同例，作为测试支撑的内存实现
 * 放在 commonMain（`forUiTest()` 这一工厂位于生产代码 AppModel.kt，必须能在 commonMain 拿到它）。
 */
class NoopSettingsRepository(
    private var current: AppSettings = AppSettings.Default,
) : SettingsRepository {
    override fun load(): SettingsLoad = SettingsLoad.Ok(current, emptyList())
    override fun save(settings: AppSettings) { current = settings }
}
