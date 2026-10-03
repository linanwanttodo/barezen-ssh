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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * M3 色彩槽位之外的补充令牌。MaterialTheme.colorScheme 装不下的东西都在这里，
 * 组件只消费本对象（CONVENTIONS §3：组件只消费主题，禁止硬编码颜色）。
 */
data class BareZenExtraColors(
    val surface2: Color,
    val borderStrong: Color,
    val accentOnSubtle: Color,
    val onErrorContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val terminalBg: Color,
    val terminalFg: Color,
    val terminalDim: Color,
    val terminalGreen: Color,
    val metricCpu: Color,
    val metricMem: Color,
    val metricLoad: Color,
    val metricUptime: Color,
    val metricNet: Color,
)

val LocalBareZenColors = staticCompositionLocalOf {
    BareZenExtraColors(
        surface2 = BareZenSurface2,
        borderStrong = BareZenBorderStrong,
        accentOnSubtle = BareZenAccentOnSubtle,
        onErrorContainer = BareZenOnErrorContainer,
        warning = BareZenWarning,
        warningContainer = BareZenWarningBg,
        success = BareZenSuccess,
        successContainer = BareZenSuccessBg,
        info = BareZenInfo,
        infoContainer = BareZenInfoBg,
        terminalBg = BareZenTerminalBg,
        terminalFg = BareZenTerminalFg,
        terminalDim = BareZenTerminalDim,
        terminalGreen = BareZenTerminalGreen,
        metricCpu = BareZenMetricCpu,
        metricMem = BareZenMetricMem,
        metricLoad = BareZenMetricLoad,
        metricUptime = BareZenMetricUptime,
        metricNet = BareZenMetricNet,
    )
}

private val DarkExtras = BareZenExtraColors(
    surface2 = BareZenSurface2,
    borderStrong = BareZenBorderStrong,
    accentOnSubtle = BareZenAccentOnSubtle,
    onErrorContainer = BareZenOnErrorContainer,
    warning = BareZenWarning,
    warningContainer = BareZenWarningBg,
    success = BareZenSuccess,
    successContainer = BareZenSuccessBg,
    info = BareZenInfo,
    infoContainer = BareZenInfoBg,
    terminalBg = BareZenTerminalBg,
    terminalFg = BareZenTerminalFg,
    terminalDim = BareZenTerminalDim,
    terminalGreen = BareZenTerminalGreen,
    metricCpu = BareZenMetricCpu,
    metricMem = BareZenMetricMem,
    metricLoad = BareZenMetricLoad,
    metricUptime = BareZenMetricUptime,
    metricNet = BareZenMetricNet,
)

private val LightExtras = BareZenExtraColors(
    surface2 = BareZenLightSurface2,
    borderStrong = BareZenLightBorderStrong,
    accentOnSubtle = BareZenLightAccentOnSubtle,
    onErrorContainer = BareZenLightOnErrorDeep,
    warning = BareZenLightWarning,
    warningContainer = BareZenLightWarningBg,
    success = BareZenLightSuccess,
    successContainer = BareZenLightSuccessBg,
    info = BareZenLightInfo,
    infoContainer = BareZenLightInfoBg,
    terminalBg = BareZenLightTerminalBg,
    terminalFg = BareZenLightTerminalFg,
    terminalDim = BareZenTerminalDim,
    terminalGreen = BareZenLightSuccess,
    metricCpu = BareZenLightMetricCpu,
    metricMem = BareZenLightMetricMem,
    metricLoad = BareZenLightMetricLoad,
    metricUptime = BareZenLightMetricUptime,
    metricNet = BareZenLightMetricLoad,
)

