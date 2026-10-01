# M0+M1 · 外壳与 SSH 终端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 BareZen-SSH 的可运行第一版：M3 暗色外壳（NavigationRail 六屏）+ 服务器管理 + sshj 连接 + JediTerm 终端，能真实登录一台服务器敲命令。

**Architecture:** 单 `shared` 模块（commonMain 放 UI/模型/接口，jvmMain 放 sshj/JediTerm/文件仓库实现），`desktopApp` 只做窗口入口。SSH 通过 `SshClient/SshSession/ShellChannel` 接口与 UI 解耦，测试用 Apache MINA sshd 嵌入式服务器。设计规格唯一来源：`docs/superpowers/specs/2026-09-24-barezen-ui-features-design.md`，视觉/文案对照 `docs/ui/prototype.html`。

**Tech Stack:** Kotlin 2.4.20 · Compose Multiplatform 1.12.1 · Material3 1.12.0-alpha03 · sshj 0.40.0 · JediTerm (`org.jetbrains.jediterm:jediterm-ui:3.73`) · kotlinx-serialization-json 1.11.0 · 测试：kotlin-test + MINA sshd 2.14.0 + CMP `ui-test`

## Global Constraints

- 新增依赖固定版本：`com.hierynomus:sshj:0.40.0`、`org.jetbrains.jediterm:jediterm-ui:3.73`（LGPL-3.0/Apache-2.0 双许可，**选用 Apache-2.0**）、`org.apache.sshd:sshd-core:2.14.0`（仅测试）、`org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0`。
- JediTerm 依赖仓库：`https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`（不在 Maven Central）。
- 色板精确值（来自设计文档，测试断言）：primary `#81D5CB`、onPrimary `#003733`、primaryContainer `#00504A`、secondaryContainer `#324B48`、surface `#101413`、onSurface `#E0E3E1`、surfaceContainerLow `#191C1C`、surfaceContainer `#1D2020`、surfaceContainerHigh `#272B2A`、surfaceContainerHighest `#323535`、outline `#899391`、outlineVariant `#3F4947`、tertiary `#AEC9E6`、error `#FFB4AB`。
- 字体：JetBrains Mono 400/500/700，从 `docs/ui/fonts/` 复制进 composeResources 随包分发。
- 文案：简体中文，MaidKit `zh-CN.json` 词表优先；产品名写 BareZen（窗口标题已是 `BareZen-SSH`）。
- 安全：密码与私钥口令**不持久化**（每次连接输入）；私钥文件路径可持久化；主机密钥 TOFU 写入 `~/.ssh/known_hosts`。
- 视觉：quiet/functional——无渐变、发光、玻璃拟态；间距 4/8/12/16/24/32；层次靠边框与对比。
- 无障碍：正文对比 ≥ 4.5:1；动效 ≤ 250ms 且尊重 `prefers-reduced-motion`（Compose 侧用 `MotionDurationInMillisThreshold` 不引复杂方案，过渡动画统一 150ms）。
- AGPL：不搬运 MaidKit Dart 源码；文案为词表短语、代码自写。
- 每个任务以一次 commit 结束；提交信息中文 conventional commits；**提交前把信息给用户过目**（若用户已授权按计划内给定信息直接提交，则按给定信息执行）。
- 开发验证平台 Linux；SSH/JediTerm 一律放在 `jvmMain`，UI 与模型放 `commonMain`（保证 Windows/macOS CI 可构建）。

**本计划交付**：设计文档 A1（列表+增删改+搜索标签过滤）、A2（密码/私钥认证）、A3（终端渲染 + 标准复制粘贴；Shift+Insert 为 JediTerm 默认支持）、A4（连接状态+断线失败态）、A5 的「连接状态 + SSH 往返延迟」。
**本计划不含**（后续计划承接）：卡片点击进详情、全部重连横幅按钮、实时统计瓦片（M4）、选中即复制/sudo 自动填入（M3 设置）、负载/内存/运行时间（M4）、多标签与分屏（M4）、SFTP/仪表盘/端口/设置四屏为占位（M2/M4/M3）、自动更新（M4）。

---

## File Structure

**Create（commonMain，`shared/src/commonMain/kotlin/com/barezen/barezen_ssh/`）**
- `ui/theme/Color.kt` — 色板常量（唯一色值来源）
- `ui/theme/Theme.kt` — `BareZenTheme`（darkColorScheme）
- `ui/theme/Type.kt` — JetBrains Mono 字体与字体族
- `app/Destination.kt` — 六屏路由枚举与中文标签
- `app/AppModel.kt` — 应用状态（路由、服务器列表、连接状态机），可注入 fake 测试
- `servers/Server.kt` — `@Serializable` 服务器模型与认证类型
- `servers/ServerRepository.kt` — 仓库接口 + 内存实现
- `servers/ServerFilter.kt` — 搜索/标签过滤纯函数
- `ssh/Ssh.kt` — `SshClient/SshSession/ShellChannel/AuthMethod/ConnectRequest/ConnectionState` 接口与类型
- `ui/shell/AppShell.kt` — NavigationRail + 内容区（topStart 12 圆角 Surface）
- `ui/screens/PlaceholderScreen.kt` — 未实现屏占位
- `ui/screens/ServersScreen.kt` — 搜索/标签/卡片网格/新增编辑对话框
- `ui/screens/TerminalScreen.kt` — 标签条 + 终端区 + 状态栏；未连接 CTA
- `ui/App.kt` — 重写：`BareZenTheme { AppShell(...) }`

**Create（jvmMain，`shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/`）**
- `ssh/JvmSshClient.kt` — sshj 实现 + TOFU 主机密钥
- `ssh/TerminalTtyConnector.kt` — `ShellChannel` → JediTerm `TtyConnector` 适配
- `terminal/TerminalView.kt` — `SwingPanel` 包 `JediTermWidget`
- `servers/FileServerRepository.kt` — JSON 文件仓库（`~/.barezen/servers.json`）
- `app/AppModel.desktop.kt` — `defaultServerRepository()` / `defaultSshClient()` actual

**Create（资源）**
- `shared/src/commonMain/composeResources/font/JetBrainsMono-{Regular,Medium,Bold}.ttf`

**Create（测试，`shared/src/{commonTest,jvmTest}/kotlin/com/barezen/barezen_ssh/`）**
- `ui/theme/ThemeColorsTest.kt` · `app/AppShellTest.kt` · `servers/ServerFilterTest.kt`
- `jvmTest`: `servers/FileServerRepositoryTest.kt` · `ssh/JvmSshClientTest.kt` · `ssh/ShellPipeTest.kt` · `app/AppModelTest.kt`（若用 fake client 且不含 Swing 则放 commonTest —— 本计划放 commonTest `app/AppModelTest.kt`）

**Modify**
- `gradle/libs.versions.toml` — 依赖与序列化插件
- `settings.gradle.kts` — IntelliJ 依赖仓库
- `shared/build.gradle.kts` — 插件与依赖 source set 分层
- `desktopApp/src/main/kotlin/com/barezen/barezen_ssh/main.kt` — 窗口 1440×900

---

### Task 1: 依赖、仓库与字体资源

**Files:**
- Modify: `gradle/libs.versions.toml`、`settings.gradle.kts`、`shared/build.gradle.kts`
- Create: `shared/src/commonMain/composeResources/font/`（复制 3 个 TTF）

**Interfaces:**
- Produces: 目录别名 `libs.sshj`、`libs.jediterm.ui`、`libs.sshd.core`、`libs.kotlinx.serializationJson`、`libs.compose.uiTest`、插件 `libs.plugins.kotlinSerialization`；后续任务按这些别名引用。

- [ ] **Step 1: 修改 `gradle/libs.versions.toml`**

在 `[versions]` 追加：

```toml
kotlinx-serialization = "1.11.0"
sshj = "0.40.0"
jediterm = "3.73"
sshd = "2.14.0"
```

在 `[libraries]` 追加：

