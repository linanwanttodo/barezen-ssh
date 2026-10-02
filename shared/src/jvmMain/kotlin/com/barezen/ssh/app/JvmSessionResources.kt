// shared/src/jvmMain/kotlin/com/barezen/ssh/app/JvmSessionResources.kt
package com.barezen.ssh.app

import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ssh.forward.ForwardManager
import com.barezen.ssh.servers.ForwardRule
import kotlinx.coroutines.CoroutineScope

/**
 * 单条会话的惰性资源容器（jvmMain 实现）：隧道管理器等「绑定在该会话上、且不应随 UI 切换而销毁」的模型。
 *
 * 所有权在会话而非 Host——Host 随标签切换而重建，若模型归 Host 持有，用户切走标签就会把正在使用的
 * 隧道一起关掉（设计 §3.4 方案 A）。放在 jvmMain 是因为 [ForwardManager] 与规则存储同在 jvmMain，
 * commonMain 看不见它们；注册表只经 [SessionScoped] 接口持有本对象。
 *
 * 线程模型：与 [SessionRegistry] 一致，公开方法均在主线程（Compose 重组线程）调用。
 */
class JvmSessionResources(
    @Suppress("unused") private val scope: CoroutineScope,
    private val session: SshSession,
) : SessionScoped {
    // 显式声明成 Lazy：close() 需要靠 isInitialized 判断「这条会话到底碰没碰过转发面板」，
    // 委托属性拿不到这个信息。惰性本身是正确性要求，见 close() 的注释。
    private val _forwardManager: Lazy<ForwardManager> =
        lazy { ForwardManager { spec -> session.startForward(spec) } }

    /** 端口转发管理器；首次访问时创建。会话切走再切回复用同一实例，隧道不断。 */
    val forwardManager: ForwardManager get() = _forwardManager.value

    /** 自动启用是否已执行过（每会话一次）；见 [ensureAutoStartApplied]。 */
    private var autoStartApplied = false

    /** 会话级资源是否已释放（[close] 幂等，且释放后不再新建隧道）。 */
    var closed: Boolean = false
        private set

    /**
     * 「连接时自动启用」是否要执行：**每会话只在首次进入活动会话时执行一次**。
     *
     * Host 以会话 id 为 key 的 LaunchedEffect 会在切走再切回时重跑；若不以会话自己记账，用户来回
     * 切标签就会反复读取规则文件并把 autoStart 转发重新 apply 一遍——对一个**已经被用户手动关掉**
     * 的转发，这就是无人值守地重新打开一个本机监听端口（安全敏感）。
     *
     * [loadRules] 由调用方（Host，jvmMain）注入：规则仓库也在 jvmMain，本类不硬绑具体存储，
     * 注册表侧一行都不用看到 ForwardRule。
     *
     * 返回 true 表示已执行自动启用；false 表示本会话已启用过（或资源已释放），调用方直接跳过。
     *
     * 挂起是因为 [ForwardManager.applyAll] 要等隧道工厂返回；Host 从 LaunchedEffect 里调用即可。
     */
    suspend fun ensureAutoStartApplied(loadRules: () -> List<ForwardRule>): Boolean {
        if (closed || autoStartApplied) return false
        autoStartApplied = true
        forwardManager.applyAll(loadRules())
        return true
    }

    /**
     * 释放全部会话级资源（关标签/断开/建连失败时由 [SessionRegistry] 调用）。幂等。
     *
     * 惰性创建是刻意的：**从没碰过转发面板的会话不会被凭创建出一个隧道管理器**；
     * 但一旦创建过，释放时必须 closeAll——隧道是「关即断」的，不做后台保活
     * （设计 §5.1「不保活」：无人值守地自动重建用户的端口转发是安全敏感行为）。
     */
    override fun close() {
        if (closed) return
        closed = true
        if (_forwardManager.isInitialized()) _forwardManager.value.closeAll()
    }
}
