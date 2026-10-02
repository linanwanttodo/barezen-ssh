// shared/src/jvmMain/kotlin/com/barezen/ssh/ssh/TerminalTtyConnector.kt（Task 7：JediTerm 适配）
package com.barezen.ssh.ssh

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/**
 * 把 [ShellChannel] 适配成 JediTerm 3.73 的 [TtyConnector]。
 *
 * 方法集以 jediterm-core-3.73 sources/javap 为准（与旧版接口不同，任务简报草图中的
 * `init()`、`getCharset()`、`isInteractive()`、`getWidth()`、`getHeight()` 在 3.73 中不存在）：
 * 必须实现 `read/write(byte[])/write(String)/isConnected/waitFor/ready/getName/close`，
 * `init(Questioner)` 是已弃用的 default（返回 true，无需覆盖），
 * `resize(TermSize)` 必须覆盖——default 链最终抛 IllegalStateException。
 *
 * 数据通路：shell 输出经 [onData] 入阻塞队列，JediTerm 模拟器线程在 [read] 中取走显示。
 * JediTerm 侧的消费缓冲只有 1024 字符（TtyBasedArrayDataStream），单块超过该长度的输出
 * 若直接截断会丢字，因此用 String 残缓冲把未消费的字符留到下一次 read。
 */
class TerminalTtyConnector(private val channel: ShellChannel) : TtyConnector {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val closeLatch = CountDownLatch(1)
    @Volatile private var closed = false
    private var residual: String = ""

    /** shell 读线程回调：块数据入队（会话关闭后拒绝新数据）。 */
    fun onData(chunk: ByteArray) {
        if (!closed) queue.put(chunk)
    }

    /** shell 读线程回调：没有更多输出了（EOF 与异常统一按“会话结束”处理，错误分类留给上层）。 */
    fun markClosed() {
        closed = true
        queue.put(SENTINEL)
        closeLatch.countDown()
    }

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        while (residual.isEmpty()) {
            // 关闭后队列已空/哨兵已被消费时避免二次阻塞
            if (closed && queue.isEmpty()) return -1
            val bytes = queue.take()
            if (bytes.isEmpty() && closed) return -1
            if (bytes.isNotEmpty()) residual = String(bytes, Charsets.UTF_8)
        }
        val n = minOf(length, residual.length)
        residual.toCharArray(0, n).copyInto(buf, destinationOffset = offset)
        residual = residual.substring(n)
        return n
    }

    override fun write(bytes: ByteArray) {
        channel.write(bytes)
    }

    override fun write(string: String) {
        channel.write(string.toByteArray(Charsets.UTF_8))
    }

    override fun resize(size: TermSize) {
        channel.resize(size.columns, size.rows)
    }

    override fun isConnected() = !closed

    override fun waitFor(): Int {
        closeLatch.await()
        return 0
    }

    override fun ready(): Boolean = residual.isNotEmpty() || queue.isNotEmpty()

    override fun getName() = "ssh"

    override fun close() {
        closed = true
        queue.offer(SENTINEL) // 解除 read() 阻塞，避免模拟器线程悬挂
        closeLatch.countDown()
        channel.close()
    }

    private companion object {
        val SENTINEL = ByteArray(0)
    }
}
