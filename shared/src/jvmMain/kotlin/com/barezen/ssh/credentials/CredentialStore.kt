// shared/src/jvmMain/kotlin/com/barezen/ssh/credentials/CredentialStore.kt
package com.barezen.ssh.credentials

import java.util.concurrent.ConcurrentHashMap

/**
 * 凭据存取抽象：以 id 为键存取秘密（密码或密钥口令）。
 * 实现类负责防御性拷贝——save 后修改传入数组、load 后修改返回数组都不得影响存储值。
 */
interface CredentialStore {
    fun save(id: String, secret: CharArray)
    fun load(id: String): CharArray?
    fun delete(id: String)
    fun isAvailable(): Boolean
}

/** 凭据 id 约定：`server/<serverId>/password` 与 `server/<serverId>/keypass`。 */
object CredentialIds {
    fun password(serverId: String): String = "server/$serverId/password"
    fun keyPassphrase(serverId: String): String = "server/$serverId/keypass"
}

/**
 * 内存实现：钥匙串不可用时的降级存储。进程退出即丢失，UI 需相应提示用户。
 */
class InMemoryCredentialStore : CredentialStore {
    private val secrets = ConcurrentHashMap<String, CharArray>()

    override fun save(id: String, secret: CharArray) {
        secrets[id] = secret.copyOf()
    }

    override fun load(id: String): CharArray? = secrets[id]?.copyOf()

    override fun delete(id: String) {
        secrets.remove(id)
    }

    override fun isAvailable(): Boolean = true
}

/** 平台钥匙串选择：按系统名挑实现；不可用时降级内存存储（UI 提示「凭据仅保存在内存」）。 */
object CredentialStores {
    fun platformDefault(): CredentialStore {
        val os = System.getProperty("os.name").lowercase()
        val native: CredentialStore = when {
            os.contains("win") -> WindowsCredStore()
            os.contains("mac") || os.contains("darwin") -> MacKeychainStore()
            else -> GnomeKeyringStore()
        }
        return if (native.isAvailable()) native else InMemoryCredentialStore()
    }
}