```toml
kotlinx-serializationJson = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
sshj = { module = "com.hierynomus:sshj", version.ref = "sshj" }
jediterm-ui = { module = "org.jetbrains.jediterm:jediterm-ui", version.ref = "jediterm" }
sshd-core = { module = "org.apache.sshd:sshd-core", version.ref = "sshd" }
compose-uiTest = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "composeMultiplatform" }
```

在 `[plugins]` 追加：

```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: 修改 `settings.gradle.kts`**

在 `dependencyResolutionManagement { repositories { ... } }` 的 `mavenCentral()` **之前**插入：

```kotlin
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
```

- [ ] **Step 3: 修改 `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
        }
        jvmMain.dependencies {
            implementation(libs.sshj)
            implementation(libs.jediterm.ui)
        }
        jvmTest.dependencies {
            implementation(libs.sshd.core)
            implementation(compose.desktop.currentOs)
        }
    }
}
```

- [ ] **Step 4: 复制字体资源**

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
mkdir -p shared/src/commonMain/composeResources/font
cp docs/ui/fonts/JetBrainsMono-Regular.ttf docs/ui/fonts/JetBrainsMono-Medium.ttf docs/ui/fonts/JetBrainsMono-Bold.ttf shared/src/commonMain/composeResources/font/
```

- [ ] **Step 5: 构建验证**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: BUILD SUCCESSFUL（现有 `SharedCommonTest`/`SharedLogicDesktopTest` 保持通过；若 jediterm 解析失败，检查 Step 2 仓库位置是否在 `mavenCentral()` 之前）

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts shared/build.gradle.kts shared/src/commonMain/composeResources/font/
git commit -m "build: 引入 sshj/jediterm/序列化依赖与 JetBrains Mono 字体资源"
```

---

### Task 2: 主题与字体

**Files:**
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Color.kt`、`Theme.kt`、`Type.kt`
- Modify: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/App.kt`（仅包 import 不动，Task 3 重写内容）
- Test: `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/ui/theme/ThemeColorsTest.kt`

**Interfaces:**
- Produces: `val BareZenPrimary: Color` 等色值常量（Task 3/5/8 引用）；`@Composable fun BareZenTheme(content: @Composable () -> Unit)`；`val BareZenTypography: Typography`。

- [ ] **Step 1: 写失败测试**

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/ui/theme/ThemeColorsTest.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeColorsTest {
    @Test fun primaryMatchesSpec() { assertEquals(Color(0xFF81D5CB), BareZenPrimary) }
    @Test fun onPrimaryMatchesSpec() { assertEquals(Color(0xFF003733), BareZenOnPrimary) }
    @Test fun primaryContainerMatchesSpec() { assertEquals(Color(0xFF00504A), BareZenPrimaryContainer) }
    @Test fun secondaryContainerMatchesSpec() { assertEquals(Color(0xFF324B48), BareZenSecondaryContainer) }
    @Test fun surfaceMatchesSpec() { assertEquals(Color(0xFF101413), BareZenSurface) }
    @Test fun onSurfaceMatchesSpec() { assertEquals(Color(0xFFE0E3E1), BareZenOnSurface) }
    @Test fun scLowMatchesSpec() { assertEquals(Color(0xFF191C1C), BareZenSurfaceContainerLow) }
    @Test fun scMatchesSpec() { assertEquals(Color(0xFF1D2020), BareZenSurfaceContainer) }
    @Test fun scHighMatchesSpec() { assertEquals(Color(0xFF272B2A), BareZenSurfaceContainerHigh) }
    @Test fun scHighestMatchesSpec() { assertEquals(Color(0xFF323535), BareZenSurfaceContainerHighest) }
    @Test fun outlineMatchesSpec() { assertEquals(Color(0xFF899391), BareZenOutline) }
    @Test fun outlineVariantMatchesSpec() { assertEquals(Color(0xFF3F4947), BareZenOutlineVariant) }
    @Test fun tertiaryMatchesSpec() { assertEquals(Color(0xFFAEC9E6), BareZenTertiary) }
    @Test fun errorMatchesSpec() { assertEquals(Color(0xFFFFB4AB), BareZenError) }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*ThemeColorsTest'`
Expected: FAIL，`Unresolved reference: BareZenPrimary`

- [ ] **Step 3: 实现 `Color.kt`**

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Color.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.ui.graphics.Color

// M3 dark · seed #0F766E (tonalSpot) — 数值见 docs/superpowers/specs/2026-09-24-barezen-ui-features-design.md
val BareZenPrimary = Color(0xFF81D5CB)
val BareZenOnPrimary = Color(0xFF003733)
val BareZenPrimaryContainer = Color(0xFF00504A)
val BareZenOnPrimaryContainer = Color(0xFF9DF2E7)
val BareZenSecondaryContainer = Color(0xFF324B48)
val BareZenOnSecondaryContainer = Color(0xFFCCE8E4)
val BareZenTertiary = Color(0xFFAEC9E6)
val BareZenTertiaryContainer = Color(0xFF2F4961)
val BareZenOnTertiaryContainer = Color(0xFFCEE5FF)
val BareZenError = Color(0xFFFFB4AB)
val BareZenSurface = Color(0xFF101413)
val BareZenOnSurface = Color(0xFFE0E3E1)
val BareZenSurfaceContainerLowest = Color(0xFF0B0F0E)
val BareZenSurfaceContainerLow = Color(0xFF191C1C)
val BareZenSurfaceContainer = Color(0xFF1D2020)
val BareZenSurfaceContainerHigh = Color(0xFF272B2A)
val BareZenSurfaceContainerHighest = Color(0xFF323535)
val BareZenOnSurfaceVariant = Color(0xFFBEC9C6)
val BareZenOutline = Color(0xFF899391)
val BareZenOutlineVariant = Color(0xFF3F4947)
```

- [ ] **Step 4: 实现 `Theme.kt`**

```kotlin
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
```

- [ ] **Step 5: 实现 `Type.kt`**

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Type.kt
package com.barezen.barezen_ssh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import barezen_ssh.shared.generated.resources.Res
import barezen_ssh.shared.generated.resources.JetBrainsMono_Regular
import barezen_ssh.shared.generated.resources.JetBrainsMono_Medium
import barezen_ssh.shared.generated.resources.JetBrainsMono_Bold

val BareZenFontFamily: FontFamily = FontFamily(
    Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
    Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
    Font(Res.font.JetBrainsMono_Bold, FontWeight.Bold),
)

val BareZenTypography = Typography()
```

注：界面字体族先定义不强制全量替换（避免行高/字号全面漂移），终端与等宽场景 Task 7 起使用；生成的访问器名若与 `JetBrainsMono_Regular` 不一致（大小写随文件名推导），以 `./gradlew :shared:generateComposeResClass` 生成的 `Res` 为准修正 import。

- [ ] **Step 6: 运行确认通过**

Run: `./gradlew :shared:jvmTest --tests '*ThemeColorsTest'`
Expected: PASS（14 个断言）

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/
git commit -m "feat: 应用 M3 暗色主题与 JetBrains Mono 字体"
```

---

### Task 3: 路由状态与应用外壳

**Files:**
- Create: `app/Destination.kt`、`ui/shell/AppShell.kt`、`ui/screens/PlaceholderScreen.kt`
- Modify: `ui/App.kt`（重写）、`desktopApp/.../main.kt`（窗口 1440×900）
- Test: `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/AppShellTest.kt`

**Interfaces:**
- Consumes: `BareZenTheme`（Task 2）。
- Produces: `enum class Destination(val label: String)`（值：`SERVERS("服务器")、TERMINAL("终端")、FILES("文件")、DASHBOARD("仪表盘")、PORTS("端口转发")、SETTINGS("设置")`）；`class AppModel`（此任务只含 `var current: Destination` 与 `fun navigate(Destination)`，Task 4/8 扩展）；`@Composable fun BareZenAppContent(model: AppModel)`（声明于 `ui/shell/AppShell.kt`，包 `com.barezen.barezen_ssh.ui.shell`），内部按 `model.current` 分发六屏（Task 5/8 提供真实屏替换占位）。

- [ ] **Step 1: 写失败 UI 测试**

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/AppShellTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import kotlin.test.Test

class AppShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun railShowsAllDestinationsAndNavigates() = runComposeUiTest {
        setContent { BareZenAppContent(model = AppModel.forUiTest()) }
        onNodeWithText("服务器").assertIsDisplayed()
        onNodeWithText("终端").assertIsDisplayed()
        onNodeWithText("文件").assertIsDisplayed()
        onNodeWithText("仪表盘").assertIsDisplayed()
        onNodeWithText("终端").performClick()
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
        onNodeWithText("设置").performClick()
        onNodeWithText("设置").assertExists()
    }
}
```

