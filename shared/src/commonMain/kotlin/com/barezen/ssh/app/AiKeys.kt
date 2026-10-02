// shared/src/commonMain/kotlin/com/barezen/ssh/app/AiKeys.kt（T-7：AI API key 存取端口）
package com.barezen.ssh.app

/**
 * AI API key 存取端口（T-7）。key **绝不进 settings.json**（AppSettings 只放
 * endpoint/model），只经此端口落到系统钥匙串；钥匙串不可用时实现方降级为内存存储，
 * 并如实经 [keychainAvailable] 告知 UI（T-1 模式：降级可见，不静默）。
 */
interface AiKeyStore {
    /** 读取已保存的 key；无或底层读取失败返回 null。 */
    fun loadKey(): String?

    /** 保存 key（覆盖旧值）。失败静默：UI 下次 load 为 null 即可发现。 */
    fun saveKey(key: String)

    /** 删除 key（幂等）。 */
    fun deleteKey()

    /** 底层是否为真实系统钥匙串；内存降级时为 false，UI 须提示「仅保存在内存」。 */
    val keychainAvailable: Boolean

    companion object {
        /** 钥匙串条目 id：与服务器凭据（server/<id>/...）命名空间区分开。 */
        const val KEY_ID = "ai/openai-compat/apikey"
    }
}

/** 端口空实现：无钥匙串、无 key。测试与「AI 功能整体不可用」的场景使用。 */
object NoopAiKeyStore : AiKeyStore {
    override fun loadKey(): String? = null
    override fun saveKey(key: String) = Unit
    override fun deleteKey() = Unit
    override val keychainAvailable: Boolean = false
}
