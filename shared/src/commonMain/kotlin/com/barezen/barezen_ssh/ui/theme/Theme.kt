// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun BareZenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BareZenDarkColors,
        typography = BareZenTypography,
        shapes = BareZenShapes,
        content = content,
    )
}