internal val BareZenDarkColors = darkColorScheme(
    primary = BareZenAccent,
    onPrimary = BareZenOnAccent,
    primaryContainer = BareZenAccentSubtle,
    onPrimaryContainer = BareZenAccentOnSubtle,
    secondary = BareZenAccent,
    onSecondary = BareZenOnAccent,
    secondaryContainer = BareZenAccentSubtle,
    onSecondaryContainer = BareZenAccentOnSubtle,
    tertiary = BareZenMetricUptime,
    onTertiary = BareZenOnAccent,
    tertiaryContainer = BareZenSuccessBg,
    onTertiaryContainer = BareZenSuccess,
    error = BareZenError,
    onError = BareZenOnAccent,
    errorContainer = BareZenErrorBg,
    onErrorContainer = BareZenOnErrorContainer,
    background = BareZenBg,
    onBackground = BareZenTextPrimary,
    surface = BareZenSurface,
    onSurface = BareZenTextPrimary,
    surfaceContainerLowest = BareZenSurface2,
    surfaceContainerLow = BareZenBg,
    surfaceContainer = BareZenPanel,
    surfaceContainerHigh = BareZenPanel,
    surfaceContainerHighest = BareZenElevated,
    onSurfaceVariant = BareZenTextSecondary,
    outline = BareZenBorder,
    outlineVariant = BareZenBorderSubtle,
    scrim = Color(0xCC000000),
)

internal val BareZenLightColors = lightColorScheme(
    primary = BareZenLightAccent,
    onPrimary = BareZenLightOnAccent,
    primaryContainer = BareZenLightAccentSubtle,
    onPrimaryContainer = BareZenLightAccentOnSubtle,
    secondary = BareZenLightAccent,
    onSecondary = BareZenLightOnAccent,
    secondaryContainer = BareZenLightAccentSubtle,
    onSecondaryContainer = BareZenLightAccentOnSubtle,
    tertiary = BareZenLightMetricUptime,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = BareZenLightSuccessBg,
    onTertiaryContainer = BareZenLightSuccess,
    error = BareZenLightError,
    onError = BareZenLightOnAccent,
    errorContainer = BareZenLightErrorBg,
    onErrorContainer = BareZenLightOnErrorDeep,
    background = BareZenLightBg,
    onBackground = BareZenLightTextPrimary,
    surface = BareZenLightSurface,
    onSurface = BareZenLightTextPrimary,
    surfaceContainerLowest = BareZenLightSurface2,
    surfaceContainerLow = BareZenLightBg,
    surfaceContainer = BareZenLightPanel,
    surfaceContainerHigh = BareZenLightPanel,
    surfaceContainerHighest = BareZenLightElevated,
    onSurfaceVariant = BareZenLightTextSecondary,
    outline = BareZenLightBorder,
    outlineVariant = BareZenLightBorderSubtle,
    scrim = Color(0x99000000),
)

/**
 * 圆角策略（apple.css `--radius-*`）：控件 6/8、容器 10/12、药丸在各处显式 999。
 * M3 的 Shapes 槽位：medium=控件、large=容器、extraLarge=大容器、small=紧凑控件。
 */
val BareZenShapes = Shapes(
    extraLarge = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(6.dp),
)

/**
 * 布局常量（apple.css 的 `--space-*` 与尺寸令牌），集中在此避免各屏各写魔法数。
 */
object BareZenSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** 固定尺寸令牌（apple.css 尺寸段）。 */
object BareZenSize {
    val titlebarHeight = 48.dp
    val brandHeight = 64.dp
    val sidebarWidth = 224.dp
    val sidebarCollapsedWidth = 56.dp
    val statusBarHeight = 28.dp
    val tabHeight = 38.dp
    val navItemHeight = 34.dp
    val controlHeight = 32.dp
    val controlHeightSm = 28.dp
    val transferQueueHeight = 44.dp
    val aiPanelWidth = 320.dp
    val settingsNavWidth = 200.dp
    val contentInset = 12.dp
    val contentTopRadius = 12.dp
}

/**
 * @param darkTheme 是否使用深色板；false 走亮色板（跟随系统时由调用方按系统偏好传入）。
 */
@Composable
fun BareZenTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) BareZenDarkColors else BareZenLightColors
    val extras = if (darkTheme) DarkExtras else LightExtras
    MaterialTheme(
        colorScheme = scheme,
        typography = BareZenTypography,
        shapes = BareZenShapes,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            LocalBareZenColors provides extras,
        ) { content() }
    }
}

/**
 * 把界面缩放只作用在 density 上；fontScale 原样保留（否则字号被缩放两次）。
 * 纯函数：便于在测试里断言，不依赖 Compose 运行环境。
 */
fun scaledDensity(base: Density, uiScale: Float): Density =
    Density(base.density * uiScale, base.fontScale)
