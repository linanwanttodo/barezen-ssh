// shared/src/commonMain/kotlin/com/barezen/ssh/credentials/CredentialResolver.kt
package com.barezen.ssh.credentials

import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod

/**
 * 凭据解析端口（commonMain 纯接口，无平台依赖）。
 *
 * 平台钥匙串实现位于 jvmMain（`KeychainCredentialResolver`），由壳层注入；
 * [com.barezen.ssh.app.AppModel] 只依赖本接口，故 commonMain 不出现任何平台调用。
 *
 * 约定：
 * - [load] 返回 null 表示「钥匙串中没有该服务器的凭据」，调用方回退到让用户手工输入（不报错）；
 * - [remember] 仅在用户显式勾选「记住凭据」时调用，禁止隐式落盘；
 * - [forget] 必须幂等（条目不存在时静默返回），服务器删除时调用，避免孤儿条目泄漏；
 * - 所有方法都必须允许在钥匙串不可用时被安全调用（[InMemoryCredentialResolver] / [NoopCredentialResolver]）。
 */
interface CredentialResolver {
    /** 取出该服务器已保存的认证方式；无条目返回 null。 */
    fun load(server: Server): AuthMethod?

    /** 把认证方式存入钥匙串（用户显式选择「记住凭据」时）。 */
    fun remember(server: Server, auth: AuthMethod)

    /** 删除该服务器的全部凭据条目；幂等。 */
    fun forget(serverId: String)

    /** 平台钥匙串是否可用；false 时 UI 必须提示「凭据仅保存在内存」。 */
    fun isAvailable(): Boolean
}

/**
 * 空实现：不存取任何凭据，行为与 T-1 之前的现状完全一致。
 * 作为 [com.barezen.ssh.app.AppModel] 的默认参数，保证既有测试与调用点零改动。
 */
object NoopCredentialResolver : CredentialResolver {
    override fun load(server: Server): AuthMethod? = null
    override fun remember(server: Server, auth: AuthMethod) {}
    override fun forget(serverId: String) {}
    override fun isAvailable(): Boolean = false
}