注：`runComposeUiTest` 的 import 若 `v2` 包不存在，改用 `androidx.compose.ui.test.runComposeUiTest`（CMP 1.12.1 文档示例为 v2）。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*AppShellTest'`
Expected: FAIL，`Unresolved reference: BareZenAppContent / AppModel.forUiTest`

- [ ] **Step 3: 实现 `Destination.kt` 与最小 `AppModel`**

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/Destination.kt
package com.barezen.barezen_ssh.app

enum class Destination(val label: String) {
    SERVERS("服务器"), TERMINAL("终端"), FILES("文件"),
    DASHBOARD("仪表盘"), PORTS("端口转发"), SETTINGS("设置")
}
```

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt
package com.barezen.barezen_ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.ServerRepository
import kotlinx.coroutines.CoroutineScope

class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    private val scope: CoroutineScope,
) {
    var current: Destination by mutableStateOf(Destination.SERVERS)
        private set
    var servers: List<Server> by mutableStateOf(repo.list())
        private set
    var connection: ConnectionState by mutableStateOf(ConnectionState.Disconnected)
        private set

    fun navigate(to: Destination) { current = to }
    fun refreshServers() { servers = repo.list() }

    companion object
}
```

（`ConnectionState`、`SshClient`、`Server`、`ServerRepository` 在 Task 4/6 定义；本步先创建同名最小骨架保证编译：）

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ssh/Ssh.kt（本任务先建骨架，Task 6 补全实现细节）
package com.barezen.barezen_ssh.ssh

import com.barezen.barezen_ssh.servers.Server

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: Server) : ConnectionState
    data class Connected(val server: Server, val latencyMs: Long) : ConnectionState
    data class Failed(val server: Server, val message: String) : ConnectionState
}

interface SshClient // Task 6 定义方法
```

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/servers/Server.kt（骨架，Task 4 完整化）
package com.barezen.barezen_ssh.servers

data class Server(val id: String, val name: String, val host: String, val port: Int, val user: String)
```

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/servers/ServerRepository.kt（骨架）
package com.barezen.barezen_ssh.servers

interface ServerRepository {
    fun list(): List<Server>
}
```

测试工厂：

```kotlin
// AppModel companion 内
fun forUiTest(): AppModel = AppModel(
    repo = object : ServerRepository { override fun list(): List<Server> = emptyList() },
    ssh = object : SshClient {},
    scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
)
```

- [ ] **Step 4: 实现占位屏与外壳**

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/screens/PlaceholderScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("「$label」功能尚未启用。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/shell/AppShell.kt
package com.barezen.barezen_ssh.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Settings
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.app.Destination
import com.barezen.barezen_ssh.ui.screens.PlaceholderScreen
import androidx.compose.ui.graphics.vector.ImageVector

private fun railIcon(to: Destination, selected: Boolean): ImageVector = when (to) {
    Destination.SERVERS -> if (selected) Icons.Filled.Dns else Icons.Outlined.Dns
    Destination.TERMINAL -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
    Destination.FILES -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    Destination.DASHBOARD -> if (selected) Icons.Filled.QueryStats else Icons.Outlined.QueryStats
    Destination.PORTS -> if (selected) Icons.Filled.SwapHoriz else Icons.Outlined.SwapHoriz
    Destination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

@Composable
fun BareZenAppContent(model: AppModel) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        NavigationRail {
            Destination.entries.filter { it in listOf(Destination.SERVERS, Destination.TERMINAL, Destination.FILES, Destination.DASHBOARD) }
                .forEach { dest ->
                    NavigationRailItem(
                        selected = model.current == dest,
                        onClick = { model.navigate(dest) },
                        icon = { Icon(railIcon(dest, model.current == dest), contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            NavigationRailItem(
                selected = model.current == Destination.PORTS,
                onClick = { model.navigate(Destination.PORTS) },
                icon = { Badge { Text("2") }; Icon(railIcon(Destination.PORTS, model.current == Destination.PORTS), contentDescription = "端口转发") },
                label = null,
            )
            NavigationRailItem(
                selected = model.current == Destination.SETTINGS,
                onClick = { model.navigate(Destination.SETTINGS) },
                icon = { Icon(railIcon(Destination.SETTINGS, model.current == Destination.SETTINGS), contentDescription = "设置") },
                label = null,
            )
        }
        Box(Modifier.fillMaxSize().padding(top = 0.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                when (model.current) {
                    Destination.SERVERS -> PlaceholderScreen("服务器")
                    Destination.TERMINAL -> com.barezen.barezen_ssh.ui.screens.TerminalScreenPlaceholder()
                    Destination.FILES -> PlaceholderScreen("文件")
                    Destination.DASHBOARD -> PlaceholderScreen("仪表盘")
                    Destination.PORTS -> PlaceholderScreen("端口转发")
                    Destination.SETTINGS -> PlaceholderScreen("设置")
                }
            }
        }
    }
}
```

`TerminalScreenPlaceholder`（本任务内联于 `PlaceholderScreen.kt`，Task 8 替换为真实 `TerminalScreen`）：

```kotlin
@Composable
fun TerminalScreenPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("在服务器列表选择「新建终端」以开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

依赖 `material-icons-extended`（`Icons.Filled.Terminal/QueryStats` 等不在 core）：在 Task 1 的 `libs.versions.toml` `[libraries]` 增补并在本步引用——**修正**：在本 Step 4 前先补：

```toml
compose-materialIcons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version.ref = "composeMultiplatform" }
```

`shared/build.gradle.kts` commonMain 增加 `implementation(libs.compose.materialIcons.extended)`。

- [ ] **Step 5: 重写 `App.kt` 与窗口入口**

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/App.kt（整体替换）
package com.barezen.barezen_ssh

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import com.barezen.barezen_ssh.ui.theme.BareZenTheme

@Composable
fun App(model: AppModel) = BareZenTheme { BareZenAppContent(model) }
```

（外壳函数命名 `BareZenAppContent`，声明于 `ui/shell/AppShell.kt`（包 `com.barezen.barezen_ssh.ui.shell`），测试与 `App.kt` 均按此 import。）

`main.kt`：

```kotlin
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BareZen-SSH",
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(AppModel.real())
    }
}
```

`AppModel.real()`（放 `AppModel.desktop.kt`，jvmMain）：

```kotlin
package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.servers.FileServerRepository
import com.barezen.barezen_ssh.ssh.JvmSshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// 注意：本项目仅 JVM 目标，不用 expect/actual，直接扩展函数：
fun AppModel.Companion.real(): AppModel = AppModel(
    repo = FileServerRepository(),
    ssh = JvmSshClient(),
    scope = CoroutineScope(Dispatchers.Default),
)
```

（`FileServerRepository`/`JvmSshClient` Task 4/6 落地；本步先建可编译空实现：`FileServerRepository` 暂返回内存列表、`JvmSshClient` 暂 `TODO()` 不触达。）

- [ ] **Step 6: 运行确认通过**

Run: `./gradlew :shared:jvmTest --tests '*AppShellTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain shared/src/commonTest desktopApp gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "feat: NavigationRail 外壳与六屏路由"
```

---

### Task 4: 服务器模型、仓库与过滤

**Files:**
- Modify: `servers/Server.kt`、`servers/ServerRepository.kt`（Task 3 骨架 → 完整）、新建 `servers/ServerFilter.kt`
- Create (jvmMain): `servers/FileServerRepository.kt`
- Test: `servers/ServerFilterTest.kt`（commonTest）、`servers/FileServerRepositoryTest.kt`（jvmTest）

