// shared/src/jvmMain/kotlin/com/barezen/ssh/credentials/KeychainCredentialResolver.kt
package com.barezen.ssh.credentials

import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.ssh.AuthMethod

/**
 * 平台钥匙串适配器：把 jvmMain 的 [CredentialStore] 接到 commonMain 的 [CredentialResolver] 端口。
 *
 * 可用性语义（重要）：[isAvailable] 回答的是「底层是不是真的系统钥匙串」，而不是「能不能存取」。
 * [CredentialStores.platformDefault] 在原生钥匙串不可用时会静默降级成 [InMemoryCredentialStore]，
 * 而它的 [CredentialStore.isAvailable] 恒为 true；若直接转发就会让降级后的 UI 误以为凭据已落钥匙串。
 * 因此原生可用性由 [platformDefault] 工厂在选存储之前按平台判定并显式传入（见 [probeNativeStore]），
 * 进程内不再随环境漂移。降级时存取仍然工作（走内存），只是 [isAvailable] 如实返回 false，
 * 供 UI 提示「凭据仅保存在内存」。
 *
 * 约定（与 [CredentialResolver] 文档一致）：
 * - [load] 不抛异常：无条目、条目与 [Server.auth] 类型不匹配、底层钥匙串报错都返回 null，
 *   调用方据此回退到让用户手工输入；
 * - **keyPath 一律取自 [Server.auth] 的 [StoredAuth.Key.keyPath]，钥匙串只提供口令**，
 *   [load] 绝不从钥匙串内容反推或覆盖密钥路径；
 * - 无口令条目（null 或空）时 [load] 返回 null 而非 PrivateKey(keyPath, null)：
 *   「无存储凭据＝回退现状」是端口语义，让调用方与「从未记住过」走同一条路径；
 * - [remember] 不外溢异常：钥匙串拒绝写入（[CredentialStoreException]）时静默降级，
 *   连接流程照常继续，只是下次仍需重新输入；
 * - [forget] 幂等：两条 id 都尝试删除，条目不存在导致的异常一律吞掉
 *   （GnomeKeyringStore.delete / MacKeychainStore.delete 在条目缺失时退出码非 0 即抛），
 *   保证删除服务器流程绝不因清理凭据而失败。
 */
class KeychainCredentialResolver(
    private val store: CredentialStore = CredentialStores.platformDefault(),
    /** 底层是否为真实系统钥匙串；见类 KDoc。 */
    private val nativeAvailable: Boolean = probeNativeStore() != null,
) : CredentialResolver {

    override fun load(server: Server): AuthMethod? {
        val secret = tryRead(secretIdFor(server))
        return when (val auth = server.auth) {
            is StoredAuth.Password -> secret?.let { AuthMethod.Password(String(it)) }
            is StoredAuth.Key -> secret?.let { AuthMethod.PrivateKey(auth.keyPath, String(it)) }
        }
    }

    override fun remember(server: Server, auth: AuthMethod) {
        when (auth) {
            is AuthMethod.Password -> saveOrIgnore(CredentialIds.password(server.id), auth.value.toCharArray())
            is AuthMethod.PrivateKey -> {
                val passphrase = auth.passphrase
                if (passphrase.isNullOrEmpty()) {
                    // 明文私钥：清掉可能残留的旧口令，否则下次连接会拿旧口令去解新密钥
                    deleteOrIgnore(CredentialIds.keyPassphrase(server.id))
                } else {
                    saveOrIgnore(CredentialIds.keyPassphrase(server.id), passphrase.toCharArray())
                }
            }
        }
    }

    override fun forget(serverId: String) {
        deleteOrIgnore(CredentialIds.password(serverId))
        deleteOrIgnore(CredentialIds.keyPassphrase(serverId))
    }

    /** 见类 KDoc：反映原生钥匙串是否可用，而非存储是否可读写（内存降级时恒为 false）。 */
    override fun isAvailable(): Boolean = nativeAvailable

    /** 只有与 [Server.auth] 类型相符的那条 id 才是本服务器的凭据；另一条不该被误读。 */
    private fun secretIdFor(server: Server): String = when (server.auth) {
        is StoredAuth.Password -> CredentialIds.password(server.id)
        is StoredAuth.Key -> CredentialIds.keyPassphrase(server.id)
    }

    private fun tryRead(id: String): CharArray? = try {
        store.load(id)
    } catch (e: CredentialStoreException) {
        null
    }

    private fun saveOrIgnore(id: String, secret: CharArray) {
        try {
            store.save(id, secret)
        } catch (e: CredentialStoreException) {
            // 钥匙串不可用或拒绝写入：不向 UI 抛异常，用户下次仍需手工输入
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun deleteOrIgnore(id: String) {
        try {
            store.delete(id)
        } catch (e: CredentialStoreException) {
            // 条目不存在或钥匙串不可用：幂等语义要求静默返回
        }
    }

    companion object {
        /**
         * 平台入口：原生钥匙串可用则返回读写真钥匙串的适配器，否则返回读写内存降级存储的适配器，
         * 后者的 [isAvailable] 为 false，供 UI 提示「凭据仅保存在内存」。
         */
        fun platformDefault(): KeychainCredentialResolver = KeychainCredentialResolver(
            store = CredentialStores.platformDefault(),
            nativeAvailable = probeNativeStore() != null,
        )

        /**
         * 按当前平台探测原生钥匙串，返回「真的系统钥匙串」实例；不可用时返回 null。
         * [CredentialStores.platformDefault] 会吞掉这层区别（降级成 [InMemoryCredentialStore]
         * 且其 isAvailable 恒为 true），故这里独立探测一次。探测失败一律视为不可用。
         *
         * 注意：本方法的平台选型必须与 [CredentialStores.platformDefault] 的选型逻辑保持一致
         * （win -> WindowsCredStore / mac,darwin -> MacKeychainStore / 其余 -> GnomeKeyringStore）。
         * 后者是禁改文件，无法抽出共用判定；**若其选型改动，必须同步这里**，否则两处会漂移，
         * 导致本适配器读写的存储与实际降级判定不一致。
         */
        fun probeNativeStore(): CredentialStore? {
            val native: CredentialStore = try {
                val os = System.getProperty("os.name").orEmpty().lowercase()
                when {
                    os.contains("win") -> WindowsCredStore()
                    os.contains("mac") || os.contains("darwin") -> MacKeychainStore()
                    else -> GnomeKeyringStore()
                }
            } catch (e: RuntimeException) {
                return null
            }
            return try {
                if (native.isAvailable()) native else null
            } catch (e: RuntimeException) {
                null
            }
        }
    }
}
