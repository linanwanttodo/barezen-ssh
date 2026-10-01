# 计划 2：控制台石墨 UI 改版（M1 功能层不动，UI 层重做）

> 状态：**待用户批准**（批准即授权文末 9 条计划内提交信息）
> 执行方式：SDD（每任务新子代理 `opencode/mimo-v2.6-flash-free` + 任务评审 + 最终整体评审）

## 1. 目标与依据

**目标**：把已实现的 M0+M1 应用（44/44 测试绿）从 MaidKit 默认视觉整体切换到已验收的「控制台石墨」设计体系——色板、字体、外壳导航、服务器屏、连接流、终端屏、愿景占位四屏全部照设计包落地；SSH/终端/模型等功能层代码不动。

**需求与设计依据（唯一权威链）**：

| 依据 | 路径 | 用途 |
|---|---|---|
| 需求文档（handoff） | `docs/superpowers/specs/2026-09-25-ui-redesign-requirements.md` | 硬约束、词表、能力真相 |
| **设计包（权威）** | `docs/ui-redesign/index.html` | 色板/字号阶/IA/每屏四态/终端方案/组件表/词表改动项 |
| 设计包（愿景参照） | `docs/ui-redesign/prototype.html` + `styles.css` | 可点原型与 token 原文 |
| 验收记录 | 附录 B 8/8、对比度 17/17 复算通过、六屏目检（2026-09-26） | 本计划的前提 |

**验收期已拍板的决策（照此执行，不再问）**：

1. 占位屏深度 = **按设计包 index 占位布局**（骨架/表单区可见 + 「占位（M2/M3/M4）」标签；不造数——状态栏与卡片指标位一律 `—`）。
2. 计划 1 **免独立收口**：Task 9 不重跑、最终评审不单跑；其 ledger（`.superpowers/sdd/2026-09-24-m0-m1-shell-ssh-terminal/progress.md`）全部 ⚠️ 分诊项迁入本计划**最终整体评审**携带，一条不丢。
3. 词表：`打开文件管理→打开文件传输`（代码同步改）；`功能尚未启用` 全部废弃，占位改骨架/空态+里程碑标签。
4. AI 侧栏 280px（本期只留入口占位，不做分栏——见非目标）。

## 2. 非目标（本期不做）

- **不做 M2–M5 功能**：SFTP、端口转发真源、主机指标采集、AI 侧栏分栏本体、浅色主题、设置持久化。
- **不实现标签关闭与显式断开**（IA 标「开放项」，与设计方的开放项对齐，等专项决策）。
- **不做删除服务器菜单**（模型 `removeServer` 已存在但设计标占位 M2，不给 UI 入口）。
- **不做加载态 skeleton**（M1 数据源同步 `repo.list()`，无加载窗口；M2 接真源时补）。
- **不渲染端口转发导航 badge**（活动数无数据源；替代旧代码硬编码的假 `Badge("2")`，M3 接入后加）。
- 不动 SSH/终端/仓库/模型层逻辑；不动 `main.kt` 窗口参数（1440×900 已满足 ≥1180）。
- 计划 1 遗留分诊项（material-icons-extended、slf4j、onClosed、TOFU 覆盖、Task 9 main.kt remember 等，见 ledger ⚠️）**不在本计划内处置**，归最终评审。

## 3. 设计 → 实现映射决策表（实现照此，不再逐项请示）

| 设计项 | 处置 |
|---|---|
| 服务器卡「已连接」统计瓦片（负载/内存/运行） | 保留三格骨架，值一律 `—`（M4 占位） |
| 服务器卡「未连接」提示体 | 已有实现，保留（「连接后查看负载、内存和运行时间。」） |
| 横幅「全部重连」 | 渲染为 **禁用按钮** + testTag `reconnect-all`（占位 M2） |
| 连接失败 | 横幅（`连接失败：{msg}` + 启用的「重试」）+ 卡片状态行「连接失败」+ 圆点 error 色 |
| 连接中 / 失败对话框 | **实现**：全局覆盖层 `ConnectFlowOverlays.kt`；失败对话框与横幅并存（横幅常驻，对话框可关） |
| 连接中「取消」 | **实现**：`AppModel` 增 `connectJob` + `cancelConnect()`（协程取消 → Disconnected；这是本计划唯一的功能层改动，带单测） |
| 文件屏示例行（project/notes.txt 等） | **不渲染示例数据**：双栏仅表头（名称/大小/修改时间）+ 面板居中「占位（M2）」 |
| 仪表盘示例值（12%/62%/0.42/45d） | 值一律 `—`；服务器选择器/刷新渲染为**禁用**控件；保留「数据来源：SSH 主机指标」说明与两张图表卡占位标题 |
| 端口转发横幅「2 条」 | 真值「活动转发：0 条；需要先建立 SSH 连接才能启动新的转发。」 |
| 设置·外观 4 行 | 行与按钮全渲染但**禁用**（无持久化后端）；其余 7 分类 = 「「{名称}」设置页占位。」 |
| 标签条 close / `+` | close **不渲染**（断开是开放项）；`+`、`助手` 渲染为**禁用**（M4 / M5 占位） |
| 终端状态栏指标 | `负载 —`、`内存 —`、`运行 —`（仅已连接态；未连接态只显示圆点+「未连接」，照设计空态） |
| 侧边栏徽标 | 不渲染（见非目标）；侧栏含「BareZen」标题行（照 prototype） |
| UI 字体 | Noto Sans SC 三字重**随包分发**（`Sans/SubsetOTF/SC` 静态 OTF，OFL）；600 字重复用 Bold 字面 |
| 「打开文件管理」 | 代码改「打开文件传输」 |

## 4. 文件结构

**改**：

- `shared/src/commonMain/.../ui/theme/Color.kt` — 整体重写为石墨 token
- `shared/src/commonMain/.../ui/theme/Theme.kt` — 槽位重映射 + Shapes + LocalTextStyle 接线
- `shared/src/commonMain/.../ui/theme/Type.kt` — UI/等宽双字族 + 字号阶
- `shared/src/commonTest/.../ui/theme/ThemeColorsTest.kt` — 断言重写（含对比度自动化证据）
- `shared/src/jvmMain/.../ui/shell/AppShell.kt` — Task 5 挂覆盖层；Task 8 整体重写
- `shared/src/jvmMain/.../ui/screens/ServersScreen.kt`、`ConnectDialog.kt`、`TerminalScreen.kt`
- `shared/src/jvmMain/.../terminal/TerminalView.kt` — JediTerm 注入
- `shared/src/commonMain/.../app/Destination.kt` — 枚举重排
- `shared/src/commonMain/.../app/AppModel.kt` — `connectJob` / `cancelConnect`（Task 5）
- `shared/src/jvmTest/.../app/AppShellTest.kt`、`ServersScreenTest.kt`、`TerminalScreenTest.kt`
- `docs/superpowers/specs/2026-09-25-ui-redesign-requirements.md` 已在验收期同步（360→280px）

**增**：

- `shared/src/commonMain/composeResources/font/NotoSansSC-{Regular,Medium,Bold}.otf`（Task 2 下载）
- `shared/src/jvmMain/.../terminal/TerminalPalette.kt`（Task 3）
- `shared/src/jvmMain/.../ui/shell/ConnectFlowOverlays.kt`（Task 5）
- `shared/src/jvmMain/.../ui/screens/{FilesScreen,DashboardScreen,PortsScreen,SettingsScreen}.kt`（Task 7）
- `shared/src/commonTest/.../ui/theme/TypographyTest.kt`（Task 2）
- `shared/src/jvmTest/.../app/{ConnectFlowTest,PlaceholderScreensTest}.kt`（Task 5/7）
- `shared/src/jvmTest/.../terminal/TerminalPaletteTest.kt`（Task 3）

**删**：`shared/src/jvmMain/.../ui/screens/PlaceholderScreen.kt`（Task 7，词表废弃）

