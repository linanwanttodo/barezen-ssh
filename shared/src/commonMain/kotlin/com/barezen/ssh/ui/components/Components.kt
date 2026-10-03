// shared/src/commonMain/kotlin/com/barezen/ssh/ui/components/Components.kt
// 通用控件库：对应 apple.css 的 .btn/.chip/.input/.select/.toggle/.slider/.banner/.card 等。
// 各屏只从这里取控件，不再手写 Material 默认形态——这是「一套界面语言」的唯一来源。
package com.barezen.ssh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.ui.theme.LocalBareZenColors

/** 药丸圆角（apple.css `--radius-pill`），chips / badge / toggle 共用。 */
val PillShape = RoundedCornerShape(999.dp)

/** 按钮形态：primary 实心 accent、secondary 描边、ghost 无底、danger 危险描边。 */
enum class BtnKind { primary, secondary, ghost, danger }

/**
 * 按钮（apple.css `.btn`）：高 32（`.sm` 28），圆角 8，水平内边距 16。
 * hover / pressed 走底色位移（无渐变、无缩放，符合「不做装饰性动效」）。
 * 未实现的能力一律 [enabled] = false 而不是隐藏（CONVENTIONS §1.7）。
 */
@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.secondary,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    val height = if (small) 28.dp else 32.dp
    val shape = RoundedCornerShape(8.dp)

    // 底色：kind 决定常态，hover/pressed 只做同色系的一档明暗位移。
    val resting = when (kind) {
        BtnKind.primary -> colors.primary
        BtnKind.secondary -> colors.surfaceContainer
        BtnKind.ghost -> Color.Transparent
        BtnKind.danger -> Color.Transparent
    }
    val hoveredBg = when (kind) {
        BtnKind.primary -> colors.primary
        BtnKind.secondary -> colors.surfaceContainerHighest
        BtnKind.ghost, BtnKind.danger -> colors.surfaceContainer
    }
    val bg = when {
        !enabled -> colors.surfaceContainer
        pressed -> colors.surfaceContainerHighest
        hovered -> hoveredBg
        else -> resting
    }
    val fg = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        kind == BtnKind.primary -> colors.onPrimary
        kind == BtnKind.danger -> colors.error
        else -> colors.onSurface
    }
    val border = when {
        !enabled -> BorderStroke(1.dp, colors.outlineVariant)
        kind == BtnKind.secondary -> BorderStroke(1.dp, colors.outline)
        kind == BtnKind.danger -> BorderStroke(1.dp, colors.error)
        else -> null
    }

    Row(
        modifier
            .defaultMinSize(minHeight = height)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            // 键盘焦点环必须在 clickable 之前（FocusRing.kt 约束）
            .then(
                if (focused) Modifier.border(2.dp, extras.accentOnSubtle, shape) else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = if (small) 12.dp else 16.dp,
                vertical = 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, iconContentDescription, modifier = Modifier.size(14.dp), tint = fg)
        }
        Text(
            text,
            fontSize = if (small) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 筛选 chip（apple.css `.chip`）：高 28 药丸，选中态为反色填充（文字底 / 底色字）。
 * 用形状与反色表达选中，不依赖彩色（无障碍：色彩不作唯一指示）。
 */
@Composable
fun FilterChipPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val fg = if (selected) colors.surface else colors.onSurfaceVariant
    Row(
        modifier
            .height(28.dp)
            .clip(PillShape)
            .background(if (selected) colors.onSurface else colors.surfaceContainer)
            .then(
                if (!selected && enabled) Modifier.border(1.dp, colors.outline, PillShape) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) fg else fg.copy(alpha = 0.45f),
            maxLines = 1,
        )
    }
}

