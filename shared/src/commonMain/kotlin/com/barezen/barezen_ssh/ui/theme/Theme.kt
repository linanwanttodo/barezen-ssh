// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BareZenDarkColors = darkColorScheme(
    primary = BareZenPrimary,
    onPrimary = BareZenOnPrimary,
    primaryContainer = BareZenPrimaryContainer,
    onPrimaryContainer = BareZenOnPrimaryContainer,
    secondaryContainer = BareZenSecondaryContainer,
    onSecondaryContainer = BareZenOnSecondaryContainer,
    tertiary = BareZenTertiary,
    tertiaryContainer = BareZenTertiaryContainer,
    onTertiaryContainer = BareZenOnTertiaryContainer,
    error = BareZenError,
    background = BareZenSurface,
    onBackground = BareZenOnSurface,
    surface = BareZenSurface,
    onSurface = BareZenOnSurface,
    surfaceContainerLowest = BareZenSurfaceContainerLowest,
    surfaceContainerLow = BareZenSurfaceContainerLow,
    surfaceContainer = BareZenSurfaceContainer,
    surfaceContainerHigh = BareZenSurfaceContainerHigh,
    surfaceContainerHighest = BareZenSurfaceContainerHighest,
    onSurfaceVariant = BareZenOnSurfaceVariant,
    outline = BareZenOutline,
    outlineVariant = BareZenOutlineVariant,
)

@Composable
fun BareZenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BareZenDarkColors, typography = BareZenTypography, content = content)
}
