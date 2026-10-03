// shared/src/commonMain/kotlin/com/barezen/ssh/ui/theme/Color.kt
// 设计基准：docs/ui-redesign/apple.css（Apple HIG 暗色体系）· 2026-10-03 用户拍板全盘采用。
//
// 【与 apple.css 的差异，逐条有据】apple.css 的取色有 8 处不达 WCAG AA，而本项目把 AA
// 列为硬约束（PRODUCT.md「Accessibility & Inclusion」），故取「apple 的色相与层次 +
// 满足 AA 的明度」，而非照抄字面值。差异集中在四组，全部经对比度计算：
//
// 1. textTertiary  #6E6E73 -> #94949A
//    原值在 bg 上仅 3.36:1、elevated 上 2.75:1（AA 需 4.5:1）。新值 bg 5.64 / panel 5.14 /
//    elevated 4.62，全部达标，且仍明显弱于 textSecondary #98989D。
// 2. onAccent  纯白 -> 近黑 #0A0A0C
//    白字压 accent #0A84FF 只有 3.65:1（AA 需 4.5:1）；近黑为 5.42:1。
//    这是「accent 既是填充色又是文字色」的结构性矛盾：能让白字达标的深蓝（≥#005FCC 系）
//    做暗底文字时必然不足 4.5:1。故拆成两个角色（见 BareZenAccent / BareZenAccentOnSubtle）。
// 3. error 族整体提亮：#FF453A -> error #FF7B74 / onErrorContainer #FF9A93
//    原值在**最亮的底色上**不足 AA：作为普通文字压 elevated(2C2C2E) 只有 4.09:1；
//    作为 errorContainer 里的文字压 14% 红色 tint 只有 4.27:1。
//    同一红色色相（R 满、G/B 抬）提亮两档后：裸底最差 5.53:1，tint 底最差 5.43:1。
//    色相未变，仍是「警告/错误」这一个红。
// 4. 亮色板整体按 Apple 亮色层次重取（bg #F2F2F4 / surface 白 / elevated #E8E8ED），
//    语义色按 AA 逐个反解：textTertiary #67676E、error #BB3330、warning #8A5A00、success #16682C。
//
// 【两条结构性约束，本文件把它们固化在命名里】
// - accent 不能同时当填充和暗底文字：白字压 #0A84FF 仅 3.65:1，而能让白字达标的深蓝
//   （≥ #005FCC 系）做暗底文字又必然不足 4.5:1。故 `BareZenAccent` 只做**填充/图标**，
//   `BareZenAccentOnSubtle`（#44A0FC）才是**文字**。
// - 错误文字一律走 `BareZenOnErrorContainer`，不走裸 `BareZenError`。
//
// 纪律（沿用 STATUS 3.4 并按本次拍板更新）：终端 ANSI 配色是**数据**，不随 UI 令牌变动；
// 彩色仅用于 accent / 语义状态 / 仪表盘数据系列，界面 chrome 不引入装饰性彩色。
package com.barezen.ssh.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 暗色板（apple.css :root） ----
val BareZenBg = Color(0xFF1C1C1E)               // 屏底 = --bg
val BareZenSurface = Color(0xFF141416)          // 侧栏/标题栏 = --surface
val BareZenSurface2 = Color(0xFF0E0E10)         // 终端与标签条底 = --surface-2
val BareZenPanel = Color(0xFF242426)            // 卡片/面板 = --panel
val BareZenElevated = Color(0xFF2C2C2E)         // 抬起层 = --elevated

val BareZenBorder = Color(0xFF38383A)           // = --border
val BareZenBorderStrong = Color(0xFF48484A)     // = --border-strong
val BareZenBorderSubtle = Color(0xFF2A2A2C)     // = --border-subtle

val BareZenTextPrimary = Color(0xFFF5F5F7)      // = --text
val BareZenTextSecondary = Color(0xFF98989D)    // = --text-secondary
val BareZenTextTertiary = Color(0xFF94949A)     // 见文件头差异 1

val BareZenAccent = Color(0xFF0A84FF)           // = --accent（同时作填充与普通底上的文字）
val BareZenOnAccent = Color(0xFF0A0A0C)         // 见文件头差异 2：accent 填充上的文字
val BareZenAccentSubtle = Color(0x290A84FF)     // 16% tint = --accent-subtle 的不透明预合成替代
val BareZenAccentOnSubtle = Color(0xFF44A0FC)   // 压在 AccentSubtle 上的文字（同色相提亮，5.14:1）

