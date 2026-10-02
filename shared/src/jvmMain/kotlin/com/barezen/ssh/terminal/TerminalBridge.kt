// shared/src/jvmMain/kotlin/com/barezen/ssh/terminal/TerminalBridge.kt（T-7：AI 侧栏桥接）
package com.barezen.ssh.terminal

import androidx.compose.runtime.staticCompositionLocalOf
import com.barezen.ssh.ssh.ShellChannel
import com.jediterm.terminal.model.TerminalTextBuffer

/**
 * 终端桥接（T-7）：把当前活动的 [TerminalView] 内部状态以**受控方式**交给 AI 侧栏。
 * 两个方向，都走既有路径、不改动 jediterm 任何交互：
 * - 只读快照：历史 + 屏幕缓冲合并后的最后 N 行（加锁读取）——供「附带终端上下文」；
 * - 受审批写入：命令确认后经 [ShellChannel.write] 落入 TTY 输入，与用户键盘同路径。
 *   审批门禁在 UI 层（AiPanel 确认对话框）；本类只提供唯一的写入入口。
 *
 * 生命周期与 TerminalView 对齐：会话挂上时 [attach]，离开组合时 [detach]。
 * 同时只至多存在一个活动桥接目标（终端屏只组合活动会话的 TerminalView）。
 */
class TerminalBridge {

    private var bufferRef: TerminalTextBuffer? = null
    private var channelRef: ShellChannel? = null

    /** TerminalView 在 shell 通道建立后调用；重复 attach 以最后一次为准。 */
    fun attach(buffer: TerminalTextBuffer, channel: ShellChannel) {
        bufferRef = buffer
        channelRef = channel
    }

    /** TerminalView 离开组合时调用：桥接目标失效（切标签 = 活动会话切走）。 */
    fun detach() {
        bufferRef = null
        channelRef = null
    }

    /**
     * 只读快照：滚动缓冲区（历史 + 屏幕）的最后 [maxLines] 行。行尾空白去除，空串保留
     * （屏幕上的空行对模型同样有意义）。无活动终端时返回空列表。
     */
    fun lastLines(maxLines: Int): List<String> {
        if (maxLines <= 0) return emptyList()
        val buffer = bufferRef ?: return emptyList()
        val texts = mutableListOf<String>()
        buffer.lock()
        try {
            texts += buffer.historyBuffer.getLineTexts()
            texts += buffer.screenBuffer.getLineTexts()
        } finally {
            buffer.unlock()
        }
        return texts.asSequence().map { it.trimEnd() }.toList().takeLast(maxLines)
    }

    /**
     * 审批通过后的唯一写入口：写入命令 + 换行（等价用户敲入回车）。
     * 返回 false 表示当前没有可注入的活动终端通道（无会话 / 会话已切走）。
     */
    fun writeCommand(command: String): Boolean {
        val channel = channelRef ?: return false
        channel.write((command + "\n").toByteArray(Charsets.UTF_8))
        return true
    }
}

/** AppShell 提供活动桥接；无值 = 本树没有挂终端视图（快照/注入不可用）。 */
val LocalTerminalBridge = staticCompositionLocalOf<TerminalBridge?> { null }