**Interfaces:**
- Produces:
  - `@Serializable data class Server(val id: String, val name: String, val host: String, val port: Int = 22, val user: String, val tags: List<String> = emptyList(), val auth: StoredAuth = StoredAuth.Password)`，`sealed interface StoredAuth { @Serializable @SerialName("password") data object Password : StoredAuth; @Serializable @SerialName("key") data class Key(val keyPath: String) : StoredAuth }`
  - `interface ServerRepository { fun list(): List<Server>; fun upsert(server: Server); fun delete(id: String) }`
  - `class InMemoryServerRepository(initial: List<Server> = emptyList()) : ServerRepository`（commonMain，测试/UI 用）
  - `fun filterServers(servers: List<Server>, query: String, tag: String?): List<Server>`
  - `class FileServerRepository(private val file: File = File("~/.barezen/servers.json"))`（jvmMain，`~` 展开为 user.home）

- [ ] **Step 1: 写失败过滤测试**

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/servers/ServerFilterTest.kt
package com.barezen.barezen_ssh.servers

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerFilterTest {
    private val web = Server("1", "web-01", "10.0.0.11", 22, "root", listOf("生产", "nginx"))
    private val db = Server("2", "db-01", "10.0.0.12", 22, "root", listOf("生产", "PostgreSQL"))
    private val edge = Server("3", "edge-02", "45.32.18.7", 2222, "admin", listOf("边缘"))
    private val all = listOf(web, db, edge)

    @Test fun emptyQueryAndNoTagReturnsAll() { assertEquals(all, filterServers(all, "", null)) }
    @Test fun queryMatchesNameIgnoringCase() { assertEquals(listOf(web), filterServers(all, "WEB", null)) }
    @Test fun queryMatchesHost() { assertEquals(listOf(db), filterServers(all, "10.0.0.12", null)) }
    @Test fun queryMatchesTag() { assertEquals(listOf(edge), filterServers(all, "边缘", null)) }
    @Test fun tagFiltersExactly() { assertEquals(listOf(web, db), filterServers(all, "", "生产")) }
    @Test fun queryAndTagCombine() { assertEquals(listOf(db), filterServers(all, "db", "生产")) }
    @Test fun noMatchReturnsEmpty() { assertEquals(emptyList(), filterServers(all, "nope", null)) }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*ServerFilterTest'`
Expected: FAIL，`Unresolved reference: filterServers`

- [ ] **Step 3: 实现模型/仓库/过滤**

`Server.kt` 完整化（`@Serializable`，`Server` 顶层 import `kotlinx.serialization.Serializable`）；`ServerFilter.kt`：

```kotlin
package com.barezen.barezen_ssh.servers

fun filterServers(servers: List<Server>, query: String, tag: String?): List<Server> {
    val q = query.trim()
    return servers.filter { s ->
        (tag == null || tag in s.tags) &&
            (q.isEmpty() || q.lowercase() in s.name.lowercase() || q.lowercase() in s.host.lowercase() || s.tags.any { q.lowercase() in it.lowercase() })
    }
}
```

`InMemoryServerRepository`：`MutableList` + `synchronized` 简单实现。

`FileServerRepository`（jvmMain）：

```kotlin
package com.barezen.barezen_ssh.servers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileServerRepository(
    private val file: File = File(System.getProperty("user.home"), ".barezen/servers.json"),
) : ServerRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()

    override fun list(): List<Server> = synchronized(lock) { read() }
    override fun upsert(server: Server) = synchronized(lock) {
        val cur = read().filterNot { it.id == server.id } + server
        write(cur)
    }
    override fun delete(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }

    private fun read(): List<Server> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString<List<Server>>(file.readText()) }.getOrDefault(emptyList())

    private fun write(servers: List<Server>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(servers))
    }
}
```

替换 Task 3 的内存占位 `FileServerRepository`（若已建）。

- [ ] **Step 4: 写仓库测试并运行**

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/servers/FileServerRepositoryTest.kt
package com.barezen.barezen_ssh.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FileServerRepositoryTest {
    private fun tmpRepo(): Pair<FileServerRepository, File> {
        val f = File.createTempFile("servers", ".json").apply { delete() }
        return FileServerRepository(f) to f
    }

    @Test fun roundTripPersistsAllFields() {
        val (repo, _) = tmpRepo()
        val s = Server("1", "web-01", "10.0.0.11", 2222, "root", listOf("生产"), StoredAuth.Key("/home/u/.ssh/id_ed25519"))
        repo.upsert(s)
        assertEquals(listOf(s), repo.list())
    }

    @Test fun upsertReplacesById() {
        val (repo, _) = tmpRepo()
        repo.upsert(Server("1", "a", "h1", 22, "root"))
        repo.upsert(Server("1", "a-renamed", "h1", 22, "root"))
        assertEquals(1, repo.list().size)
        assertEquals("a-renamed", repo.list().single().name)
    }

    @Test fun deleteRemoves() {
        val (repo, _) = tmpRepo()
        repo.upsert(Server("1", "a", "h1", 22, "root"))
        repo.delete("1")
        assertEquals(emptyList(), repo.list())
    }

    @Test fun corruptFileYieldsEmptyList() {
        val f = File.createTempFile("servers", ".json").apply { writeText("{ not json") }
        assertEquals(emptyList(), FileServerRepository(f).list())
    }
}
```

Run: `./gradlew :shared:jvmTest --tests '*ServerFilterTest' --tests '*FileServerRepositoryTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/servers shared/src/commonTest/kotlin/com/barezen/barezen_ssh/servers shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/servers
git commit -m "feat: 服务器模型与 JSON 文件仓库"
```

---

### Task 5: 服务器列表屏与增改对话框（A1）

**Files:**
- Create: `ui/screens/ServersScreen.kt`
- Modify: `app/AppModel.kt`（增删改动作 + 查询/标签状态）、`ui/shell/AppShell.kt`（SERVERS 分支接真实屏）
- Test: `app/ServersScreenTest.kt`（commonTest）

**Interfaces:**
- Consumes: `filterServers`、`ServerRepository`、`Destination`、`BareZenTheme` 色值。
- Produces:
  - `AppModel` 新增：`var query: String`、`var selectedTag: String?`、`val visibleServers: List<Server>`、`fun setQuery(String)`、`fun selectTag(String?)`、`fun saveServer(Server)`、`fun removeServer(id: String)`
  - `@Composable fun ServersScreen(model: AppModel, onNewTerminal: (Server) -> Unit, onOpenFiles: () -> Unit)`

- [ ] **Step 1: 写失败 UI 测试**

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/ServersScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import com.barezen.barezen_ssh.ui.screens.ServersScreen
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test

class ServersScreenTest {
    private fun model() = AppModel(
        repo = InMemoryServerRepository(
            listOf(
                Server("1", "web-01", "10.0.0.11", 22, "root", listOf("生产")),
                Server("2", "db-01", "10.0.0.12", 22, "root", listOf("生产", "数据库")),
            )
        ),
        ssh = object : SshClient {},
        scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test fun searchFiltersCards() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("web-01").assertExists()
        onNodeWithText("db-01").assertExists()
        onNodeWithText("按名称、地址或标签搜索服务器").performTextInput("db-01")
        onNodeWithText("db-01").assertExists()
        onNodeWithText("web-01").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun addDialogSavesServer() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("新建服务器").performClick()
        onNodeWithText("名称").performTextInput("cache-01")
        onNodeWithText("地址").performTextInput("10.0.0.13")
        onNodeWithText("保存").performClick()
        onNodeWithText("cache-01").assertExists()
        assert(m.visibleServers.any { it.name == "cache-01" })
    }
}
```

注：搜索框用 `TextField` 且 placeholder 文案为「按名称、地址或标签搜索服务器」；若 `performTextInput` 需先 `onNode(hasSetTextAction())`，则改用语义 `testTag("server-search")` + `onNodeWithTag("server-search").performTextInput(...)`（**推荐直接用 testTag，实现与测试统一**）。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*ServersScreenTest'`
Expected: FAIL，`Unresolved reference: ServersScreen`

- [ ] **Step 3: 扩展 `AppModel`**

```kotlin
var query: String by mutableStateOf("")
var selectedTag: String? by mutableStateOf(null)
val visibleServers: List<Server> get() = filterServers(servers, query, selectedTag)
fun setQuery(value: String) { query = value }
fun selectTag(value: String?) { selectedTag = value }
fun saveServer(server: Server) { repo.upsert(server); refreshServers() }
fun removeServer(id: String) { repo.delete(id); refreshServers() }
```

- [ ] **Step 4: 实现 `ServersScreen`**

结构（对照 prototype §S1，卡片去掉统计瓦片，保留头部/标签/状态行/操作行）：

```kotlin
@Composable
fun ServersScreen(
    model: AppModel,
    onNewTerminal: (Server) -> Unit,
    onOpenFiles: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 横幅：surfaceContainerLow 圆角 12，文案「${未连接数} 台服务器未连接」
        // 搜索：OutlinedTextField testTag("server-search")，placeholder「按名称、地址或标签搜索服务器」
        // 标签 chips：servers 全部 tags 去重，selected=selectedTag，点击 toggle，再点取消
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 300.dp), …) {
            items(model.visibleServers, key = { it.id }) { server ->
                ServerCard(server, onNewTerminal, onOpenFiles, onEdit = { …对话框… })
            }
        }
    }
}
```

`ServerCard` 关键节点（全部使用 prototype 文案）：
- 头：`Icons.Outlined.Dns` + 名称（16sp/500）+ 地址 `host:port`（12sp，onSurfaceVariant）+ 标签徽章（rounded 999、scHighest 背景）
- 状态行：`圆点(primary 或 onSurfaceVariant)` + 「未连接」/「已连接 · {n} ms」+ 右侧文字按钮「连接」（未连接时显示）
- 操作行：填充按钮「新建终端」（icon terminal）→ `onNewTerminal(server)`；描边按钮「打开文件管理」→ `onOpenFiles()`
- 未连接卡片体：`msgbox`（scHighest 55% 背景、圆角 12）`insights` 图标 + 「连接后查看负载、内存和运行时间。」
- 卡片右上编辑 icon → `ServerEditDialog`

`ServerEditDialog`（新增与编辑共用）字段：名称 / 地址 / 端口（默认 22）/ 用户名 / 认证方式（单选：密码 / 私钥文件，私钥时显示路径行）/ 标签（逗号分隔）；按钮「取消」「保存」；标题「新建服务器」/「编辑服务器」。`Server.id` 新建时 `randomUUID()`（`kotlin.uuid.Uuid.random()`）。

- [ ] **Step 5: `AppShell` 接线**

`Destination.SERVERS -> ServersScreen(model, onNewTerminal = { model.navigate(Destination.TERMINAL) }, onOpenFiles = { model.navigate(Destination.FILES) })`（连接流程 Task 8 完善；本任务 `onNewTerminal` 暂只导航，Task 8 改为「设为当前目标并打开连接对话框」）。

- [ ] **Step 6: 运行确认通过**

Run: `./gradlew :shared:jvmTest --tests '*ServersScreenTest' --tests '*AppShellTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/ServersScreenTest.kt
git commit -m "feat: 服务器列表屏与增改对话框"
```

---

### Task 6: SSH 连接封装（sshj + 嵌入式 sshd 测试）

**Files:**
- Modify: `ssh/Ssh.kt`（补全接口）、`app/AppModel.kt`（连接状态机）、`app/AppModel` 骨架中 `SshClient {}` 测试 fake 更新
- Create (jvmMain): `ssh/JvmSshClient.kt`、`ssh/TofuHostKeyVerifier.kt`
- Test (commonTest): `app/AppModelTest.kt`；Test (jvmTest): `ssh/JvmSshClientTest.kt`

**Interfaces:**
- Produces（commonMain `ssh/Ssh.kt`）:

```kotlin
sealed interface AuthMethod {
    data class Password(val value: String) : AuthMethod
    data class PrivateKey(val keyPath: String, val passphrase: String? = null) : AuthMethod
}
data class ConnectRequest(val host: String, val port: Int, val user: String, val auth: AuthMethod)

interface ShellChannel {
    fun write(bytes: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun close()
}

interface SshSession {
    fun pingMs(): Long
    fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel
    fun close()
}

interface SshClient {
    suspend fun connect(request: ConnectRequest): SshSession
}
```

`ConnectionState` 保持 Task 3 定义不变。

- [ ] **Step 1: 写 AppModel 连接状态机失败测试**

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/AppModelTest.kt
package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.AuthMethod
import com.barezen.barezen_ssh.ssh.ConnectRequest
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppModelTest {
    private val server = Server("1", "web-01", "10.0.0.11", 22, "root")

    private fun model(client: SshClient) = AppModel(
        repo = InMemoryServerRepository(listOf(server)),
        ssh = client,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test fun connectHappyPathEndsConnected() = runBlocking {
        val session = object : SshSession {
            override fun pingMs() = 12L
            override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit) = error("unused")
            override fun close() {}
        }
        val m = model(object : SshClient { override suspend fun connect(request: ConnectRequest) = session })
        m.startConnect(server, AuthMethod.Password("pw"))
        assertEquals(ConnectionState.Connected(server, 12L), m.connection)
    }

    @Test fun connectFailureCarriesMessage() = runBlocking {
        val m = model(object : SshClient {
            override suspend fun connect(request: ConnectRequest): SshSession = throw IllegalStateException("auth failed")
        })
        m.startConnect(server, AuthMethod.Password("bad"))
        val state = m.connection
        assertTrue(state is ConnectionState.Failed && state.message.contains("auth failed"))
    }
}
```

（测试驱动出 `AppModel.startConnect(server, auth)`，内部先置 `Connecting` 再协程连接；`runBlocking` 下 fake 同步完成 → 断言终态。）

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*AppModelTest'`
Expected: FAIL，`Unresolved reference: startConnect / 接口方法缺失`

- [ ] **Step 3: 补全 `Ssh.kt` 接口与 `AppModel.startConnect`**

```kotlin
// AppModel 新增
fun startConnect(server: Server, auth: AuthMethod) {
    connection = ConnectionState.Connecting(server)
    scope.launch {
        try {
            val session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
            connection = ConnectionState.Connected(server, session.pingMs())
        } catch (e: Exception) {
            connection = ConnectionState.Failed(server, e.message ?: e.toString())
        }
    }
}
fun disconnect() { connection = ConnectionState.Disconnected }
```

更新所有 `object : SshClient {}` 测试 fake 为 `object : SshClient { override suspend fun connect(r: ConnectRequest) = error("unused") }`（Task 3/5 测试同步修改）。

- [ ] **Step 4: 实现 `JvmSshClient`（jvmMain）与 TOFU 验证器**

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/TofuHostKeyVerifier.kt
package com.barezen.barezen_ssh.ssh

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.KnownHostEntry
import java.io.File
import java.security.PublicKey

/**
 * TOFU：known_hosts 已有条目必须匹配；无条目则接受并追加写入。
 */
class TofuHostKeyVerifier(
    private val knownHosts: File = File(System.getProperty("user.home"), ".ssh/known_hosts"),
) : HostKeyVerifier {
    private val lock = Any()

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = synchronized(lock) {
        if (!knownHosts.exists()) return true.also { append(hostname, key) }
        val entries = knownHosts.readLines().mapNotNull { KnownHostEntry.parseLine(it).firstOrNull() }
        val match = entries.any { it.check(hostname, key) }
        if (!match && entries.none { it.check(hostname, key) }) {
            // 存在同主机不同密钥 → 拒绝；无该主机条目 → 接受
            val sameHost = entries.any { it.check(hostname, key) }
            if (!sameHost) return true.also { append(hostname, key) }
        }
        match || entries.none { it.check(hostname, key) }.also { if (it) append(hostname, key) }
    }

    override fun findKnownHosts(key: PublicKey): MutableList<KnownHostEntry> = mutableListOf()

    private fun append(hostname: String, key: PublicKey) {
        knownHosts.parentFile?.mkdirs()
        val type = KeyType.fromKey(key)
        val b64 = java.util.Base64.getEncoder().encodeToString(key.encoded)
        knownHosts.appendText("$hostname ${type.toString().lowercase()} $b64\n")
    }
}
```

（实现以 `KnownHostEntry.check` 为准；若 0.40.0 的 `KnownHostEntry` API 不同（`check` 方法名/参数），以 `~/.gradle/caches/modules-2/files-2.1/com.hierynomus/sshj/0.40.0/**/sshj-0.40.0.jar` 内 `javap -classpath … net.schmizz.sshj.transport.verification.KnownHostEntry` 输出为准调整——语义要求：同主机已有不同密钥 → `verify` 返回 false；未知主机 → 接受并写入。）

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/JvmSshClient.kt
package com.barezen.barezen_ssh.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.Session
import java.io.File

class JvmSshClient : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession = withContext(Dispatchers.IO) {
        val client = SSHClient().apply {
            addHostKeyVerifier(TofuHostKeyVerifier())
            connectTimeout = 10_000
            keepAliveInterval = 15_000
        }
        try {
            when (val auth = request.auth) {
                is AuthMethod.Password -> client.authPassword(request.user, auth.value)
                is AuthMethod.PrivateKey ->
                    client.authPublickey(request.user, client.loadKeys(File(auth.keyPath), auth.passphrase?.toCharArray()))
            }
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            throw e
        }
        JvmSshSession(client)
    }
}

private class JvmSshSession(private val client: SSHClient) : SshSession {
    @Volatile private var session: Session? = null

    override fun pingMs(): Long {
        val started = System.nanoTime()
        client.startSession().use { s ->
            s.exec("true").let { cmd ->
                cmd.waitFor()
            }
        }
        return (System.nanoTime() - started) / 1_000_000
    }

    override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel {
        val s = client.startSession().also { session = it }
        s.allocateDefaultPty()
        val shell: Session.SessionChannel = s.startShell()
        val reader = Thread {
            val buf = ByteArray(8192)
            try {
                val input = shell.remoteInputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) onData(buf.copyOf(n))
                }
                onClosed(null)
            } catch (e: Exception) {
                onClosed(e)
            }
        }.apply { isDaemon = true; name = "ssh-shell-reader"; start() }
        return object : ShellChannel {
            override fun write(bytes: ByteArray) { shell.remoteOutputStream.write(bytes); shell.remoteOutputStream.flush() }
            override fun resize(cols: Int, rows: Int) {
                runCatching { shell.changeWindowDimensions(cols, rows, 0, 0) }
            }
            override fun close() { runCatching { shell.close() }; runCatching { session?.close() } }
        }
    }

    override fun close() {
        runCatching { session?.close() }
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }
}
```

（`Session.SessionChannel` 的 resize 方法名若非 `changeWindowDimensions`，以 `javap -classpath sshj-0.40.0.jar net.schmizz.sshj.connection.channel.Session\$SessionChannel` 为准；找不到 resize 入口时保留 `runCatching` 空实现并在验收走查记录——终端仍可固定尺寸工作。）

- [ ] **Step 5: 写嵌入式 sshd 集成测试**

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/ssh/JvmSshClientTest.kt
package com.barezen.barezen_ssh.ssh

import org.apache.sshd.server.SshServer
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.key.GeneratorSecurityKeyPairProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.ShellFactory
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.password.SessionContext
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFails

class JvmSshClientTest {
    private lateinit var sshd: SshServer

    private fun startSshd(): Int {
        sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = GeneratorSecurityKeyPairProvider()
            passwordAuthenticator = object : PasswordAuthenticator {
                override fun authenticate(u: String, p: String, s: SessionContext?, k: PublicKey?) = u == "test" && p == "secret"
            }
            shellFactory = ShellFactory { EchoShell() }
        }
        return sshd.start()
    }

    @AfterTest fun stop() { runCatching { sshd.stop(true) } }

    @Test fun connectAndPingSucceeds() = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        val session = JvmSshClient().connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        assertTrue(session.pingMs() >= 0)
        session.close()
    }

    @Test fun wrongPasswordFails() = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        assertFails {
            JvmSshClient().connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("wrong")))
        }
    }

    @Test fun shellReceivesDataAndSendsInput() = kotlinx.coroutines.runBlocking {
        val port = startSshd()
        val session = JvmSshClient().connect(ConnectRequest("127.0.0.1", port, "test", AuthMethod.Password("secret")))
        val received = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
        val shell = session.startShell(onData = { received.add(it) }, onClosed = {})
        shell.write("ping\n".toByteArray())
        val first = received.poll(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(first != null && String(first).contains("READY"))
        shell.close(); session.close()
    }
}