/** 徽标（apple.css `.badge`）：高 20 药丸，accent tint 底 + accent 文字。 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.accent,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val (bg, fg) = when (tone) {
        BadgeTone.accent -> colors.primaryContainer to extras.accentOnSubtle
        BadgeTone.warning -> extras.warningContainer to extras.warning
        BadgeTone.error -> colors.errorContainer to extras.onErrorContainer
        BadgeTone.success -> extras.successContainer to extras.success
        BadgeTone.neutral -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Row(
        modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

enum class BadgeTone { accent, warning, error, success, neutral }

/**
 * 横幅（apple.css `.banner`）：tint 底 + 1dp 描边 + 圆角 8，可选右侧动作。
 * 二态由语义决定，不做装饰：info=accent、warn=warning、error=error。
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.accent,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val (bg, stroke, fg, markFg) = when (tone) {
        BadgeTone.accent -> Quad(
            colors.primaryContainer, colors.primary.copy(alpha = 0.35f),
            colors.onSurface, extras.accentOnSubtle,
        )
        BadgeTone.warning -> Quad(
            extras.warningContainer, extras.warning.copy(alpha = 0.35f),
            colors.onSurface, extras.warning,
        )
        BadgeTone.error -> Quad(
            colors.errorContainer, colors.error,
            colors.onSurface, extras.onErrorContainer,
        )
        BadgeTone.success -> Quad(
            extras.successContainer, extras.success.copy(alpha = 0.35f),
            colors.onSurface, extras.success,
        )
        BadgeTone.neutral -> Quad(
            colors.surfaceContainer, colors.outline,
            colors.onSurface, colors.onSurfaceVariant,
        )
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, stroke),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = markFg)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp, color = fg)
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * 卡片（apple.css `.chart-card` / `.setting-card` / `.server-card`）：
 * panel 底 + 1dp 描边 + 圆角 10。注意 `.server-card` 用的是 `--bg` 底，
 * 卡片里再放 `--panel` 的小瓦片——层次由「底色两级」表达，而非叠加描边。
 */
@Composable
fun BzCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    /** true 用 --panel 底（图表/设置卡），false 用 --bg 底（服务器卡、规则条目）。 */
    raised: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = if (raised) colors.surfaceContainer else colors.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** 状态点（apple.css `.status-dot`）：8dp 圆点。`hollow` 用空心描边表达「无」。 */
@Composable
fun StatusDot(
    on: Boolean,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.success,
    hollow: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val color = when (tone) {
        BadgeTone.success -> extras.success
        BadgeTone.warning -> extras.warning
        BadgeTone.error -> colors.error
        BadgeTone.accent -> extras.accentOnSubtle
        BadgeTone.neutral -> colors.onSurfaceVariant
    }
    Box(
        modifier
            .size(8.dp)
            .then(
                if (hollow || !on) Modifier.border(1.dp, color, CircleShape)
                else Modifier.background(color, CircleShape)
            ),
    )
}

/**
 * 分段控件（apple.css `.segmented-control`）：外层描边槽 + 选中项反色填充。
 * 选中的项同时用字重变化表达（600），不单靠底色。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    disabledIndices: Set<Int> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, LocalBareZenColors.current.borderStrong, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val enabled = i !in disabledIndices
            val selected = i == selectedIndex
            val fg = when {
                selected -> colors.surface
                enabled -> colors.onSurfaceVariant
                else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
            }
            Box(
                Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) colors.onSurface else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 开关（apple.css `.toggle`）：38x22 药丸轨道 + 18dp 圆钮，选中为 success 色。
 * 比 M3 Switch 更扁，与 32dp 行高对齐；隐藏原生 Switch 的双主题配色。
 */
@Composable
fun BzSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val track = when {
        !enabled -> colors.outlineVariant
        checked -> extras.success
        else -> LocalBareZenColors.current.borderStrong
    }
    val knob = if (!enabled) colors.onSurfaceVariant.copy(alpha = 0.4f) else colors.surfaceContainerLowest
    Row(
        modifier
            .height(22.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .testTag("bz-switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(PillShape)
                .background(track),
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp, start = 2.dp, bottom = 2.dp)
                    .then(if (checked) Modifier.width(20.dp) else Modifier.width(18.dp))
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(knob),
            )
        }
    }
}

/**
 * 滑杆（apple.css `.slider`）：4dp 轨道 + 16dp 圆钮，右侧带等宽数值。
 * 用 Slider 但换掉 track/thumb 配色以贴合本主题；[valueLabel] 由调用方给出已格式化文本。
 */
@Composable
fun BzSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .width(160.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.onSurface,
                activeTrackColor = extras.accentOnSubtle,
                inactiveTrackColor = extras.borderStrong,
                disabledThumbColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
                disabledActiveTrackColor = colors.outlineVariant,
                disabledInactiveTrackColor = colors.outlineVariant,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            valueLabel,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** 标题栏式文本（apple.css `.titlebar-screen-name`）：13sp/600。 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 小节标题（apple.css `.chart-title` / `.pf-list-title`）：13sp/600。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 弱化说明文字（apple.css `.screen-subtitle` / `.setting-desc`）。 */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 12) {
    Text(
        text,
        modifier = modifier,
        fontSize = size.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 让自定义按钮内容继承前景色（供需要自定义排版的场景）。 */
@Composable
fun ContentColorProvider(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color, content = content)
}

/** 形状常量别名，供需要精确指定 apple 圆角的调用点。 */
object BzShape {
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(10.dp)
    val large = RoundedCornerShape(12.dp)
    val small = RoundedCornerShape(6.dp)
    fun pill(): Shape = PillShape
}
