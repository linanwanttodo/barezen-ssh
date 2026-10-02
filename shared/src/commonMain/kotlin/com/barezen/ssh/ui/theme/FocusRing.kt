// shared/src/commonMain/kotlin/com/barezen/ssh/ui/theme/FocusRing.kt
package com.barezen.ssh.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 键盘焦点环 —— 需求文档 §2「平台与语言硬约束」第 3 条：
 * *「所有操作可 Tab 到达，Tab 顺序符合视觉顺序，**焦点环可见**」*。
 *
 * 此前全库无任何 focus 处理，自绘的可点行（侧栏导航项、收起按钮、设置分类项）只依赖
 * `Modifier.clickable` 的默认行为，键盘焦点无可见反馈。此修饰符在**键盘焦点**落到该元素时
 * 绘制一圈 2dp `primary` 描边：
 *
 * - 不改任何色彩 token（用现成的 `primary`/形状），不影响对比度证据；
 * - 无位移动效、无发光、无渐变（符合本项目「不做悬浮效果」的约定）；
 * - 材质类组件（Button/TextField 等）本身已有 M3 的 focus state layer，不重复叠加。
 *
 * 用法：放在 `clickable` 之后、且与 `clip` 同形状。
 */
@Composable
fun Modifier.focusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ringColor = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, ringColor, shape) else Modifier)
}
