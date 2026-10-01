// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal val BareZenDarkColors = darkColorScheme(
    primary = BareZenAccent,
    onPrimary = BareZenOnAccent,
    primaryContainer = BareZenAccentSubtle,
    onPrimaryContainer = BareZenAccent,
    secondaryContainer = BareZenAccentSubtle,
    onSecondaryContainer = BareZenAccent,
    tertiary = BareZenInfo,
    tertiaryContainer = BareZenInfoBg,
    onTertiaryContainer = BareZenInfo,
    error = BareZenError,
    onError = BareZenOnAccent,
    errorContainer = BareZenErrorBg,
    onErrorContainer = BareZenError,
    background = BareZenBg,
    onBackground = BareZenTextPrimary,
    surface = BareZenSurface,
    onSurface = BareZenTextPrimary,
    surfaceContainerLowest = BareZenBg,
    surfaceContainerLow = BareZenPanel,
    surfaceContainer = BareZenPanel,
    surfaceContainerHigh = BareZenElevated,
    surfaceContainerHighest = BareZenElevated,
    onSurfaceVariant = BareZenTextSecondary,
    outline = BareZenBorder,
    outlineVariant = BareZenBorderSubtle,
)

/** 设计包 §5 圆角策略：窗口/卡片 8、输入/按钮 6、小件 4；chips 药丸在各组件处显式 999。 */
val BareZenShapes = Shapes(
    extraLarge = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(4.dp),
)

/**
 * @param darkTheme 是否使用深色板。**目前只有深色板**：传 false 仍是深色，
 *   浅色主题落地时只改这一个分支（见设计 §9.1）。
 */
@Composable
fun BareZenTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    // 浅色板尚未实现；保留形参与分支，落地时只改这里
    @Suppress("UNUSED_EXPRESSION")
    val scheme = if (darkTheme) BareZenDarkColors else BareZenDarkColors
    MaterialTheme(
        colorScheme = scheme,
        typography = BareZenTypography,
        shapes = BareZenShapes,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        ) { content() }
    }
}

/**
 * 把界面缩放只作用在 density 上；fontScale 原样保留（否则字号被缩放两次）。
 * 纯函数：便于在测试里断言，不依赖 Compose 运行环境（设计 §15 R9）。
 */
fun scaledDensity(base: Density, uiScale: Float): Density =
    Density(base.density * uiScale, base.fontScale)
