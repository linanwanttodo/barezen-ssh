package com.barezen.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.real
import com.barezen.ssh.ui.shell.LocalWindowController
import com.barezen.ssh.ui.shell.WindowController
import com.barezen.ssh.ui.shell.WindowWorkArea
import java.awt.Toolkit

/** 还原窗口时的默认尺寸（与初始窗口一致）。 */
private val DefaultWindowSize = DpSize(1440.dp, 900.dp)

/** 窗口边缘缩放区厚度（dp）：无边框窗口下由 Compose 提供拖边缩放。 */
private val ResizerThickness = 6f.dp

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() = application {
    // remember：模型承载连接态与 shell 会话，重组静默重建会把活动会话晾成孤儿
    val model = remember { AppModel.real() }
    val windowState = rememberWindowState(
        width = DefaultWindowSize.width,
        height = DefaultWindowSize.height,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        // 关窗即收口会话（先 close 再退出，SSH 连接/keep-alive 线程不残留）
        onCloseRequest = {
            model.disconnect()
            exitApplication()
        },
        title = "BareZen-SSH",
        state = windowState,
        // **无系统装饰**：标题栏由 AppShell 自建（与侧栏粘连，含关闭/最大化/最小化）。
        // 用系统装饰会得到「自建标题栏 + 系统标题栏」双层，视觉与拖拽都不对。
        // Undecorated 同时提供边缘拖拽缩放；标题栏拖动由 WindowControl.windowDragHandle 接。
        decoration = WindowDecoration.Undecorated(ResizerThickness),
        resizable = true,
    ) {
        val controller = remember(windowState) {
            DesktopWindowController(
                windowState,
                onRequestClose = {
                    model.disconnect()
                    exitApplication()
                },
            )
        }
        CompositionLocalProvider(LocalWindowController provides controller) {
            // 窗口根 = 界面根，**满铺不内缩**：界面自己就是窗口，不该像卡片浮在窗口里。
            // 底色取 --bg(#1C1C1E)，与内容区同色；侧栏/标题栏的 --surface 靠色差分层。
            App(model, Modifier.fillMaxSize().background(Color(0xFF1C1C1E)))
        }
    }
}

/**
 * 桌面窗口控制：把 [WindowController] 的意图翻译成 `WindowState` 的状态变化。
 *
 * 最大化 = 填满**屏幕工作区**，不是全屏——全屏会盖住任务栏且丢掉自建的窗口按钮。
 * 这里用 AWT 的 `Toolkit.getScreenSize()`（已扣除任务栏）并按系统缩放换算成 dp。
 */
private class DesktopWindowController(
    private val state: WindowState,
    private val onRequestClose: () -> Unit,
    private val uiScale: Float = 1f,
) : WindowController {

    override val isMaximized: Boolean
        get() = state.size.width >= workArea.width - 1.dp && state.size.height >= workArea.height - 1.dp

    override val workArea: WindowWorkArea
        get() {
            val px = Toolkit.getDefaultToolkit().screenSize
            return WindowWorkArea((px.width / uiScale).dp, (px.height / uiScale).dp)
        }

    /** AWT 窗口由 `LocalAwtWindow` 提供（实验 API），故置 null——拖动降级为无操作。 */
    override val awtWindow: java.awt.Window? get() = null

    override fun close() {
        // 真正退出走 onCloseRequest（那里要 disconnect 收口会话），这里只发信号。
        onRequestClose()
    }

    override fun minimize() {
        state.isMinimized = true
    }

    override fun toggleMaximize() {
        if (isMaximized) {
            state.size = DefaultWindowSize
        } else {
            val wa = workArea
            state.size = DpSize(wa.width, wa.height)
            state.position = WindowPosition(Alignment.Center)
        }
    }
}