val BareZenError = Color(0xFFFF7B74)            // = --error 同色相提亮，见文件头差异 3
val BareZenErrorBg = Color(0x24FF7B74)          // 14% tint
val BareZenOnErrorContainer = Color(0xFFFF9A93)  // 压在 ErrorBg 上的文字，见文件头差异 3
val BareZenWarning = Color(0xFFFF9F0A)          // = --warning
val BareZenWarningBg = Color(0x24FF9F0A)        // 14% tint
val BareZenSuccess = Color(0xFF30D158)          // = --success
val BareZenSuccessBg = Color(0x2430D158)        // 14% tint
val BareZenInfo = Color(0xFF64D2FF)             // = --info（Apple 亮蓝，暗底可读）
val BareZenInfoBg = Color(0x2464D2FF)

val BareZenTerminalBg = Color(0xFF0E0E10)       // 终端底
val BareZenTerminalFg = Color(0xFFF5F5F7)       // 终端前景
val BareZenTerminalDim = Color(0xFF8E8E93)      // 终端输出弱化色
val BareZenTerminalGreen = Color(0xFF30D158)    // 终端提示符

// ---- 仪表盘数据系列（数据可视化，非界面 chrome） ----
// 作为卡片顶部 3dp 色条与图例点，判定门槛是非文本 UI 构件的 3:1。
// 紫色在 panel 上 4.40:1，四色均达标。
val BareZenMetricCpu = Color(0xFF0A84FF)
val BareZenMetricMem = Color(0xFF30D158)
val BareZenMetricLoad = Color(0xFFFF9F0A)
val BareZenMetricUptime = Color(0xFFBF5AF2)
val BareZenMetricNet = Color(0xFFFF9F0A)

// ---- 亮色板（Apple 亮色层次；语义色按 AA 反解，见文件头差异 4） ----
val BareZenLightBg = Color(0xFFF2F2F4)
val BareZenLightSurface = Color(0xFFFFFFFF)
val BareZenLightPanel = Color(0xFFFFFFFF)
val BareZenLightElevated = Color(0xFFE8E8ED)
val BareZenLightSurface2 = Color(0xFFF7F7F9)

val BareZenLightBorder = Color(0xFFC7C7CC)      // 亮底需要实色边界：暗底 28% alpha 在白底上不可见
val BareZenLightBorderStrong = Color(0xFFAEAEB2)
val BareZenLightBorderSubtle = Color(0xFFE5E5EA)

val BareZenLightTextPrimary = Color(0xFF1C1C1E)
val BareZenLightTextSecondary = Color(0xFF5B5B61)
val BareZenLightTextTertiary = Color(0xFF67676E) // elevated 上 4.60:1

val BareZenLightAccent = Color(0xFF005FCC)      // 白底 5.98:1，bg 5.35:1
val BareZenLightOnAccent = Color(0xFFFFFFFF)
val BareZenLightAccentSubtle = Color(0x1F005FCC)
val BareZenLightAccentOnSubtle = Color(0xFF004799) // 压在 AccentSubtle 上，白底 7.5:1

val BareZenLightError = Color(0xFFBB3330)       // 白底 5.79:1，tint 底 4.65:1
val BareZenLightErrorBg = Color(0x24BB3330)
val BareZenLightOnErrorDeep = Color(0xFF8F2422)  // errorContainer 上的更深档
val BareZenLightWarning = Color(0xFF8A5A00)
val BareZenLightWarningBg = Color(0x248A5A00)
val BareZenLightSuccess = Color(0xFF16682C)
val BareZenLightSuccessBg = Color(0x2416682C)
val BareZenLightInfo = Color(0xFF00629E)
val BareZenLightInfoBg = Color(0x2400629E)

val BareZenLightTerminalBg = Color(0xFF1C1C1E)  // 亮色板下终端仍用暗底（终端是深色场景）
val BareZenLightTerminalFg = Color(0xFFF5F5F7)

// ---- 亮色板数据系列（白底 3:1 门槛） ----
val BareZenLightMetricCpu = Color(0xFF0062D6)
val BareZenLightMetricMem = Color(0xFF16682C)
val BareZenLightMetricLoad = Color(0xFF8A5A00)
val BareZenLightMetricUptime = Color(0xFF7A3E9D)