## 5. 环境与命令规约（每个子代理 brief 必带）

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
# 单测试套件（挂起先删缓存：rm -rf ~/.cache/fontconfig/）
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*ThemeColorsTest'
# 全量 / 编译
timeout -k 15 600 ./gradlew --no-daemon :shared:jvmTest
timeout -k 15 600 ./gradlew --no-daemon :desktopApp:compileKotlin
```

- Gradle **必须** `--no-daemon`；外网下载走代理 `-x http://127.0.0.1:7897`（curl 用 `-x http://127.0.0.1:7897`）。
- 杀应用只用记录的 PID（`kill <pid>`），**禁止 `pkill -f`**（会匹配自身 shell，已误杀过）。
- 截图目检走查需 `DISPLAY=:1`。
- RED 纪律：每任务先落测试跑红（编译失败也算红），再实现跑绿，证据（命令输出）写进报告。

## 6. 计划内提交信息（**待用户批准本文时一并授权**）

1. `feat: 设计 token 改版——控制台石墨色板、槽位映射与对比度证据测试`
2. `feat: Noto Sans SC 随包分发并接线字号阶与 UI 字体`
3. `feat: 终端 16 色板经 JediTerm SettingsProvider 独立注入`
4. `feat: 服务器屏改版——横幅态机、空态、统计占位与词表新词`
5. `feat: 连接中/失败覆盖层对话框与可取消连接`
6. `feat: 终端屏改版——标签条占位与状态栏指标位`
7. `feat: 愿景四屏占位布局（文件/仪表盘/端口转发/设置）`
8. `feat: 外壳改版——200px 可折叠侧边导航`
9. `feat: 收尾——全量回归与设计包走查`

（计划外另有 1 条待过目：`docs: 收录 UI 重设计设计包（prototype.html 与 styles.css）`——设计包 3 文件中仅 index.html 随验收修复入库，补齐余两者。）

---

## Task 1：设计 token 层——石墨色板、槽位映射、对比度证据测试

**触碰**：`Color.kt`、`Theme.kt`、`ThemeColorsTest.kt`
**不动**：任何屏（屏在 Task 4–8 逐个迁移）；`Type.kt` 原样（Noto 在 Task 2）。

### RED — 重写 `ThemeColorsTest.kt`（整体替换）

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/ui/theme/ThemeColorsTest.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 断言值 = docs/ui-redesign/index.html §1 色板 + styles.css --tokens 原文。 */
class ThemeColorsTest {
    @Test fun bgMatchesDesignPack() = assertEquals(Color(0xFF0B0D0E), BareZenBg)
    @Test fun surfaceMatchesDesignPack() = assertEquals(Color(0xFF131516), BareZenSurface)
    @Test fun panelMatchesDesignPack() = assertEquals(Color(0xFF1A1D1E), BareZenPanel)
    @Test fun elevatedMatchesDesignPack() = assertEquals(Color(0xFF232728), BareZenElevated)
    @Test fun borderMatchesDesignPack() = assertEquals(Color(0xFF2E3335), BareZenBorder)
    @Test fun borderSubtleMatchesDesignPack() = assertEquals(Color(0xFF24292B), BareZenBorderSubtle)
    @Test fun textPrimaryMatchesDesignPack() = assertEquals(Color(0xFFEEF1F3), BareZenTextPrimary)
    @Test fun textSecondaryMatchesDesignPack() = assertEquals(Color(0xFF9CA5A9), BareZenTextSecondary)
    @Test fun textTertiaryMatchesDesignPack() = assertEquals(Color(0xFF8B9498), BareZenTextTertiary)
    @Test fun accentMatchesDesignPack() = assertEquals(Color(0xFF52B788), BareZenAccent)
    @Test fun onAccentMatchesDesignPack() = assertEquals(Color(0xFF101413), BareZenOnAccent)
    @Test fun accentSubtleMatchesDesignPack() = assertEquals(Color(0xFF1A2E26), BareZenAccentSubtle)
    @Test fun errorMatchesDesignPack() = assertEquals(Color(0xFFE56A6A), BareZenError)
    @Test fun errorBgMatchesDesignPack() = assertEquals(Color(0xFF2A1A1A), BareZenErrorBg)
    @Test fun warningMatchesDesignPack() = assertEquals(Color(0xFFE5A044), BareZenWarning)
    @Test fun infoMatchesDesignPack() = assertEquals(Color(0xFF5CA8D8), BareZenInfo)
    @Test fun terminalBgMatchesDesignPack() = assertEquals(Color(0xFF1E1E1E), BareZenTerminalBg)

    @Test fun schemeMapsDesignTokens() {
        val c = BareZenDarkColors
        assertEquals(BareZenAccent, c.primary)
        assertEquals(BareZenOnAccent, c.onPrimary)
        assertEquals(BareZenAccentSubtle, c.secondaryContainer)
        assertEquals(BareZenAccent, c.onSecondaryContainer)
        assertEquals(BareZenError, c.error)
        assertEquals(BareZenErrorBg, c.errorContainer)
        assertEquals(BareZenBg, c.background)
        assertEquals(BareZenSurface, c.surface)
        assertEquals(BareZenPanel, c.surfaceContainerLow)
        assertEquals(BareZenElevated, c.surfaceContainerHigh)
        assertEquals(BareZenTextSecondary, c.onSurfaceVariant)
        assertEquals(BareZenBorder, c.outline)
        assertEquals(BareZenBorderSubtle, c.outlineVariant)
    }

    /** 设计包 §contrast 的证据自动化：正文组合全部 ≥4.5:1。 */
    @Test fun textCombinationsPassWcagAA() {
        val cases = listOf(
            Triple(BareZenTextPrimary, BareZenBg, 4.5),
            Triple(BareZenTextSecondary, BareZenBg, 4.5),
            Triple(BareZenTextTertiary, BareZenBg, 4.5),
            Triple(BareZenAccent, BareZenBg, 4.5),
            Triple(BareZenError, BareZenBg, 4.5),
            Triple(BareZenWarning, BareZenBg, 4.5),
            Triple(BareZenInfo, BareZenBg, 4.5),
            Triple(BareZenTextSecondary, BareZenPanel, 4.5),
            Triple(BareZenTextTertiary, BareZenPanel, 4.5),
            Triple(BareZenAccent, BareZenPanel, 4.5),
            Triple(BareZenOnAccent, BareZenAccent, 4.5),
            Triple(BareZenError, BareZenErrorBg, 4.5),
        )
        cases.forEach { (fg, bg, min) ->
            assertTrue(contrastRatio(fg, bg) >= min, "$fg on $bg = ${contrastRatio(fg, bg)} < $min")
        }
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun ch(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(color.red) + 0.7152 * ch(color.green) + 0.0722 * ch(color.blue)
    }
}
```

跑红：`timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*ThemeColorsTest'`（符号不存在 = 红）。

### GREEN

**`Color.kt` 整体替换**（旧 `BareZenPrimary` 等 23 个值全部删除）：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Color.kt
// 控制台石墨——token 原文见 docs/ui-redesign/index.html §1 与 styles.css :root
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.graphics.Color

val BareZenBg = Color(0xFF0B0D0E)              // 背景 bg
val BareZenSurface = Color(0xFF131516)         // 表面 surface
val BareZenPanel = Color(0xFF1A1D1E)           // 面板 panel
val BareZenElevated = Color(0xFF232728)        // 抬高 elevated
val BareZenBorder = Color(0xFF2E3335)          // 边框 border
val BareZenBorderSubtle = Color(0xFF24292B)    // border-subtle
val BareZenTextPrimary = Color(0xFFEEF1F3)
val BareZenTextSecondary = Color(0xFF9CA5A9)
val BareZenTextTertiary = Color(0xFF8B9498)
val BareZenAccent = Color(0xFF52B788)
val BareZenOnAccent = Color(0xFF101413)
val BareZenAccentSubtle = Color(0xFF1A2E26)
val BareZenError = Color(0xFFE56A6A)
val BareZenErrorBg = Color(0xFF2A1A1A)
val BareZenWarning = Color(0xFFE5A044)
val BareZenWarningBg = Color(0xFF2A1F12)
val BareZenInfo = Color(0xFF5CA8D8)
val BareZenInfoBg = Color(0xFF132536)
val BareZenTerminalBg = Color(0xFF1E1E1E)      // 终端背景（Task 3 注入 JediTerm）
```

**`Theme.kt` 整体替换**（`private` 改 `internal` 供测试；补 Shapes）：

```kotlin
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
```

注意：`BareZenTypography` 现为 `Typography()`（Task 2 才改），本任务不碰 `Type.kt`。

**验证**：`--tests '*ThemeColorsTest'` 全绿 → 全量 `:shared:jvmTest` 应回归 44/44（旧色仅被测试引用，扫描确认：`grep -rn "BareZenPrimary\|BareZenTertiary\|BareZenOnSurfaceVariant\|BareZenOutline" shared/src --include='*.kt'` 应只剩0 命中；若屏码有引用则仅改 import 不改行为）。

**提交**：`feat: 设计 token 改版——控制台石墨色板、槽位映射与对比度证据测试`

---

## Task 2：Noto Sans SC 随包 + 字号阶 + UI 字体接线

**触碰**：`composeResources/font/`（下载）、`Type.kt`、`Theme.kt`（LocalTextStyle 一处）、新增 `TypographyTest.kt`（commonTest）+ `ThemeWiringTest.kt`（jvmTest）

### RED — 先落两个测试

`shared/src/commonTest/.../ui/theme/TypographyTest.kt`：

```kotlin
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.text.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 断言值 = 设计包 index.html §3 字体与字号阶。 */
class TypographyTest {
    @Test fun typeScaleMatchesDesignPack() {
        val t = BareZenTypography
        assertEquals(24.sp, t.titleLarge.fontSize)
        assertEquals(31.2.sp, t.titleLarge.lineHeight)
        assertEquals(FontWeight.Bold, t.titleLarge.fontWeight)
        assertEquals(18.sp, t.titleMedium.fontSize)
        assertEquals(25.2.sp, t.titleMedium.lineHeight)
        assertEquals(FontWeight.Bold, t.titleMedium.fontWeight)
        assertEquals(15.sp, t.titleSmall.fontSize)
        assertEquals(21.sp, t.titleSmall.lineHeight)
        assertEquals(FontWeight.SemiBold, t.titleSmall.fontWeight)
        assertEquals(14.sp, t.bodyLarge.fontSize)
        assertEquals(22.4.sp, t.bodyLarge.lineHeight)
        assertEquals(12.sp, t.bodyMedium.fontSize)
        assertEquals(18.sp, t.bodyMedium.lineHeight)
        assertEquals(11.sp, t.labelLarge.fontSize)
        assertEquals(15.4.sp, t.labelLarge.lineHeight)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)
        assertEquals(BareZenUiFontFamily, t.titleLarge.fontFamily)
    }