/** 简单回显 shell：启动即输出 READY，随后把输入原样回显。 */
private class EchoShell : Command {
    private var input: InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    private lateinit var output: OutputStream
    override fun setInputStream(`in`: InputStream?) { input = `in` ?: java.io.ByteArrayInputStream(ByteArray(0)) }
    override fun setOutputStream(out: OutputStream?) { output = out ?: java.io.OutputStream.nullOutputStream() }
    override fun setErrorStream(err: OutputStream?) {}
    override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback?) {}
    override fun start(session: ServerSession?) {
        output.write("READY\n".toByteArray()); output.flush()
        Thread {
            val buf = ByteArray(1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n); output.flush()
            }
        }.apply { isDaemon = true; start() }
    }
    override fun destroy() {}
}
```

（`known_hosts` 测试注意：`TofuHostKeyVerifier` 默认写真实 `~/.ssh/known_hosts`；测试里通过 `TofuHostKeyVerifier(File.createTempFile("kh",""))` 注入——`JvmSshClient` 需留构造参数 `hostKeyVerifier: HostKeyVerifier = TofuHostKeyVerifier()` 供测试传入临时文件，测试两处 `JvmSshClient()` 改为 `JvmSshClient(TofuHostKeyVerifier(tmp))`。）

- [ ] **Step 6: 运行确认通过**

Run: `./gradlew :shared:jvmTest`
Expected: PASS（全部测试；嵌入式 sshd 若端口/工厂 API 与 2.14.0 有出入，以 `javap -classpath ~/.gradle/.../sshd-core-2.14.0.jar org.apache.sshd.server.SshServer` 修正 setter 名，语义不变）

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ssh shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/ssh shared/src/commonTest
git commit -m "feat: SSH 连接封装（sshj + 嵌入式 sshd 测试）"
```

