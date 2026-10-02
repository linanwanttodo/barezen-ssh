// shared/src/commonMain/kotlin/com/barezen/ssh/ui/theme/Color.kt
// DBX 设计令牌（dark 段权威值见 github.com/t8y2/dbx apps/desktop/src/styles/tokens.css），
// 亮色板从同一色相派生；语义背景为带 alpha 的 tint，渲染时合成在 surface 上。
package com.barezen.ssh.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 暗色板（DBX .dark 段） ----
val BareZenBg = Color(0xFF131416)              // 背景 = dbx --background rgb(19 20 22)
val BareZenSurface = Color(0xFF1B1B1E)         // 表面 = dbx --card rgb(27 27 30)
val BareZenPanel = Color(0xFF19191C)           // 面板 = dbx --sidebar rgb(25 25 28)
val BareZenElevated = Color(0xFF232327)        // 抬高（比 surface 略亮，自派生）
val BareZenBorder = Color(0x476E6E72)          // = dbx --border rgb(110 110 114 / 0.28)
val BareZenBorderSubtle = Color(0x296E6E72)    // 同 rgb，16% alpha
val BareZenTextPrimary = Color(0xFFF2F2F4)
val BareZenTextSecondary = Color(0xFF9E9EA6)
val BareZenTextTertiary = Color(0xFF82828A)
val BareZenAccent = Color(0xFFD0D0D6)          // 中性主色 = dbx --primary rgb(208 208 214)
val BareZenOnAccent = Color(0xFF1B1B1E)
val BareZenAccentSubtle = Color(0x1AFFFFFF)    // 白色 10% alpha
val BareZenError = Color(0xFFF3625F)           // = dbx --destructive rgb(243 98 95)
val BareZenErrorBg = Color(0x26F3625F)         // destructive 15% alpha（AA 合成后 4.51:1）
val BareZenWarning = Color(0xFFFBBF24)         // = dbx --warning rgb(251 191 36)
val BareZenWarningBg = Color(0x26FBBF24)
val BareZenInfo = Color(0xFF60A5FA)            // = dbx --info rgb(96 165 250)
val BareZenInfoBg = Color(0x2660A5FA)
val BareZenTerminalBg = Color(0xFF1E1E1E)      // 终端背景（Task 3 注入 JediTerm）

// ---- 亮色板（同一色相派生；语义前景与暗色同值，背景 alpha 提到 16%） ----
val BareZenLightBg = Color(0xFFF5F5F7)
val BareZenLightSurface = Color(0xFFFFFFFF)
val BareZenLightPanel = Color(0xFFF9F9FA)
val BareZenLightElevated = Color(0xFFFFFFFF)
val BareZenLightBorder = Color(0x476E6E72)     // 与暗色同 rgb(110 110 114 / 0.28)
val BareZenLightBorderSubtle = Color(0x296E6E72)
val BareZenLightTextPrimary = Color(0xFF1B1B1E)
val BareZenLightTextSecondary = Color(0xFF6E6E76)
val BareZenLightTextTertiary = Color(0xFF67676F)
val BareZenLightAccent = Color(0xFF3A3A40)
val BareZenLightOnAccent = Color(0xFFFFFFFF)
val BareZenLightAccentSubtle = Color(0x0F000000) // 黑色 6% alpha
val BareZenLightErrorBg = Color(0x29F3625F)
val BareZenLightWarningBg = Color(0x29FBBF24)
val BareZenLightInfoBg = Color(0x2960A5FA)
