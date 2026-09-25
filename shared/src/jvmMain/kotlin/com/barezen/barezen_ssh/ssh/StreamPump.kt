// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/StreamPump.kt（Task 7：独立可测的读泵）
package com.barezen.barezen_ssh.ssh

import java.io.InputStream

/**
 * 把 [input] 的字节流逐块投递给 [onChunk]，结束时恰好回调一次 [onClose]
 * （正常 EOF 传 null，异常传异常本身）。独立于 sshj，可用普通流做单元测试。
 */
class StreamPump(private val onChunk: (ByteArray) -> Unit) {
    fun start(input: InputStream, onClose: (Throwable?) -> Unit) {
        Thread({
            try {
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) onChunk(buf.copyOf(n))
                }
                onClose(null)
            } catch (e: Exception) {
                onClose(e)
            }
        }, "stream-pump").apply { isDaemon = true; start() }
    }
}