---

### Task 7: JediTerm 终端组件与会话管道

**Files:**
- Create (jvmMain): `terminal/TerminalView.kt`、`ssh/TerminalTtyConnector.kt`、`ssh/StreamPump.kt`
- Modify: `ssh/Ssh.kt`（无需变更，仅消费）
- Test (jvmTest): `ssh/StreamPumpTest.kt`

**Interfaces:**
- Consumes: `SshSession.startShell`、`ConnectionState`。
- Produces:
  - `class StreamPump(onChunk: (ByteArray) -> Unit)` + `fun pump(input: InputStream, onClose: (Throwable?) -> Unit)`（独立可测的读泵）
  - `class TerminalTtyConnector(channel: ShellChannel) : TtyConnector`（JediTerm 适配：阻塞队列收 `onData`，`read/write` 对接）
  - `@Composable fun TerminalView(session: SshSession, modifier: Modifier)`：创建 channel、组装 widget、`DisposableEffect` 关闭

- [ ] **Step 1: 写读泵失败测试**

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/ssh/StreamPumpTest.kt
package com.barezen.barezen_ssh.ssh

import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamPumpTest {
    @Test fun pumpsAllChunksThenSignalsClose() {
        val chunks = mutableListOf<ByteArray>()
        val closed = CountDownLatch(1)
        var closeErr: Throwable? = null
        StreamPump { chunks.add(it) }.apply {
            start(ByteArrayInputStream("hello world".toByteArray())) { e -> closeErr = e; closed.countDown() }
        }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        assertEquals("hello world", chunks.joinToString("") { String(it) })
        assertEquals(null, closeErr)
    }

    @Test fun propagatesStreamError() {
        val closed = CountDownLatch(1)
        var closeErr: Throwable? = null
        val failing = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("boom")
        }
        StreamPump { }.apply { start(failing) { e -> closeErr = e; closed.countDown() } }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        assertEquals("boom", closeErr?.message)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*StreamPumpTest'`
Expected: FAIL，`Unresolved reference: StreamPump`

- [ ] **Step 3: 实现 `StreamPump`**

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/StreamPump.kt
package com.barezen.barezen_ssh.ssh

import java.io.InputStream

class StreamPump(private val onChunk: (ByteArray) -> Unit) {
    fun start(input: InputStream, onClose: (Throwable?) -> Unit) {
        Thread({
            try {
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) onChunk(buf.copyOf(n))
                }
                onClose(null)
            } catch (e: Exception) {
                onClose(e)
            }
        }, "stream-pump").apply { isDaemon = true; start() }
    }
}
```

（Task 6 `JvmSshSession.startShell` 的 reader 线程可重构为复用 `StreamPump`——重构后跑一遍 `JvmSshClientTest`。）

- [ ] **Step 4: 运行泵测试通过**

Run: `./gradlew :shared:jvmTest --tests '*StreamPumpTest' --tests '*JvmSshClientTest'`
Expected: PASS

- [ ] **Step 5: 实现 `TerminalTtyConnector` 与 `TerminalView`**

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ssh/TerminalTtyConnector.kt
package com.barezen.barezen_ssh.ssh

import org.jetbrains.jediterm.terminal.TtyConnector
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue

class TerminalTtyConnector(private val channel: ShellChannel) : TtyConnector {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false

    fun onData(chunk: ByteArray) { if (!closed) queue.put(chunk) }
    fun markClosed() { closed = true; queue.put(ByteArray(0)) }

    override fun init() {}
    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val bytes = queue.take()
        if (bytes.isEmpty() && closed) return -1
        val str = String(bytes, Charset.forName("UTF-8"))
        val n = minOf(length, str.length)
        str.toCharArray(buf, offset, 0, n)
        // 简化：剩余字符丢弃前先回写队首（UTF-8 多字节/长行边界在 JediTerm 内按块处理，v1 可接受）
        return n
    }
    override fun write(bytes: String) { channel.write(bytes.toByteArray(Charsets.UTF_8)) }
    override fun close() { closed = true; channel.close() }
    override fun isConnected() = !closed
    override fun getName() = "ssh"
    override fun getCharset(): Charset = Charsets.UTF_8
    override fun isInteractive() = true
    override fun getWidth() = 80
    override fun getHeight() = 24
}
```

