// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/TofuHostKeyVerifier.kt
package com.barezen.ssh.ssh

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.io.File
import java.security.PublicKey

/**
 * TOFU：known_hosts 已有条目必须匹配；无条目则接受并追加写入。
 *
 * sshj 0.40.0 中顶层 `KnownHostEntry.parseLine/check` 已不存在（javap 核对），改为继承
 * [OpenSSHKnownHosts] 复用其条目解析与匹配，覆写两个钩子以满足语义：
 * - 未知主机 → [hostKeyUnverifiableAction] 追加条目并返回 true（接受）
 * - 同主机已有不同密钥 → [hostKeyChangedAction] 返回 false（拒绝）
 * - 同主机同密钥 → 基类直接返回 true
 *
 * 基类构造器只读一次文件，而本实例在应用中是长生命周期单例；因此每次校验前
 * [rescan] 重扫磁盘，保证“内存快照 == known_hosts”，否则本运行期刚落盘的条目
 * 不可见（重连重复追加；换钥后仍被误判为未知主机而接受）。
 */
class TofuHostKeyVerifier(
    private val knownHosts: File = File(System.getProperty("user.home"), ".ssh/known_hosts"),
) : OpenSSHKnownHosts(knownHosts) {
    private val lock = Any()

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = synchronized(lock) {
        rescan()
        super.verify(hostname, port, key)
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> = synchronized(lock) {
        rescan()
        super.findExistingAlgorithms(hostname, port)
    }

    override fun hostKeyUnverifiableAction(hostname: String, key: PublicKey): Boolean {
        knownHosts.parentFile?.mkdirs()
        write(HostEntry(null, hostname, KeyType.fromKey(key), key))
        return true
    }

    override fun hostKeyChangedAction(hostname: String, key: PublicKey): Boolean = false

    /** 重扫 known_hosts（含外部进程改动）；读取失败时保留上一次快照，绝不降级为“无条目”。 */
    private fun rescan() {
        val fresh = runCatching { OpenSSHKnownHosts(knownHosts) }.getOrNull() ?: return
        entries.clear()
        entries.addAll(fresh.entries())
    }
}
