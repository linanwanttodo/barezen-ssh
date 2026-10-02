// shared/src/commonMain/kotlin/com/barezen/ssh/app/SessionResources.kt
package com.barezen.ssh.app

/**
 * 会话级可释放资源（隧道管理器等）的抽象。
 *
 * 之所以是接口而不是具体类：隧道管理器 ForwardManager 在 jvmMain（它依赖同样在 jvmMain 的
 * ForwardRuleStore），而 [SessionRegistry] 在 commonMain、看不见 jvmMain 的类型。注册表对
 * 会话资源只需要一项能力——**能在会话关闭/断开时把它释放掉**——故收敛成这个 commonMain
 * 可见的最小接口，具体类型由 jvmMain 提供并经注入点交给注册表。
 */
interface SessionScoped : AutoCloseable {
    /** 释放该会话的全部资源；幂等，重复调用无副作用。 */
    override fun close()
}