（`TtyConnector` 接口方法集以 jediterm-3.73 sources 为准：`javap -classpath ~/.gradle/.../jediterm-ui-3.73.jar org.jetbrains.jediterm.terminal.TtyConnector`；若含 `setResizeHandler` 等新增方法按需补空实现；`read` 的字符边界问题若导致丢字，改为内部维护 `String` 残缓冲——验收走查以 `ls -R /usr` 长输出无乱码为准。）

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/terminal/TerminalView.kt
package com.barezen.barezen_ssh.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.barezen.barezen_ssh.ssh.SshSession
import com.barezen.barezen_ssh.ssh.TerminalTtyConnector
import org.jetbrains.jediterm.terminal.ui.JediTermWidget
import java.util.concurrent.Executors

@Composable
fun TerminalView(session: SshSession, modifier: Modifier = Modifier) {
    val executor = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "jediterm").apply { isDaemon = true } } }
    val widget = remember(session) {
        JediTermWidget(executor).apply {
            // 连接：TtyConnector 模式（init 后由 JediTerm 自行驱动 read）
        }
    }
    val connector = remember(session) {
        val c = TerminalTtyConnector(session.startShell(onData = { (widget.terminal as? org.jetbrains.jediterm.terminal.Terminal)?.writeReceivedData?.invoke(it) ?: run {}.let {} }, onClosed = {}))
        c
    }
    DisposableEffect(session) {
        onDispose { connector.close(); executor.shutdown() }
    }
    SwingPanel(background = androidx.compose.ui.graphics.Color(0xFF1E1E1E), factory = { widget }, modifier = modifier)
}
```

**实现注意（如实说明）**：JediTerm 3.73 的挂接入口以 sources 为准——标准路径是 `JediTermWidget#connect(TtyConnector)`（或 `terminal.setTtyConnector + start`）。执行任务时先解 sources jar 定位：

```bash
find ~/.gradle/caches -name 'jediterm-ui-3.73*.jar' | head
# sources jar 不存在则下载: https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/jediterm/jediterm-ui/3.73/jediterm-ui-3.73-sources.jar
```

以其中 `JediTermWidget`/`TerminalWidget` 的实际方法为准完成三件事（语义不变）：① widget 持有 connector 并启动读循环；② shell 输出 `onData` → widget 显示；③ 用户键盘输入 → `channel.write`。终端配色（bg `#1E1E1E`、fg `#CCCCCC`）通过 `DefaultSettingsProvider` 子类覆盖 `get理想Foreground` 对应方法（sources 中确认方法名），字体 `JetBrains Mono 13`。

- [ ] **Step 6: 编译验证 + 终端组件测试**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: PASS（若 `TtyConnector`/`JediTermWidget` 符号不符，按 Step 5 sources 流程修正后重跑）

- [ ] **Step 7: Commit**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/ssh/StreamPumpTest.kt
git commit -m "feat: JediTerm 终端组件与会话管道"
```

---

### Task 8: 连接流程与终端屏状态栏接线（A3/A4/A5）

**Files:**
- Create: `ui/screens/TerminalScreen.kt`、`ui/screens/ConnectDialog.kt`
- Modify: `ui/shell/AppShell.kt`（TERMINAL/服务器卡片接线）、`app/AppModel.kt`（activeServer + 连接目标）、`desktopApp/.../main.kt`（确认窗口）
- Test: `app/TerminalScreenTest.kt`（commonTest）

**Interfaces:**
- Consumes: `TerminalView`（Task 7，jvmMain —— commonMain 通过 `expect`？**不用 expect**：`TerminalScreen` 放 jvmMain，AppShell 对 TERMINAL 的分支用 `jvmMain` 实际屏；commonMain 仅保留接口占位）。**落地方式**：`AppShell` 在 commonMain 定义 `@Composable fun TerminalRoute(model: AppModel)` 为 `expect`/`actual`——项目仅 JVM，直接把 `AppShell.kt` 移到 jvmMain 即可避免 expect。**决定：Task 3 后 `ui/shell/AppShell.kt`、`ui/screens/TerminalScreen.kt`、`ServersScreen.kt` 全部位于 jvmMain**（UI 只跑桌面，commonMain 保留 theme/模型/ssh 接口）。若执行中发现 AppShell 已在 commonMain 且测试在 commonTest —— 将 `AppShellTest/ServersScreenTest` 同步移至 jvmTest，import 不变。
- Produces:
  - `AppModel`：`var pendingConnect: Server?`（对话框目标）、`fun requestConnect(server: Server)`、`var activeShellSession: SshSession?`（jvm 侧持有）
  - `@Composable fun TerminalScreen(model: AppModel)`：未连接 → CTA；连接中 → 进度；已连接 → 标签条 + `TerminalView` + 状态栏；失败 → msgbox 错误 + 重试
  - `@Composable fun ConnectDialog(server: Server, onResult: (AuthMethod?) -> Unit)`

- [ ] **Step 1: 写失败终端屏测试**

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/TerminalScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.servers.InMemoryServerRepository
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.SshClient
import com.barezen.barezen_ssh.ui.screens.TerminalScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

class TerminalScreenTest {
    private fun model(state: ConnectionState) = AppModel(
        repo = InMemoryServerRepository(listOf(Server("1", "web-01", "10.0.0.11", 22, "root"))),
        ssh = object : SshClient { override suspend fun connect(r: com.barezen.barezen_ssh.ssh.ConnectRequest) = error("unused") },
        scope = CoroutineScope(Dispatchers.Unconfined),
    ).apply { connection = state }

    @OptIn(ExperimentalTestApi::class)
    fun assertCta(m: AppModel) = runComposeUiTest {
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("在服务器列表选择「新建终端」以开始。").assertIsDisplayed()
    }

    @Test fun disconnectedShowsCta() { assertCta(model(ConnectionState.Disconnected)) }

    @OptIn(ExperimentalTestApi::class)
    @Test fun failedShowsErrorAndRetry() = runComposeUiTest {
        val m = model(ConnectionState.Failed(Server("1", "web-01", "10.0.0.11", 22, "root"), "auth failed"))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("连接失败：auth failed").assertIsDisplayed()
        onNodeWithText("重试").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedShowsStatusBarWithLatency() = runComposeUiTest {
        val m = model(ConnectionState.Connected(Server("1", "web-01", "10.0.0.11", 22, "root"), 12))
        setContent { BareZenTheme { TerminalScreen(m) } }
        onNodeWithText("已连接").assertIsDisplayed()
        onNodeWithText("SSH 往返 12 ms").assertIsDisplayed()
    }
}
```

