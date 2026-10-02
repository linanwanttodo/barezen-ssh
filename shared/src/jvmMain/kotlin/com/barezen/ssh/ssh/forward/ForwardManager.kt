// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/forward/ForwardManager.kt
package com.barezen.ssh.ssh.forward

import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.ForwardTunnel
import com.barezen.ssh.servers.ForwardRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 单条转发隧道的运行状态。FAILED 时 [ForwardEntry.message] 携带原因。 */
enum class ForwardEntryState { ACTIVE, FAILED }

/** 列表里的一条转发：持久化规则加运行态。tunnel 仅在 ACTIVE 时非空。 */
data class ForwardEntry(
    val spec: ForwardSpec,
    val state: ForwardEntryState,
    val message: String? = null,
    val tunnel: ForwardTunnel? = null,
)

/**
 * 当前连接的端口转发状态层。隧道工厂注入（生产环境是 SshSession.startForward，
 * 测试是假工厂），本类只管状态机：
 * - [apply] 幂等：同 spec 已 ACTIVE 直接返回既有条目，不再开隧道；
 *   已 FAILED 的条目重试（替换原条目）。
 * - 失败不抛：落成 FAILED 条目，UI 展示原因。
 */
class ForwardManager(
    private val tunnelFactory: suspend (ForwardSpec) -> ForwardTunnel,
) {
    private val _entries = MutableStateFlow<List<ForwardEntry>>(emptyList())
    val entries: StateFlow<List<ForwardEntry>> = _entries.asStateFlow()

    suspend fun apply(spec: ForwardSpec): ForwardEntry {
        _entries.value.firstOrNull { it.spec == spec && it.state == ForwardEntryState.ACTIVE }
            ?.let { return it }
        return try {
            val tunnel = tunnelFactory(spec)
            val entry = ForwardEntry(spec, ForwardEntryState.ACTIVE, tunnel = tunnel)
            _entries.update { current -> current.filterNot { it.spec == spec } + entry }
            entry
        } catch (t: Throwable) {
            val entry = ForwardEntry(
                spec,
                ForwardEntryState.FAILED,
                message = t.message ?: t.toString(),
            )
            _entries.update { current -> current.filterNot { it.spec == spec } + entry }
            entry
        }
    }

    /** 移除并关闭隧道；按条目身份匹配，重复调用无副作用。 */
    fun remove(entry: ForwardEntry) {
        entry.tunnel?.close()
        _entries.update { current -> current.filterNot { it === entry } }
    }

    /** 关闭全部隧道并清空列表（断开连接时调用）。 */
    fun closeAll() {
        _entries.value.forEach { it.tunnel?.close() }
        _entries.value = emptyList()
    }

    /** 按持久化规则批量启用 autoStart 的条目；单条失败不阻断其余。 */
    suspend fun applyAll(rules: Collection<ForwardRule>) {
        rules.filter { it.autoStart }.forEach { apply(it.toSpec()) }
    }
}
