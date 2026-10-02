// shared/src/jvmMain/kotlin/com/barezen/ssh/terminal/TerminalView.kt（Task 7：终端组件）
package com.barezen.ssh.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.ssh.TerminalTtyConnector
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Color
import java.awt.Font
import com.barezen.ssh.settings.AppSettings

/**
 * 终端组件：JediTermWidget + SSH 会话管道。三条语义（3.73 挂接方式以 sources 核对为准）：
 * ① widget 持有 connector 并启动读循环——`setTtyConnector` 后 `start()`，
 *    模拟器线程经 TtyBasedArrayDataStream 反复调用 connector.read；
 * ② shell 输出 onData → connector 队列 → read → widget 显示；
 * ③ 用户键盘输入 → JediTerm 写入 connector.write → channel.write。
 */
@Composable
fun TerminalView(
    session: SshSession,
    settings: AppSettings = AppSettings.Default,
    modifier: Modifier = Modifier,
) {
    // T-7：AI 侧栏桥接。AppShell 提供 LocalTerminalBridge；无值（未挂侧栏）时 null，
    // 终端行为完全不变。桥接只拿只读缓冲快照与审批后的写入入口。
    val bridge = LocalTerminalBridge.current
    val widget = remember(session) {
        JediTermWidget(BareZenTerminalSettings(settings.copyOnSelect)).apply {
            //SwingPanel 的 background 参数已弃用（compose 1.12.1）：按其指引在组件创建时手动设置，同为 #1E1E1E
            background = Color(TerminalPalette.Background)
        }
    }
    DisposableEffect(session) {
        // startShell 的 onData 依赖 connector，connector 构造又依赖返回的 channel：
        // 先用转发壳建 connector 再 startShell，消除 shell 读线程早于 connector 就绪的投递窗口
        // （转发目标只会在 startShell 返回之后被用户输入/尺寸变化/释放触达）。
        lateinit var channel: ShellChannel
        val connector = TerminalTtyConnector(object : ShellChannel {
            override fun write(bytes: ByteArray) = channel.write(bytes)
            override fun resize(cols: Int, rows: Int) = channel.resize(cols, rows)
            override fun close() = channel.close()
        })
        channel = session.startShell(onData = connector::onData, onClosed = { connector.markClosed() })
        widget.setTtyConnector(connector)
        widget.start()
        // T-7：通道就绪后把只读缓冲与写入口交给桥（仅当壳层提供了桥时）
        bridge?.attach(widget.terminalTextBuffer, channel)
        onDispose {
            // 先解除桥接（AI 侧栏立即失去快照/注入能力），再关 connector：
            // 置 isConnected=false，widget.close() 打断阻塞中的 read 时
            // 模拟器线程的异常会被静默吞掉；同时关闭 SSH 通道。onClosed 无论正常关闭还是
            // 连接错误（Task 6 已知延后项）都只表示“没有更多输出”，错误分类留给上层状态。
            bridge?.detach()
            connector.close()
            widget.close()
        }
    }
    // key(session)：SwingPanel 的 interop holder 是无 key 的 remember，只认首次 factory 结果；
    // 会话切换时换 key 重建整个组，才能让新 widget 真正挂到面板上。
    key(session) {
        SwingPanel(factory = { widget }, modifier = modifier)
    }
}

/**
 * 终端配色与字体：bg #1E1E1E、fg #CCCCCC、JetBrains Mono 13。
 * sources 核对（简报草图的 `get理想Foreground` 为乱码）：JediTermWidget.createDefaultStyle()
 * 把 [getDefaultStyle] 的结果灌入 StyleState，而 `getDefaultForeground()/getDefaultBackground()`
 * 的默认实现又从它派生——TerminalPanel 的窗口色与 null 色回退两条渲染路径都收敛到此，
 * 故覆盖 [getDefaultStyle] 即是唯一生效入口（只覆盖 getDefaultForeground/Background 不够）。
 */
private class BareZenTerminalSettings(
    private val copyOnSelect: Boolean,
) : DefaultSettingsProvider() {
    // 官方弃用提示（改用 getDefaultForeground/Background）在 3.73 不成立，见类注释；此处仅为抑制 OVERRIDE_DEPRECATION
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getDefaultStyle(): TextStyle =
        TextStyle(TerminalPalette.rgb(TerminalPalette.Foreground), TerminalPalette.rgb(TerminalPalette.Background))

    // JediTerm 3.73 无 getANSIColor（简报预置回退）：16 色的实际签核接口是 UserSettingsProvider.getTerminalColorPalette()
    override fun getTerminalColorPalette(): ColorPalette = BareZenColorPalette(super.getTerminalColorPalette())

    override fun getTerminalFont(): Font = Font(TERMINAL_FONT_FAMILY, Font.PLAIN, TERMINAL_FONT_SIZE)

    override fun getTerminalFontSize(): Float = TERMINAL_FONT_SIZE.toFloat()

    // 设置·选中即复制：javap 已确认 SettingsProvider（DefaultSettingsProvider）声明 copyOnSelect()，
    // 且被 TerminalPanel 消费（选中即写入剪贴板）。此处把它接到用户设置。
    override fun copyOnSelect(): Boolean = copyOnSelect

    private companion object {
        const val TERMINAL_FONT_FAMILY = "JetBrains Mono"
        const val TERMINAL_FONT_SIZE = 13
    }
}
