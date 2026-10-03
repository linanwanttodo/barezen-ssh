// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/WindowControl.kt
// 自建窗口 chrome 的运行期句柄。
//
// 窗口用 `WindowDecoration.None`（无系统标题栏）后，关闭/最小化/最大化、拖动与边缘缩放
// 都要自己接。Compose 侧没有「声明这块区域可拖窗」的修饰符（1.12 只有
// `UndecoratedWindowResizer`，它只做边缘缩放、不做标题栏拖动），
// 故拖动用 `detectDragGestures` + AWT `Window.location` 实现：拖动开始时记住窗口原点，
// 之后按每帧位移量累加——指针事件只给增量，窗口移动后也不必重算坐标基准。
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Point

/** 自建标题栏需要的窗口能力。默认实现全为空操作——headless 组合时安全。 */
interface WindowController {
    val isMaximized: Boolean

    /** 窗口可用工作区（已扣除任务栏）。 */
    val workArea: WindowWorkArea

    /**
     * 底层 AWT 窗口，供标题栏拖动区使用。
     * 壳层经它拿窗口而不直接依赖 `LocalAwtWindow`（后者是实验 API，且会把 AWT 泄漏进 UI 层）。
     */
    val awtWindow: java.awt.Window?

    fun close()
    fun minimize()
    fun toggleMaximize()
}

data class WindowWorkArea(val width: Dp, val height: Dp)

/** 预览/测试用的空实现：点窗口按钮不崩，也不真的退出进程。 */
object NoopWindowController : WindowController {
    override val isMaximized: Boolean = false
    override val awtWindow: java.awt.Window? = null
    override fun minimize() {}
    override fun toggleMaximize() {}
    override fun close() {}
    override val workArea: WindowWorkArea = WindowWorkArea(1920.dp, 1080.dp)
}

val LocalWindowController = staticCompositionLocalOf<WindowController> { NoopWindowController }

/** 拖动阈值：单帧位移超过它即判定为「在拖动」，否则按下-抬起算一次点击。 */
private const val DragMoveThresholdPx = 4f

/**
 * 把区域声明为**标题栏拖动区**：拖动即移动窗口；几乎没有位移的按下-抬起视为点击，
 * 触发 [onClick]（桌面惯例：双击标题栏 = 最大化/还原）。
 *
 * [window] 为 null（headless 测试组合）时退化为 no-op，不装任何监听。
 */
@Composable
fun Modifier.windowDragHandle(
    window: java.awt.Window?,
    onClick: (() -> Unit)? = null,
): Modifier {
    if (window == null) return this
    return remember(window, onClick) {
        val origin = Point()
        var moved = false
        pointerInput(window) {
            detectDragGestures(
                onDragStart = {
                    origin.setLocation(window.locationOnScreen)
                    moved = false
                },
                onDrag = { change, delta ->
                    change.consume()
                    if (kotlin.math.abs(delta.x) > DragMoveThresholdPx ||
                        kotlin.math.abs(delta.y) > DragMoveThresholdPx
                    ) {
                        moved = true
                    }
                    origin.x += delta.x.toInt()
                    origin.y += delta.y.toInt()
                    window.location = Point(origin.x, origin.y)
                },
                onDragEnd = { if (!moved) onClick?.invoke() },
                onDragCancel = { moved = false },
            )
        }
    }
}