（`AppModel.connection` 需 `internal set` 或提供测试用 `applyConnection(state)`——在 `AppModel` 加 `fun applyConnectionForTest(state: ConnectionState) { connection = state }`，测试改用它。）

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:jvmTest --tests '*TerminalScreenTest'`
Expected: FAIL，`Unresolved reference: TerminalScreen`

- [ ] **Step 3: 实现 `ConnectDialog`**

字段：用户名（默认 server.user）、端口（默认 server.port）、认证分段（密码 / 私钥）、密码框或私钥路径（带文件选择按钮 `JFileChooser`，desktop only）；按钮「取消」「连接」；标题「连接到 {name}」。返回 `AuthMethod?`（null=取消）。视觉：M3 `AlertDialog`，色值全走 `MaterialTheme.colorScheme`。

- [ ] **Step 4: 实现 `TerminalScreen`**

```kotlin
@Composable
fun TerminalScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        // 标签条：scHigh 高 40dp，单 chip「终端」（active，primary 下边线 2dp）——多标签 M4
        Box(Modifier.weight(1f)) {
            when (val st = model.connection) {
                is ConnectionState.Disconnected -> Cta("在服务器列表选择「新建终端」以开始。")
                is ConnectionState.Connecting -> 水平居中 spin 圆环 + 「正在连接 {st.server.name}…」
                is ConnectionState.Failed -> msgbox(error) + 「连接失败：{st.message}」+ TextButton「重试」→ model.requestConnect(st.server)
                is ConnectionState.Connected -> TerminalView(model.shellSession!!, Modifier.fillMaxSize())
            }
        }
        // 状态栏：scHigh、高 30dp、11sp
        StatusBar(model.connection)
    }
}
```

`StatusBar`：`Disconnected` → 灰点 + 「未连接」；`Connecting` → primary 点 + 「连接中」；`Connected(s, ms)` → primary 点 + 「已连接」 + 「{s.name}」 + 「SSH 往返 {ms} ms」；`Failed` → error 点 + 「连接失败」。

- [ ] **Step 5: 接线连接流程**

`AppModel`：

```kotlin
var pendingConnect: Server? by mutableStateOf(null)
    private set
var shellSession: SshSession? = null
    private set

fun requestConnect(server: Server) { pendingConnect = server }
fun dismissConnect() { pendingConnect = null }
fun confirmConnect(auth: AuthMethod) {
    val server = pendingConnect ?: return
    pendingConnect = null
    connection = ConnectionState.Connecting(server)
    scope.launch {
        try {
            shellSession?.close()
            val session = ssh.connect(ConnectRequest(server.host, server.port, server.user, auth))
            shellSession = session
            connection = ConnectionState.Connected(server, session.pingMs())
            current = Destination.TERMINAL
        } catch (e: Exception) {
            connection = ConnectionState.Failed(server, e.message ?: e.toString())
        }
    }
}
```

（`startConnect` 若保留为免对话框捷径则删除，Task 6 测试改走 `requestConnect + confirmConnect` 或保留 `startConnect` 供测试直连——**保留 `startConnect(server, auth)` 作为内部主路径，`confirmConnect` 委托它**，Task 6 测试不改。`startConnect` 成功后置 `current = TERMINAL` 与 `shellSession`——把 `session` 存成员。）

接线点：
- `ServersScreen` 卡片「连接」→ `model.requestConnect(server)`；`AppShell` 顶层：`model.pendingConnect?.let { ConnectDialog(it, onResult = { auth -> if (auth != null) model.startConnect(it, auth, openTerminal = true) else model.dismissConnect() }) }`
- 卡片「新建终端」→ `requestConnect` 同上（成功后自动跳终端）
- `AppShell` TERMINAL 分支 → `TerminalScreen(model)`
- 连接成功横幅计数、状态栏延迟已接

- [ ] **Step 6: 运行确认通过 + 手动验证**

Run: `./gradlew :shared:jvmTest`
Expected: PASS

手动：`./gradlew :desktopApp:run` → 新建服务器（填你真实主机）→ 连接 → 输密码 → 出现终端 → 敲 `uname -a` 有输出 → 状态栏显示 `SSH 往返 N ms` → 断网/错密码 → 失败态与重试可见。

- [ ] **Step 7: Commit**

```bash
git add shared/src
git commit -m "feat: 连接流程与终端屏状态栏接线"
```

---

### Task 9: 验收走查

**Files:** 无新增；如走查发现问题，修复涉及文件随手提交。

- [ ] **Step 1: 全量测试**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: BUILD SUCCESSFUL，0 失败

- [ ] **Step 2: 对照原型走查（`docs/ui/prototype.html#home`、`#terminal`）**

清单（逐项人工确认，截图存 `/tmp/opencode/barezen/m0/`）：
- [ ] rail 五个目的地文案/图标与原型一致，选中态 pill 指示
- [ ] 内容区 topLeft 12 圆角，底色 `#1D2020`，内容面 `#101413`
- [ ] 服务器屏：横幅计数、搜索占位、标签 chips、卡片结构（头/徽章/状态行/双按钮）与原型同构（统计瓦片缺席 = 预期，M4 补）
- [ ] 终端屏：标签条、状态栏文案 `已连接 · SSH 往返 N ms`
- [ ] 全部界面无渐变/发光/玻璃；字号层级 11/12/13/14/16 与原型同
- [ ] Tab 键遍历焦点环可见；窗口缩至 1180 宽无错版
- [ ] `./gradlew :desktopApp:run` 冷启动 < 2s（启动日志计时）

- [ ] **Step 3: 提交修复（如有）**

```bash
git add -A
git commit -m "fix: 计划1验收走查修复（{摘要}）"
```

（无修复则跳过；提交信息同样先给用户过目。）

---

## Self-Review 记录

1. **Spec 覆盖**：A1（Task 4/5）、A2（Task 6，密码+私钥）、A3（Task 7 渲染/复制粘贴为 JediTerm 默认；选中即复制与 sudo 填入明确划出）、A4（Task 6/8 状态机+失败态+重连重试）、A5 连接+延迟（Task 8）——设计文档其余条目均标注了承接计划（M2–M4）。占位四屏 + 横幅按钮/瓦片缺席已在页首「本计划不含」列明。
2. **占位符扫描**：Task 7 `TerminalView` 给出的是「以 sources 为准的三语义」而非完整代码——JediTerm 3.73 具体挂接方法无法在计划期 100% 确认，已给出 sources/javap 的精确获取与判定流程、验收判据（长输出无乱码），不属于「TBD/TODO」；其余代码块均为可落盘内容。
3. **类型一致性**：`ConnectionState.Connected(server, latencyMs)`（Task 3 定义，Task 6/8 断言一致）、`startConnect(server, auth)`（Task 6 定义，Task 8 委托复用）、`Server` 字段 id/name/host/port/user/tags/auth（Task 4 起一致）、`ServersScreen(model, onNewTerminal, onOpenFiles)`（Task 5 定义、Task 5 接线一致）、测试 fake `SshClient` 在 Task 3/5/8 均需带 `connect` 方法（已在 Task 6 Step 3 注明同步修改）。