    @Test fun monoRolesUseJetBrainsMono() {
        assertEquals(13.sp, BareZenMonoBody.fontSize)
        assertEquals(19.5.sp, BareZenMonoBody.lineHeight)
        assertEquals(BareZenMonoFontFamily, BareZenMonoBody.fontFamily)
        assertEquals(11.sp, BareZenMonoSmall.fontSize)
        assertEquals(BareZenMonoFontFamily, BareZenMonoSmall.fontFamily)
    }
}
```

`shared/src/jvmTest/.../app/ThemeWiringTest.kt`：

```kotlin
package com.barezen.barezen_ssh.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import com.barezen.barezen_ssh.ui.theme.BareZenUiFontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeWiringTest {
    /** 裸 Text 的字体来源：BareZenTheme 必须把 LocalTextStyle 指到 Noto 字族。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun themeProvidesUiFontAsLocalTextStyle() = runComposeUiTest {
        var captured = androidx.compose.ui.text.TextStyle()
        setContent { BareZenTheme { captured = LocalTextStyle.current } }
        assertEquals(BareZenUiFontFamily, captured.fontFamily)
    }
}
```

跑红：`--tests '*TypographyTest'`（编译失败 = 红）。

### GREEN

**步骤 1 — 下载字体（随包，OFL）**：

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH/shared/src/commonMain/composeResources/font
for w in Regular Medium Bold; do
  curl -fL --retry 3 --retry-delay 2 -x http://127.0.0.1:7897 -o "NotoSansSC-$w.otf" \
    "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/SubsetOTF/SC/NotoSansSC-$w.otf" \
  || curl -fL --retry 3 -x http://127.0.0.1:7897 -o "NotoSansSC-$w.otf" \
    "https://github.com/notofonts/noto-cjk/raw/main/Sans/SubsetOTF/SC/NotoSansSC-$w.otf"
done
ls -la NotoSansSC-*.otf   # 三个文件各约 4–9MB；-f 保证 404 不落脏文件
```

（探测已验证：该路径 200；`SemiBold.otf` **不存在**，SC 静态字重 = Thin/Light/DemiLight/Regular/Medium/Bold/Black。）

**步骤 2 — `Type.kt` 整体替换**：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Type.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import barezen_ssh.shared.generated.resources.JetBrainsMono_Bold
import barezen_ssh.shared.generated.resources.JetBrainsMono_Medium
import barezen_ssh.shared.generated.resources.JetBrainsMono_Regular
import barezen_ssh.shared.generated.resources.NotoSansSC_Bold
import barezen_ssh.shared.generated.resources.NotoSansSC_Medium
import barezen_ssh.shared.generated.resources.NotoSansSC_Regular
import barezen_ssh.shared.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** UI 正文字族：Noto Sans SC 随包分发（OFL，Sans/SubsetOTF/SC）。 */
val BareZenUiFontFamily = FontFamily(
    Font(Res.font.NotoSansSC_Regular, FontWeight.Normal),
    Font(Res.font.NotoSansSC_Medium, FontWeight.Medium),
    Font(Res.font.NotoSansSC_Bold, FontWeight.Bold),
    // 静态 OTF 无 600：SemiBold 显式复用 Bold 字面，避免合成加粗
    Font(Res.font.NotoSansSC_Bold, FontWeight.SemiBold),
)

/** 等宽字族：终端外的主机地址、延迟、指标数值（设计包 §3 等宽行）。 */
val BareZenMonoFontFamily = FontFamily(
    Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
    Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
    Font(Res.font.JetBrainsMono_Bold, FontWeight.Bold),
)

/** 设计包 §3 字号阶（行高按倍率换算为字面值，保证与测试同字面量）。 */
val BareZenTypography = Typography(
    titleLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 24.sp, lineHeight = 31.2.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 14.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontFamily = BareZenUiFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight.Medium),
)

/** 等宽样式对（屏内显式引用：主机地址用 Body、延迟/指标用 Small）。 */
val BareZenMonoBody = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 13.sp, lineHeight = 19.5.sp)
val BareZenMonoSmall = TextStyle(fontFamily = BareZenMonoFontFamily, fontSize = 11.sp, lineHeight = 15.4.sp)
```

> 回退注记：若 `Font(...)` 顶层用法编译报「仅 Composable 上下文可用」，把两个字族 getter 改回 `@Composable get()`，`BareZenTypography` 改为 `@Composable get() = Typography(...)`，并把 `TypographyTest` 改为 `runComposeUiTest { setContent { BareZenTheme { captured = MaterialTheme.typography } }; … }` 断言（`ThemeWiringTest` 不受影响）。走此回退须在报告注明。

**步骤 3 — `Theme.kt` 的 `BareZenTheme` 接 LocalTextStyle**（裸 `Text` 默认即 Noto，屏内 `fontSize=…` 便捷参数只覆盖字号、字族继承）：

```kotlin
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun BareZenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BareZenDarkColors,
        typography = BareZenTypography,
        shapes = BareZenShapes,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        ) { content() }
    }
}
```

**验证**：`--tests '*TypographyTest'`、`--tests '*ThemeWiringTest'` 绿；全量 `:shared:jvmTest`（此刻起所有 UI 测试都在真实加载 Noto——字体文件缺失会当场炸红，即随包成功的负向证明）。可再 `unzip -l shared/build/processedResources/jvm/main/composeResources/.. `或 `ls` 生成物确认 OTF 进了产物（证据写报告）。

**提交**：`feat: Noto Sans SC 随包分发并接线字号阶与 UI 字体`

---

## Task 3：终端 16 色板独立注入（JediTerm SettingsProvider）

**触碰**：新增 `terminal/TerminalPalette.kt`、`terminal/TerminalView.kt`、新增 `jvmTest/.../terminal/TerminalPaletteTest.kt`
**原则**：UI 主题不派生终端色（设计包 §终端方案末条）；16 色在 `#1E1E1E` 上全部 ≥4.5:1。

