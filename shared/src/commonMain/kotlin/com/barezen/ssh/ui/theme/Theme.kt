// shared/src/commonMain/kotlin/com/barezen/ssh/ui/theme/Theme.kt
package com.barezen.ssh.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    // 配色终审（STATUS 3.4）：tertiary 槽位不再携带彩色语义，跟随 primary 走中性灰阶
    tertiary = BareZenAccent,
    tertiaryContainer = BareZenAccentSubtle,
    onTertiaryContainer = BareZenOnAccent,
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

internal val BareZenLightColors = lightColorScheme(
    primary = BareZenLightAccent,
    onPrimary = BareZenLightOnAccent,
    primaryContainer = BareZenLightAccentSubtle,
    onPrimaryContainer = BareZenLightAccent,
    secondaryContainer = BareZenLightAccentSubtle,
    onSecondaryContainer = BareZenLightAccent,
    // 配色终审（STATUS 3.4）：同暗色板，tertiary 走中性灰阶
    tertiary = BareZenLightAccent,
    tertiaryContainer = BareZenLightAccentSubtle,
    onTertiaryContainer = BareZenLightOnAccent,
    error = BareZenLightError,
    onError = BareZenLightOnAccent,
    errorContainer = BareZenLightErrorBg,
    onErrorContainer = BareZenLightOnErrorDeep,
    background = BareZenLightBg,
    onBackground = BareZenLightTextPrimary,
    surface = BareZenLightSurface,
    onSurface = BareZenLightTextPrimary,
    surfaceContainerLowest = BareZenLightBg,
    surfaceContainerLow = BareZenLightPanel,
    surfaceContainer = BareZenLightPanel,
    surfaceContainerHigh = BareZenLightElevated,
    surfaceContainerHighest = BareZenLightElevated,
    onSurfaceVariant = BareZenLightTextSecondary,
    outline = BareZenLightBorder,
    outlineVariant = BareZenLightBorderSubtle,
)

/** DBX 圆角策略：控件 4px、容器 6px；chips 药丸在各组件处显式 999。 */
val BareZenShapes = Shapes(
    extraLarge = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
)

/**
 * @param darkTheme 是否使用深色板；false 走亮色板（跟随系统时由调用方按系统偏好传入）。
 */
@Composable
fun BareZenTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) BareZenDarkColors else BareZenLightColors
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