### RED — `TerminalPaletteTest.kt`

```kotlin
package com.barezen.barezen_ssh.terminal

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalPaletteTest {
    @Test fun paletteMatchesDesignPack() {
        assertEquals(0xCCCCCC, TerminalPalette.Foreground)
        assertEquals(0x8A8A8A, TerminalPalette.Dim)
        assertEquals(0xE45C5C, TerminalPalette.Red)
        assertEquals(0x0DBC79, TerminalPalette.Green)
        assertEquals(0xE5E510, TerminalPalette.Yellow)
        assertEquals(0x4AA8FF, TerminalPalette.Blue)
        assertEquals(0xD670D6, TerminalPalette.Magenta)
        assertEquals(0x33B8D9, TerminalPalette.Cyan)
        assertEquals(0x1E1E1E, TerminalPalette.Background)
    }

    @Test fun allTextColorsPassAAOnTerminalBg() {
        listOf(
            TerminalPalette.Foreground, TerminalPalette.Dim, TerminalPalette.Red,
            TerminalPalette.Green, TerminalPalette.Yellow, TerminalPalette.Blue,
            TerminalPalette.Magenta, TerminalPalette.Cyan,
        ).forEach { fg ->
            assertTrue(contrast(TerminalPalette.Background, fg) >= 4.5, "#${fg.toString(16)} 未达 AA")
        }
    }

    private fun contrast(bgHex: Int, fgHex: Int): Double {
        fun lum(hex: Int): Double {
            fun ch(v: Int): Double {
                val c = v / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * ch((hex shr 16) and 0xFF) + 0.7152 * ch((hex shr 8) and 0xFF) + 0.0722 * ch(hex and 0xFF)
        }
        val (hi, lo) = listOf(lum(bgHex), lum(fgHex)).sorted().reversed()
        return (hi + 0.05) / (lo + 0.05)
    }
}
```

### GREEN

**`TerminalPalette.kt`（新增）**：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/terminal/TerminalPalette.kt
package com.barezen.barezen_ssh.terminal

import com.jediterm.terminal.TerminalColor

/**
 * 终端 16 色（docs/ui-redesign/index.html §2）：独立于 UI 主题，24-bit RGB。
 * 亮色 8–15 与 1–6 同值（设计包仅列 8 色且声明 16 色均达 AA）；黑 0/亮白 15 沿用 JediTerm 默认。
 */
object TerminalPalette {
    const val Foreground = 0xCCCCCC
    const val Dim = 0x8A8A8A
    const val Red = 0xE45C5C
    const val Green = 0x0DBC79
    const val Yellow = 0xE5E510
    const val Blue = 0x4AA8FF
    const val Magenta = 0xD670D6
    const val Cyan = 0x33B8D9
    const val Background = 0x1E1E1E

    internal fun rgb(hex: Int): TerminalColor =
        TerminalColor.rgb((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF)
}
```

**`TerminalView.kt` 修改（3 处）**：

1. `background = Color(0x1E, 0x1E, 0x1E)` → `background = Color(TerminalPalette.Background)`（`java.awt.Color(int)` 接受 0xRRGGBB）。
2. `getDefaultStyle()` → `TextStyle(TerminalPalette.rgb(TerminalPalette.Foreground), TerminalPalette.rgb(TerminalPalette.Background))`（保留原 `@Suppress("OVERRIDE_DEPRECATION")` 与注释）。
3. `BareZenTerminalSettings` 内新增覆写（方法签名以 JediTerm 3.73 `TerminalSettings` 接口为准——先 `grep` 依赖源码或编译器提示核对方法名，再落码）：

```kotlin
override fun getANSIColor(index: Int): TerminalColor = when (index) {
    1, 9 -> TerminalPalette.rgb(TerminalPalette.Red)
    2, 10 -> TerminalPalette.rgb(TerminalPalette.Green)
    3, 11 -> TerminalPalette.rgb(TerminalPalette.Yellow)
    4, 12 -> TerminalPalette.rgb(TerminalPalette.Blue)
    5, 13 -> TerminalPalette.rgb(TerminalPalette.Magenta)
    6, 14 -> TerminalPalette.rgb(TerminalPalette.Cyan)
    else -> super.getANSIColor(index)   // 黑 0 / 白 7 / 亮黑 8 / 亮白 15 沿用默认
}
```

若 `DefaultSettingsProvider` 不提供 `getANSIColor` 的可覆写实现（接口在 `com.jediterm.terminal.ui.settings.TerminalSettings`），改为在 `BareZenTerminalSettings` 显式 `override` 接口方法并委托 `super`；报告写明实际签核的方法名。

**验证**：`--tests '*TerminalPaletteTest'` 绿；全量回归。JediTerm 的灰/黄等明暗差异**目检留到 Task 9 走查**（本任务不启动应用）。

**提交**：`feat: 终端 16 色板经 JediTerm SettingsProvider 独立注入`

---

## Task 4：服务器屏改版（横幅态机、空态、统计占位、词表新词）

**触碰**：`ServersScreen.kt`、`ServersScreenTest.kt`

### RED — `ServersScreenTest.kt` 增补（保留既有 2 测试不动）

```kotlin
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText

    @OptIn(ExperimentalTestApi::class)
    @Test fun vocabularyAndReconnectPlaceholder() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("打开文件传输").assertIsDisplayed()     // 词表新词
        onNodeWithText("打开文件管理").assertDoesNotExist()    // 旧词清零
        onNodeWithText("全部重连").assertIsNotEnabled()        // 占位 M2：禁用
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyStateShowsAddFirstServer() = runComposeUiTest {
        val m = AppModel(
            repo = InMemoryServerRepository(emptyList()),
            ssh = object : SshClient { override suspend fun connect(request: ConnectRequest) = error("unused") },
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithText("添加第一台服务器以开始使用").assertIsDisplayed()
        onNodeWithText("新建服务器").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedBannerShowsErrorAndRetryOpensDialog() = runComposeUiTest {
        val m = model()
        val failed = Server("1", "web-01", "10.0.0.11", 22, "root")
        m.applyConnectionForTest(ConnectionState.Failed(failed, "Auth fail"))
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("连接失败：Auth fail").assertIsDisplayed()
        onNodeWithText("重试").performClick()
        onNodeWithText("连接到 web-01").assertIsDisplayed()     // 重试 = 重开认证对话框
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedCardShowsStatPlaceholders() = runComposeUiTest {
        val m = model()
        val up = Server("1", "web-01", "10.0.0.11", 22, "root")
        m.applyConnectionForTest(ConnectionState.Connected(up, 24))
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("负载").assertIsDisplayed()
        onNodeWithText("内存").assertIsDisplayed()
        onNodeWithText("运行").assertIsDisplayed()
        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()
        // 三个指标值均为「—」（M4 占位，不造数）
        assertEquals(3, onAllNodesWithText("—").fetchSemanticsNodes().size)
    }
```

（补 import：`com.barezen.barezen_ssh.ssh.ConnectionState`、`kotlin.test.assertEquals`。）

跑红：`--tests '*ServersScreenTest'`。

### GREEN — `ServersScreen.kt` 改造点

1. **`ServersHeader` 横幅二态**（方法签名加 `onRetry: (Server) -> Unit`，实参传 `model.requestConnect`）：
   - `model.connection is ConnectionState.Failed` → 错误横幅：`Surface(color = colorScheme.errorContainer, shape = RoundedCornerShape(8.dp))`，行内 error 色 `Icons.Outlined.Error` + `Text("连接失败：${state.message}", color = colorScheme.onErrorContainer, fontSize = 13.sp)` + 弹性间隔 + `TextButton("重试") { onRetry(state.server) }`。
   - 否则 → 现有未连接计数横幅，**行尾追加** `Button(onClick = {}, enabled = false, modifier = Modifier.testTag("reconnect-all")) { Text("全部重连") }`（ghost 观感：`TextButton` 形态亦可，必须 `enabled = false`）。横幅圆角 12→8。
2. **空态/筛选空态**（`ServersScreen` 网格内，`visibleServers` 为空时插入跨全宽 item，需要把 `dialogOpen` 的开闭回调传进去）：
   - `model.servers` 为空 → 居中列：`Icons.Outlined.Dns`(32dp, onSurfaceVariant) + `Text("暂无服务器")`(`titleSmall`) + `Text("添加第一台服务器以开始使用")`(12sp, onSurfaceVariant) + `Button { Text("新建服务器") }`（复用 `dialogOpen = true`）。
   - 有服务器但筛选为空 → 同布局，文案 `「没有匹配的服务器」`/`「换个关键词或标签再试试」`（设计包未覆盖此分支，沿用空态骨架；报告注明）。
3. **卡片**（`ServerCard`）：
   - 圆角 12→8，`Surface` 加 `border = BorderStroke(1.dp, colorScheme.outline)`（import `androidx.compose.foundation.BorderStroke`）。
   - 名称 `Text(server.name, style = MaterialTheme.typography.titleSmall)`；地址行改 `style = BareZenMonoBody`（等宽 13，import `com.barezen.barezen_ssh.ui.theme.BareZenMonoBody`）。
   - `isUp` 时插入统计瓦片行（替换提示体位置的兄弟分支）：三个 1fr 小瓦片 `Surface(color = colorScheme.surfaceContainerLow? 不——卡片底已是 panel，瓦片用 colorScheme.background + BorderStroke(1.dp, outlineVariant), shape 6.dp)`，内列 `Text(负载/内存/运行, 10sp, onSurfaceVariant)` + `Text("—", style = BareZenMonoSmall)`。
   - 状态行三分支：`isUp` → 圆点 primary + `已连接 · {latency} ms`；`connection is Failed && connection.server.id == server.id` → 圆点 `colorScheme.error` + `Text("连接失败", color = colorScheme.error)`；否则灰点 + `未连接`。`连接` TextButton 保留（失败态同样给重试入口）。
   - 按钮词表：`Text("打开文件管理")` → `Text("打开文件传输")`。
4. **右下角新建**：删掉 `FloatingActionButton`，同位置 `Button(onClick = { editing = null; dialogOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp), shape = RoundedCornerShape(6.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("新建服务器") }`（primary 自动 accent/onAccent）。
5. **编辑对话框**：`ServerEditDialog` 圆角 12→8，其余词表已合规不动。

**验证**：`--tests '*ServersScreenTest'` 绿 + 全量回归（`AppShellTest` 此刻仍绿——它断言的旧占位文案在 Task 7/8 才拆）。

**提交**：`feat: 服务器屏改版——横幅态机、空态、统计占位与词表新词`

---

## Task 5：连接中/失败覆盖层 + 可取消连接

**触碰**：`AppModel.kt`（connectJob/cancelConnect）、新增 `ui/shell/ConnectFlowOverlays.kt`、`AppShell.kt`（挂载一行）、`ConnectDialog.kt`（安全说明行）、新增 `jvmTest/.../app/ConnectFlowTest.kt`

### RED — `ConnectFlowTest.kt`

```kotlin
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertTrue

class ConnectFlowTest {
    private val web01 = Server("1", "web-01", "10.0.0.11", 22, "root")

    private fun model(ssh: SshClient) = AppModel(
        repo = InMemoryServerRepository(listOf(web01)),
        ssh = ssh,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val hangingSsh = object : SshClient {
        val gate = CompletableDeferred<Unit>()
        override suspend fun connect(request: ConnectRequest): SshSession = gate.await() as SshSession
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectingDialogShowsAndCancelRestoresDisconnected() = runComposeUiTest {
        val m = model(hangingSsh)
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Connecting)
        setContent { BareZenAppContent(model = m) }
        onNodeWithText("正在连接 web-01…").assertIsDisplayed()
        onNodeWithText("取消").performClick()
        assertTrue(m.connection is ConnectionState.Disconnected)
        onNodeWithText("正在连接 web-01…").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedDialogDismissLeavesBanner() = runComposeUiTest {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("Auth fail")
        })
        m.requestConnect(web01)
        m.confirmConnect(AuthMethod.Password("pw"))
        assertTrue(m.connection is ConnectionState.Failed)
        setContent { BareZenAppContent(model = m) }
        // 对话框与横幅同文案：先 2 个节点，关掉对话框后剩横幅 1 个
        assertEquals(2, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("取消").performClick()
        assertEquals(1, onAllNodesWithText("连接失败：Auth fail").fetchSemanticsNodes().size)
        onNodeWithText("重试").assertIsDisplayed()   // 横幅上的重试仍在
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectDialogShowsMemoryOnlySecurityNote() = runComposeUiTest {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = error("unused")
        })
        setContent { BareZenAppContent(model = m) }
        m.requestConnect(web01)                       // 弹认证对话框
        onNodeWithText("密码仅保存在内存中，不会写入本地文件。").assertIsDisplayed()
    }
}
```

> `hangingSsh.connect` 的返回类型按 `SshClient` 接口实测签名写（`SshClient.connect` 返回 `SshSession`；`gate.await()` 挂起不返回即可——用 `suspendCoroutine`/`CompletableDeferred` 挂住，若类型不匹配改为 `gate.await(); throw CancellationException()` 之外的可编译写法，报告注明）。`onAllNodesWithText`、`assertEquals` 记得 import。

跑红：`--tests '*ConnectFlowTest'`（编译失败 = 红）。

### GREEN

**1. `AppModel.kt`（唯一功能层改动）**：

```kotlin
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException

    /** 进行中的连接协程；取消连接用（设计包连接中对话框「取消」）。 */
    private var connectJob: Job? = null

    fun startConnect(server: Server, auth: AuthMethod) {
        if (connection is ConnectionState.Connecting) return
        connection = ConnectionState.Connecting(server)
        connectJob = scope.launch {
            try {
                shellSession?.close()
                shellSession = null
                val session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
                shellSession = session
                connection = ConnectionState.Connected(server, session.pingMs())
                current = Destination.TERMINAL
            } catch (e: CancellationException) {
                throw e                       // 取消不落失败态：由 cancelConnect 置 Disconnected
            } catch (e: Exception) {
                connection = ConnectionState.Failed(server, e.message ?: e.toString())
            } finally {
                connectJob = null
            }
        }
    }

    /** 取消进行中的连接：掐协程并回落 Disconnected（旧会话已在协程里收口，无孤儿）。 */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        connection = ConnectionState.Disconnected
    }
```

（`scope.launch` 原本就在；把原 try/catch 挪进 job 即可，其余逻辑逐字保留。）

**2. 新增 `ui/shell/ConnectFlowOverlays.kt`**：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/shell/ConnectFlowOverlays.kt
package com.barezen.barezen_ssh.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextUnit.Companion  // 用不到就删
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.ssh.ConnectionState

/**
 * 连接中 / 失败全局覆盖层（设计包「连接流程」三态）。
 * 终端屏自理 Connecting/Failed 状态机，故在终端屏不叠加，避免双弹。
 */
@Composable
fun ConnectFlowOverlays(model: AppModel) {
    if (model.current == com.barezen.barezen_ssh.app.Destination.TERMINAL) return
    val connection = model.connection
    var dismissed by remember { mutableStateOf<ConnectionState.Failed?>(null) }

    if (connection is ConnectionState.Connecting) {
        AlertDialog(
            onDismissRequest = { model.cancelConnect() },
            text = {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(
                        Modifier = androidx.compose.foundation.layout.PaddingValues(0.dp), // 见下方说明
                    )
                    Text("正在连接 ${connection.server.name}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { model.cancelConnect() }) { Text("取消") } },
        )
    }
    if (connection is ConnectionState.Failed && dismissed !== connection) {
        AlertDialog(
            onDismissRequest = { dismissed = connection },
            text = {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "连接失败：${connection.message}",
                        modifier = androidx.compose.foundation.layout.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = { Button(onClick = { dismissed = connection; model.requestConnect(connection.server) }) { Text("重试") } },
            dismissButton = { TextButton(onClick = { dismissed = connection }) { Text("取消") } },
        )
    }
}
```

> 写码规约（给子代理）：上面骨架里的全限定名请收成正常 import（`Column/Spacer/Modifier/Alignment/2.dp/12.dp/sp`），`Spacer` 用 `Modifier.height(12.dp)`；此文件写完必须 `:desktopApp:compileKotlin` 过编译——示例仅定结构、文案与状态逻辑，不逐字照抄排版。

**3. `ConnectDialog.kt` 安全说明行**：在 `text = { Column(...) }` 内、密码/私钥输入块**之后**追加：

```kotlin
Text(
    "密码仅保存在内存中，不会写入本地文件。",
    fontSize = 12.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

（import `androidx.compose.ui.unit.sp`——文件已有。口令同为内存态，两种认证模式下都显示此句，属实。）

**4. `AppShell.kt` 挂载**（当前壳内、`ConnectDialog` 调用旁加一行 `ConnectFlowOverlays(model)`；Task 8 重写壳时必须原样迁移这两行——`ConnectDialog` + `ConnectFlowOverlays`）。

**验证**：`--tests '*ConnectFlowTest'` 绿；全量回归。既有 `TerminalScreenTest.failedShowsErrorAndRetry` 不受影响（直屏渲染不经壳）。

**提交**：`feat: 连接中/失败覆盖层对话框与可取消连接`

---

## Task 6：终端屏改版（标签条占位、状态栏指标位）

**触碰**：`TerminalScreen.kt`、`TerminalScreenTest.kt`

### RED — `TerminalScreenTest.kt` 增补（保留既有 3 测试；`connectedShowsStatusBarWithLatency` 若因新节点断言歧义需微调，改用下列新断言为准）

```kotlin
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import kotlin.test.assertEquals

    @OptIn(ExperimentalTestApi::class)
    @Test fun tabStripHoldsDisabledPlaceholderButtons() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("+").assertIsNotEnabled()      // 多标签 M4 占位（若 + 用图标语义则断 contentDescription，见 GREEN）
        onNodeWithText("助手").assertIsNotEnabled()   // AI 助手 M5 占位
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun statusBarShowsMetricSlotsWithoutFakeValues() = runComposeUiTest {
        val m = model(ConnectionState.Connected(Server("1", "web-01", "10.0.0.11", 22, "root"), 12))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("已连接").assertIsDisplayed()
        onNodeWithText("web-01").assertIsDisplayed()
        onNodeWithText("SSH 往返 12 ms").assertIsDisplayed()
        onNodeWithText("负载 —").assertIsDisplayed()
        onNodeWithText("内存 —").assertIsDisplayed()
        onNodeWithText("运行 —").assertIsDisplayed()
        assertEquals(3, onAllNodesWithText("—").fetchSemanticsNodes().size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedStatusBarShowsOnlyDotAndLabel() = runComposeUiTest {
        val m = model(ConnectionState.Disconnected)
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("未连接").assertIsDisplayed()
        onNodeWithText("负载 —").assertDoesNotExist()   // 未连接态不渲染指标位（照设计空态）
    }
```

跑红。

### GREEN — `TerminalScreen.kt` 改造点

1. **`TerminalTabStrip` 重写**（40dp → **36dp**，下边线 2dp → 无，活动标签 elevated 底 6dp 圆角，照设计包 §终端方案与组件表）：
   - `model.connection is Connected` 时渲染会话标签：`Surface(color = colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(6.dp))` + `Icon(Icons.Outlined.Terminal, null, 14dp)` + `Text(server.name, 12sp/500)`。**不渲染 close**（显式断开=开放项）。
   - 右侧组：`Button(onClick = {}, enabled = false)` 语义 `contentDescription = "多标签占位（M4）"` + `Text("+")` 形态改 `IconButton(enabled=false)`，`contentDescription = "多标签（M4 占位）"`；`TextButton(onClick = {}, enabled = false) { Text("助手") }`。
   - 断言注意：`+` 若走 icon 语义，测试改 `onNodeWithContentDescription("多标签（M4 占位）").assertIsNotEnabled()`（RED 阶段按此调整，报告注明取哪种）。
2. **`ConnectionStatusBar`**：高 30dp → **28dp**；底色 `surfaceContainerHigh` → `surfaceContainer`（panel，组件表「状态栏 panel 背景」）；顶部 1dp divider 保留（`outlineVariant`）。已连接态排布改为：
   `[圆点][label] [| 分隔 1dp 竖线 onSurfaceVariant][server.name] [| ][SSH 往返 X ms] [弹性间隔][负载 —][内存 —][运行 —]`
   - 分隔符用 `Text(" | ", 11sp, onSurfaceVariant)` 或 1×12dp `Box(background=outlineVariant)`（取后者更贴设计，报告注明）。
   - 三个指标位各一个 `Text("负载 —")` 等（11sp，`style = BareZenMonoSmall`，值与标签同节点——测试按整串断言）。**值必须 `—`，不造数。**
   - 未连接/失败态只渲染圆点+标签（现状已如此，确认不带指标位）。
3. **字号阶接线**：`Cta`/`ConnectingPane`/`FailedPane` 文案 13sp → `14sp`（bodyLarge）或 `style = MaterialTheme.typography.bodyMedium`；`FailedPane` 容器圆角 12→8、底色 `errorContainer`（已映射 #2A1A1A）。
4. 终端空态文案不动（已是设计文案）。

**验证**：`--tests '*TerminalScreenTest'` 绿 + 全量回归。

**提交**：`feat: 终端屏改版——标签条占位与状态栏指标位`

---

## Task 7：愿景四屏占位布局 + 删除 PlaceholderScreen

**触碰**：新增 `ui/screens/{FilesScreen,DashboardScreen,PortsScreen,SettingsScreen}.kt`、删 `PlaceholderScreen.kt`、`AppShell.kt` 路由（4 行换成新屏，其余壳代码不动——完整壳重写在 Task 8）、新增 `jvmTest/.../app/PlaceholderScreensTest.kt`

### RED — `PlaceholderScreensTest.kt`

```kotlin
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.screens.DashboardScreen
import com.barezen.barezen_ssh.ui.screens.FilesScreen
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.test.Test

class PlaceholderScreensTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun filesScreenSkeleton() = runComposeUiTest {
        setContent { BareZenTheme { FilesScreen() } }
        onNodeWithText("文件传输").assertIsDisplayed()
        onNodeWithText("上传").assertIsNotEnabled()
        onNodeWithText("下载").assertIsNotEnabled()
        onNodeWithText("本地").assertIsDisplayed()
        onNodeWithText("远程").assertIsDisplayed()
        onNodeWithText("名称").assertIsDisplayed()
        onNodeWithText("大小").assertIsDisplayed()
        onNodeWithText("修改时间").assertIsDisplayed()
        onNodeWithText("传输队列占位（M2）").assertIsDisplayed()
        onNodeWithText("占位（M2）").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dashboardScreenHoldsMetricSlotsNotNumbers() = runComposeUiTest {
        setContent { BareZenTheme { DashboardScreen() } }
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("选择服务器").assertIsNotEnabled()
        onNodeWithText("刷新").assertIsNotEnabled()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
        listOf("CPU", "内存", "平均负载", "运行时间").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("CPU / 内存 / 网络折线图占位（M4）").assertIsDisplayed()
        onNodeWithText("磁盘用量条形图占位（M4）").assertIsDisplayed()
        // 四个指标值均为 —，绝无示例数值
        androidx.compose.ui.test.onAllNodesWithText("—").assertAny { }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun portsScreenHoldsRealZeroCount() = runComposeUiTest {
        setContent { BareZenTheme { PortsScreen() } }
        onNodeWithText("端口转发").assertIsDisplayed()
        onNodeWithText("新建转发").assertIsNotEnabled()
        onNodeWithText("活动转发：0 条；需要先建立 SSH 连接才能启动新的转发。").assertIsDisplayed()
        onNodeWithText("转发表单占位（M3）").assertIsDisplayed()
        onNodeWithText("类型：本地监听 / 服务器监听 / SOCKS5").assertIsDisplayed()
        onNodeWithText("已保存的配置 / 活动转发列表占位（M3）").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun settingsEightCategoriesSwitchClientSide() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen() } }
        listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")
            .forEach { onNodeWithText(it).assertIsDisplayed() }
        // 默认分类：外观 4 行且全部禁用
        onNodeWithText("主题").assertIsDisplayed()
        onNodeWithText("跟随系统").assertIsNotEnabled()
        onNodeWithText("界面字体").assertIsDisplayed()
        onNodeWithText("显示语言").assertIsDisplayed()
        onNodeWithText("简体中文").assertIsNotEnabled()
        // 切分类（客户端状态）
        onNodeWithText("存储").performClick()
        onNodeWithText("「存储」设置页占位。").assertIsDisplayed()
    }
}
```

（`assertAny` 那行若 API 不顺手，改成 `assertEquals(4, onAllNodesWithText("—").fetchSemanticsNodes().size)`。）

跑红。

### GREEN — 四个新屏（全部只读骨架，**一个假数据都不许有**）

**通用**：屏头 `Row(高度 56.dp, padding horizontal 24.dp)` = `Text(title, style = MaterialTheme.typography.titleLarge)` + 弹性间隔 + 右侧操作；内容区 `Column(padding 24.dp)`；卡片一律 `Surface(color = colorScheme.background, shape = 8.dp, border = BorderStroke(1.dp, colorScheme.outline))`——照 index.html 各占位稿逐元素落。

**`FilesScreen.kt`**：

- 头：`文件传输` + `OutlinedButton("上传", enabled=false)` + `OutlinedButton("下载", enabled=false)`。
- 双栏 `Row(weight(1f), spacedBy(16.dp))`：每栏 `file-pane` = panel 底、8dp 圆角、1dp border；栏头 `本地`/`远程`（panel 上条、12sp/500）；列头行 `名称|大小|修改时间`（12sp secondary、底边 1dp borderSubtle）；栏体 `weight(1f)` 居中 `Text("占位（M2）", 12sp, onSurfaceVariant)`——**不渲染示例文件行**。
- 底部队列条：高 48dp、panel 底、顶 1dp border、`Icon(Icons.Outlined.Upload, null, 16dp)` + `Text("传输队列占位（M2）", 12sp, textSecondary)`。

**`DashboardScreen.kt`**：

- 头：`仪表盘` + `OutlinedButton("选择服务器", enabled=false)` + 弹性 + `Text("数据来源：SSH 主机指标", 12sp, onSurfaceVariant)` + `OutlinedButton("刷新", enabled=false)`。
- 指标格 `LazyVerticalGrid`/`Row` 四卡（CPU/内存/平均负载/运行时间）：panel/8/1dp border，标签 10sp secondary + 值 `Text("—", style = BareZenMonoBody)`。
- 两张图表卡：`Box(height 160.dp)` 同卡片皮肤，标题 `CPU / 内存 / 网络折线图占位（M4）`、`磁盘用量条形图占位（M4）`（13sp secondary，顶部），卡内居中 `Icon(Icons.Outlined.Insights, null, 28dp, onSurfaceVariant 弱化)`。

**`PortsScreen.kt`**：

- 头：`端口转发` + `Button("新建转发", enabled=false)`。
- 信息横幅：panel/8dp/1dp border、`Icons.Outlined.Info` + `Text("活动转发：0 条；需要先建立 SSH 连接才能启动新的转发。", 13sp)`（**0 条是真值**）。
- 卡 1：标题 `转发表单占位（M3）` + 副文 `类型：本地监听 / 服务器监听 / SOCKS5`（12sp secondary）。
- 卡 2：标题 `已保存的配置 / 活动转发列表占位（M3）`。
- （入口 badge 不渲染——见非目标。）

**`SettingsScreen.kt`**：

- `Row`：左列宽 200dp（panel 底、右 1dp border），8 项 `外观/终端/连接/智能助手/凭据/存储/更新/关于`，选中项 `background(elevated)`+文字 primary，未选中 transparent+secondary；`remember { mutableStateOf(0) }` 客户端切分类。
- 右侧内容 `Column(padding 24.dp)`：
  - 分类 0（外观）：`Text("外观", style = titleMedium)` + 4 行 `SettingRow(label, desc, buttonLabel)`：
    - `主题` / `跟随系统 / 浅色 / 深色` / 按钮 `跟随系统`（禁用）
    - `界面字体` / `Noto Sans SC` / 按钮 `选择`（禁用）
    - `界面缩放` / — / 按钮 `100%`（禁用）
    - `显示语言` / — / 按钮 `简体中文`（禁用）
    行式样：panel 底、6dp 圆角、1dp borderSubtle、padding 12/16，左列 label(14sp/500)+desc(12sp secondary)，右列 `OutlinedButton(enabled=false)`。
  - 其余分类：居中空态 `Icon(Icons.Outlined.Settings, 32dp)` + `Text("「{name}」设置页占位。", 13sp, onSurfaceVariant)`。

**改 `AppShell.kt` 路由**（本任务只动 4 行 import + when 分支）：`FILES → FilesScreen()`、`DASHBOARD → DashboardScreen()`、`PORTS → PortsScreen()`、`SETTINGS → SettingsScreen()`；**删除** `PlaceholderScreen` import 与 `ui/screens/PlaceholderScreen.kt` 文件。

**验证**：`--tests '*PlaceholderScreensTest'` 绿；全量回归——`AppShellTest` 中 `「设置」功能尚未启用。` 断言此刻会**红**：属预期，本任务只改 AppShellTest 那 2 行（把旧占位断言替换为 `onNodeWithText("外观").assertIsDisplayed()`），完整重写留给 Task 8；报告注明。词表扫描：`grep -rn "功能尚未启用" shared/src` = 0 命中。

**提交**：`feat: 愿景四屏占位布局（文件/仪表盘/端口转发/设置）`

---

## Task 8：外壳改版——200px 可折叠侧边导航

**触碰**：`Destination.kt`、`AppShell.kt`（整体重写）、`AppShellTest.kt`（整体重写）

### RED — `AppShellTest.kt` 整体替换

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/AppShellTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlin.test.Test

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        // 六目的地照设计包 IA 顺序（仪表盘首位）
        listOf("仪表盘", "服务器", "终端", "文件", "端口转发", "设置").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
        // 默认目的地仍是服务器（功能优先，落点不换）
        onNodeWithText("暂无服务器").assertIsDisplayed()
        onNodeWithText("终端").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithText("文件").performClick()
        onNodeWithText("传输队列占位（M2）").assertIsDisplayed()
        onNodeWithText("设置").performClick()
        onNodeWithText("跟随系统").assertIsNotEnabled()
        onNodeWithText("仪表盘").performClick()
        onNodeWithText("数据来源：SSH 主机指标").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sidebarCollapsesToIconsAndExpandsBack() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        onNodeWithText("服务器").assertIsDisplayed()
        onNodeWithTag("sidebar-toggle").performClick()          // 收起
        onNodeWithText("服务器").assertDoesNotExist()           // 文字标签隐藏（精确匹配，不误中「新建服务器」）
        onNodeWithTag("sidebar-toggle").performClick()          // 展开
        onNodeWithText("服务器").assertIsDisplayed()
    }
}
```

跑红。

### GREEN

**1. `Destination.kt` 重排**（label 不变）：

```kotlin
enum class Destination(val label: String) {
    DASHBOARD("仪表盘"), SERVERS("服务器"), TERMINAL("终端"),
    FILES("文件"), PORTS("端口转发"), SETTINGS("设置")
}
```

（`AppModel.current` 初值仍是 `SERVERS`——**不改**；`startConnect` 成功跳 `TERMINAL` 不动。）

**2. `AppShell.kt` 整体重写**（保留 `BareZenAppContent` 对外函数名与 ConnectDialog/ConnectFlowOverlays 挂载；`grep -rn "Badge(" shared/src` 确认旧硬编码 `Badge("2")` 连同 `androidx.compose.material3.Badge` import 一起消失）：

```kotlin
@Composable
fun BareZenAppContent(model: AppModel) {
    var collapsed by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppSidebar(
            model = model,
            collapsed = collapsed,
            onToggle = { collapsed = !collapsed },
            modifier = Modifier.fillMaxHeight(),
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            when (model.current) {
                Destination.DASHBOARD -> DashboardScreen()
                Destination.SERVERS -> ServersScreen(
                    model,
                    onNewTerminal = { model.requestConnect(it) },
                    onOpenFiles = { model.navigate(Destination.FILES) },
                )
                Destination.TERMINAL -> TerminalScreen(model)
                Destination.FILES -> FilesScreen()
                Destination.PORTS -> PortsScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
    if (model.pendingConnect != null) {
        ConnectDialog(model.pendingConnect!!) { auth ->
            if (auth != null) model.confirmConnect(auth) else model.dismissConnect()
        }
    }
    ConnectFlowOverlays(model)
}
```

（`ConnectDialog`/`onNewTerminal`/`onOpenFiles` 的现有接线以**当前文件实测**为准——上面是结构示意，迁移时逐行对照旧壳，行为不变：`onNewTerminal` 现接什么就保留什么，仅壳的导航部分换血。）

**`AppSidebar`（新私有组件）**：

- 宽度：展开 **200.dp** / 折叠 **56.dp**；`Column(Modifier.fillMaxHeight().width(w).background(colorScheme.surface))`，右缘 1dp `borderSubtle` 竖线（Box 后置或 border）。
- 顶部标题行（高 56dp、padding 16）：`Icon(Icons.Outlined.Terminal, null, 20dp, tint = primary)` + `Text("BareZen", titleSmall)`；折叠时只留图标。
- 列表按枚举顺序渲染，**「文件」与「端口转发」之间插 `Spacer(Modifier.weight(1f))`**（设计包 mock-nav-spacer 位置）：实际渲染顺序 `DASHBOARD, SERVERS, TERMINAL, FILES, [weight spacer], PORTS, SETTINGS, [weight spacer], 收起按钮`。
- 导航项：`Row(填宽, 高 40dp, padding horizontal 12, spacedBy 12, clip 6dp, background(if selected) colorScheme.surfaceContainer else Transparent)` + 图标（20dp，选中 tint = `onSurface`，未选 `onSurfaceVariant`）+ `Text(label, 14sp, 选中 primary 未选 secondary)`；整行 `Modifier.clickable { model.navigate(dest) }` + `semantics { selected = ... }`。
- 图标映射（material-icons-extended 已在依赖 `libs.compose.materialIcons.extended`）：仪表盘 `Icons.Outlined.Monitoring`、服务器 `Icons.Outlined.Dns`、终端 `Icons.Outlined.Terminal`、文件 `Icons.Outlined.Folder`、端口转发 `Icons.Outlined.SyncAlt`、设置 `Icons.Outlined.Settings`。**若 `Monitoring`/`SyncAlt` 编译不过，回退 `QueryStats`/`SwapHoriz` 并在报告注明**。
- 底部收起按钮：`Row(填宽, 高 40dp, padding 12, spacedBy 12, clickable, Modifier.testTag("sidebar-toggle"))`，内容=展开态 `Icon(Icons.Filled.ChevronLeft) + Text("收起", 12sp secondary)`，折叠态仅 `Icon(Icons.Filled.ChevronRight)`；`contentDescription = if (collapsed) "展开侧边栏" else "收起侧边栏"`。
- **不渲染 badge**（非目标）。

**验证**：`--tests '*AppShellTest'` 绿 → 全量 `:shared:jvmTest`（此时全量应全绿）→ `:desktopApp:compileKotlin`。

**提交**：`feat: 外壳改版——200px 可折叠侧边导航`

---

## Task 9：收尾——全量回归、编译、设计包真机走查

**触碰**：无源码改动预期（走查发现问题 → 按「计划内 fix 模板」小修，报告列拟议修复）。

1. **全量测试**（先 `rm -rf ~/.cache/fontconfig/` 防挂）：

   ```bash
   timeout -k 15 600 ./gradlew --no-daemon :shared:jvmTest
   ```

   断言全绿；测试数应 ≥44（基线 44 + 新增，删除/合并在报告列账）。

2. **编译**：`timeout -k 15 600 ./gradlew --no-daemon :desktopApp:compileKotlin`

3. **真机走查**（`DISPLAY=:1`；**启动后记录 PID，收场只按 PID kill**）：

   ```bash
   ./gradlew --no-daemon :desktopApp:run &   # 记录其打印的 java PID
   ```

   对照 `docs/ui-redesign/prototype.html` 六屏逐项截图核验（截图存 `/tmp/opencode/ui-redesign-walkthrough/`）：

   - [ ] 仪表盘：占位四卡 `—`、图表占位标题、控件禁用
   - [ ] 服务器：横幅+禁用全部重连、卡片统计 `—`（连接真实服务器后仍为 `—`，不得造数）、词表新词、右下新建按钮
   - [ ] 连接流：认证对话框（含安全说明行）→ 连接中对话框（可取消）→ 失败对话框/横幅（如可复现）
   - [ ] 终端：JBM 13、16 色（跑 `ls --color=always`/`uptime` 看红绿黄蓝）、状态栏 28px + `负载 —`、标签条禁用 `+`/`助手`
   - [ ] 文件 / 端口转发 / 设置：占位骨架、禁用控件、真值 0 条
   - [ ] 侧边栏：200px 展开/折叠、选中 panel 底、无 badge
   - [ ] 1180px 窗口下终端区 ≥932px、无遮挡；reduced-motion 桌面环境不引入任何位移动效
4. **收尾提交**：`feat: 收尾——全量回归与设计包走查`（走查小修与文档勾账并入此提交；若无改动则跳过提交并在报告说明）。

---

## 7. 执行与评审规约（SDD）

- **每任务一个全新子代理**，`agent: opencode/mimo-v2.6-flash-free`；brief 必含：本任务 RED/GREEN 全文、触碰文件、第 5 节命令规约、对应提交信息、非目标红线（不造数/不动功能层/禁 pkill）。
- 产出落 `.superpowers/sdd/2026-09-26-ui-redesign-graphite/`：`task-N-brief.md`、`task-N-report.md`、`task-N-review.md`；`progress.md` 为唯一进度依据（开篇先建，把本计划第 2 节计划 1 遗留分诊指引抄进去）。
- **任务评审**：每个报告出来后独立评审（测试证据、词表、非目标违例、造数检查）；发现问题 → 同任务子代理返工再审。
- **提交**：任务评审通过才提交；6–9 条计划内消息已随本文授权（第 6 节），逐条 `git log` 核对。
- **最终整体评审**（Task 9 通过后）：`review-package PLAN` + 携带两份账本——
  1. 本计划 `progress.md` 全部条目；
  2. **计划 1** `.superpowers/sdd/2026-09-24-m0-m1-shell-ssh-terminal/progress.md` 的全部 ⚠️ 分诊项（Task 9 走查缺失处置、material-icons-extended、slf4j、onClosed、远端 shell 死亡检测、TOFU 覆盖、Badge 假数据移除后的回补点、main.kt remember 等）——免独立收口的代价是**一个不许丢**。
- 评审通过后恢复 brainstorming 第 9 步出口记录（`.superpowers/brainstorm-ui-redesign.md` 勾第 9 条并指向本计划）。

## 8. 风险与回退

| 风险 | 缓解 |
|---|---|
| 字体下载失败（代理不稳） | curl `-f --retry 3` + 第二镜像已写入步骤；两源皆挂则任务阻塞上报，**不许**用系统字体糊弄（硬约束 #5） |
| `Font()` 顶层用法不容许 | Type.kt 已写 @Composable 回退路径（Task 2 注记），测试同步改组合内捕获 |
| JediTerm `getANSIColor` 签名差异 | 以 3.73 接口源为准核对，方法名变体在报告注明；色值断言测试与签名解耦 |
| `Monitoring`/`SyncAlt` 图标不存在 | 显式回退 QueryStats/SwapHoriz |
| Task 5 取消连接的竞态（cancel 与完成同时） | 失败态/成功态终值以最后写者为准，双写均落安全态（Disconnected/Connected/Failed 之一），无中间态泄漏；测试覆盖常规路径 |
| 全量测试挂起 | 先删 `~/.cache/fontconfig/`；`timeout -k 15` 兜底 |
| 设计与现码接线差异（如 ConnectDialog 现有结构） | 各任务 brief 要求「先读目标文件实测再动手」，本计划的结构示意与逐行迁移冲突时以现码行为不变为准，报告注明 |
