# P1 · 设置全域 + 持久化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把设置屏从「分类骨架 + 全禁用行 + 7 个分类占位」变成可用设置中心：6 个分类真做、设置持久化、连接可导入导出、更新可检查、关于有真实版本与开源致谢。

**Architecture:** 单一 `AppSettings`（不可变、`@Serializable`、每字段带默认）持久化到 `~/.barezen/settings.json`；`SettingsModel` 持 Compose 状态并**即时落盘**。写入走**原子替换**，文件不可读时**先隔离副本再回落默认值**（复用本项目已在 `FileServerRepository` 上验证过的加固模式）。应用级设置（主题/缩放）在 `App()` 根部生效，屏级/组件级设置按设计 §4.4 归位。

**Tech Stack:** Kotlin Multiplatform（仅 JVM 目标）· Compose Desktop · kotlinx.serialization · JediTerm 3.73 · sshj 0.40 · JDK 21（`java.net.http.HttpClient`）

**设计文档（唯一需求源）:** `docs/superpowers/specs/2026-10-01-settings-completion-design.md`

---

## Global Constraints

以下为项目级硬约束，**每个任务的要求都隐含包含本节**。

- 仓库：`/home/lin/All_projects/Javaproject/BareZen-SSH`，`master` 直跑（无 remote）。
- **Gradle 必须 `--no-daemon`**；测试必须加 **`--rerun`**（不加会报 `UP-TO-DATE`，任务根本没执行，不构成证据）。
- 跑测试前先 `rm -rf ~/.cache/fontconfig/`（否则可能挂起）。
- **禁止 `pkill -f`**（会匹配到自身 shell）。只按记录的 PID `kill <pid>`。
- 外网下载走代理：`curl -x http://127.0.0.1:7897`。
- **TDD**：每个任务先落测试跑红（编译失败也算红），再实现跑绿。
- **不造数红线**：没有真源的数据一律不渲染数字/版本/假备选。只有一个真选项的设置行**照实显示单值**。
- 词表：纯简体中文 UI，术语照 `docs/ui-redesign/index.html` 附录 A。
- **不得改动** `Color.kt` 既有 token 值；不得引入渐变 / 悬浮效果 / emoji。
- **提交信息必须先给用户过目**再提交。
- 测试证据**另存到独立路径**（`--rerun` 会覆盖 `shared/build/test-results/jvmTest/` 同名 XML）。
- 基线命令与期望：
  ```bash
  timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
  # 期望 BUILD SUCCESSFUL，18 suites / 77 tests / 0 failures（P1 开始前的基线）
  ```

---

## File Structure

**新增 · 生产代码**

| 路径 | 职责 |
|---|---|
| `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/BuildInfo.kt` | 版本常量（与 `gradle.properties` 同步，有测试守卫） |
| `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt` | 数据模型 + 3 枚举 + `sanitized()` |
| `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/SettingsRepository.kt` | 仓库接口 + `SettingsLoad` + `SettingsWriteException` |
| `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/SettingsModel.kt` | Compose 状态持有者（即时落盘） |
| `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt` | `ADDRESS_MASK` |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt` | `settings.json` 读写（原子 + 隔离） |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParser.kt` | `~/.ssh/config` 解析 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransfer.kt` | 导出/导入（JSON · CSV） |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/UpdateChecker.kt` | GitHub Releases 检查（注入式 fetch） |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt` | 4 种共享行控件 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAppearanceSection.kt` | 外观 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsTerminalSection.kt` | 终端 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsConnectionSection.kt` | 连接 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsStorageSection.kt` | 存储 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsUpdateSection.kt` | 更新 |
| `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAboutSection.kt` | 关于 |

**新增 · 测试**

| 路径 | 职责 |
|---|---|
| `shared/src/commonTest/.../settings/AppSettingsTest.kt` | 模型 + `sanitized()` |
| `shared/src/commonTest/.../app/SettingsModelTest.kt` | 状态 + 落盘 + 失败保留 |
| `shared/src/jvmTest/.../settings/FileSettingsRepositoryTest.kt` | 持久化 + 隔离 + 原子写 |
| `shared/src/jvmTest/.../settings/OpenSshConfigParserTest.kt` | OpenSSH 解析 |
| `shared/src/jvmTest/.../settings/ConnectionTransferTest.kt` | 导出脱敏 + CSV + 导入去重 |
| `shared/src/jvmTest/.../settings/UpdateCheckerTest.kt` | 选版 + 比较 + 零网络 |
| `shared/src/jvmTest/.../BuildInfoTest.kt` | 版本一致性守卫 |
| `shared/src/jvmTest/.../app/SettingsScreenTest.kt` | 设置屏 UI |

**修改**

| 路径 | 改动 |
|---|---|
| `gradle.properties` | 追加 `barezen.version=0.1.0` |
| `desktopApp/build.gradle.kts` | `packageVersion` 改读该属性 |
| `shared/src/commonMain/.../ui/theme/Theme.kt` | `BareZenTheme` 加 `darkTheme: Boolean = true` 形参 |
| `shared/src/commonMain/.../app/AppModel.kt` | 加 `settings: SettingsModel` 构造参数与属性 |
| `shared/src/jvmMain/.../app/AppModel.desktop.kt` | `real()` 里构造并 `load()` |
| `shared/src/jvmMain/.../App.kt` | 主题 + `LocalDensity` |
| `shared/src/jvmMain/.../ui/screens/SettingsScreen.kt` | 6 类路由 + 通知区 |
| `shared/src/jvmMain/.../ui/screens/ServersScreen.kt:341` | 地址掩码 |
| `shared/src/jvmMain/.../ui/screens/ConnectDialog.kt` | 只读端口掩码 |
| `shared/src/jvmMain/.../terminal/TerminalView.kt:30,70` | 设置类带参数 + `copyOnSelect` |
| `shared/src/jvmTest/.../app/PlaceholderScreensTest.kt` | 3 条断言更新 |
| `shared/src/jvmTest/.../app/AppShellTest.kt` | 1 条断言更新 |

---

## Task 1: 版本单一真相源

**Files:**
- Modify: `gradle.properties`
- Modify: `desktopApp/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/BuildInfo.kt`
- Test: `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt`

**Interfaces:**
- Consumes: 无
- Produces: `object BuildInfo { const val VERSION: String }`（`com.barezen.barezen_ssh.BuildInfo`）—— Task 12 关于页使用

- [ ] **Step 1: 写失败测试**

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt
package com.barezen.barezen_ssh

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 版本号单一真相源守卫：gradle.properties 的 barezen.version 必须与 BuildInfo.VERSION 一致。
 * 任一处被改而另一处没跟上 → 本测试变红。
 */
class BuildInfoTest {

    /** 从 user.dir 向上找 gradle.properties（Gradle 测试工作目录随 source set 而变，不能写死层级）。 */
    private fun findGradleProperties(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle.properties")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("从 ${System.getProperty("user.dir")} 向上找不到 gradle.properties")
    }

    @Test fun versionMatchesGradleProperties() {
        val props = Properties().apply {
            findGradleProperties().inputStream().use { load(it) }
        }
        val declared = props.getProperty("barezen.version")
        assertNotNull(declared, "gradle.properties 里缺少 barezen.version")
        assertTrue(declared.isNotBlank(), "barezen.version 不得为空")
        assertEquals(declared.trim(), BuildInfo.VERSION, "gradle.properties 与 BuildInfo.VERSION 不一致")
    }
}
```

- [ ] **Step 2: 跑红**

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*BuildInfoTest'
```

期望：**编译失败**（`BuildInfo` 不存在）→ 即 RED。

- [ ] **Step 3: 实现**

`gradle.properties` 末尾追加：

```properties
# 应用版本（单一真相源；BuildInfo.VERSION 必须与之同步，由 BuildInfoTest 守卫）
barezen.version=0.1.0
```

`desktopApp/build.gradle.kts` 中，把 `nativeDistributions` 里的硬编码版本改为读属性：

```kotlin
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BareZen-SSH"
            // 版本单一真相源在 gradle.properties；不要在这里硬编码
            packageVersion = providers.gradleProperty("barezen.version").get()
        }
```

创建 `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/BuildInfo.kt`：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/BuildInfo.kt
package com.barezen.barezen_ssh

/**
 * 构建期常量。
 *
 * 版本号的**单一真相源**是 `gradle.properties` 的 `barezen.version`；
 * 本常量必须与之保持一致 —— 由 `BuildInfoTest` 守卫，改一处忘另一处会变红。
 */
object BuildInfo {
    const val VERSION: String = "0.1.0"
}
```

- [ ] **Step 4: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*BuildInfoTest'
```

期望：`BUILD SUCCESSFUL`，1 个测试通过。

- [ ] **Step 5: 验证打包版本确实跟着走**

```bash
timeout -k 15 600 ./gradlew --no-daemon :desktopApp:compileKotlin
# 期望 BUILD SUCCESSFUL（providers.gradleProperty 解析成功）
```

- [ ] **Step 6: 提交（信息先给用户过目）**

```bash
git add gradle.properties desktopApp/build.gradle.kts \
  shared/src/commonMain/kotlin/com/barezen/barezen_ssh/BuildInfo.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt
git commit -m "feat: 版本号单一真相源（gradle.properties）并加一致性守卫测试"
```

---

## Task 2: `AppSettings` 数据模型 + `sanitized()`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt`
- Test: `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `enum class Theme { DARK, FOLLOW_SYSTEM }`
  - `enum class UpdateChannel { STABLE, PREVIEW }`
  - `enum class ConflictPolicy { ASK, OVERWRITE, SKIP, RENAME }`
  - `data class AppSettings(...)`（字段与默认值见下）
  - `AppSettings.Default`、`AppSettings.SCHEMA_VERSION`、`AppSettings.UI_SCALE_CHOICES` 等常量
  - `data class Sanitized(val value: AppSettings, val warnings: List<String>)`
  - `fun AppSettings.sanitized(): Sanitized`

- [ ] **Step 1: 写失败测试**

创建 `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt`：

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsTest {

    /** 与 FileSettingsRepository 必须用同一套配置，否则"能存不能读"。 */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test fun defaultsAreStable() {
        val d = AppSettings.Default
        assertEquals(AppSettings.SCHEMA_VERSION, d.schemaVersion)
        assertEquals(Theme.FOLLOW_SYSTEM, d.theme)
        assertEquals("Noto Sans SC", d.uiFont)
        assertEquals(1.0f, d.uiScale)
        assertEquals("zh-CN", d.language)
        assertEquals("JetBrains Mono", d.terminalFont)
        assertEquals("graphite", d.terminalPalette)
        assertEquals(false, d.copyOnSelect)
        assertEquals(true, d.shiftInsertPaste)
        assertEquals(false, d.sudoAutofill)
        assertNull(d.autoConnectServerId)
        assertEquals(false, d.hideAddresses)
        assertEquals(ConflictPolicy.ASK, d.conflictPolicy)
        assertNull(d.updateRepo)
        assertEquals(UpdateChannel.STABLE, d.updateChannel)
        assertEquals(false, d.autoCheckUpdates)
        assertNull(d.feedbackUrl)
    }

    @Test fun roundTripSerialization() {
        val original = AppSettings.Default.copy(
            theme = Theme.DARK,
            uiScale = 1.25f,
            updateRepo = "acme/barezen-ssh",
            feedbackUrl = "https://example.com/issues",
            autoConnectServerId = "srv-1",
        )
        val decoded = json.decodeFromString<AppSettings>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test fun unknownKeysAreIgnored() {
        val text = """{"schemaVersion":1,"theme":"DARK","futureField":"whatever"}"""
        val decoded = json.decodeFromString<AppSettings>(text)
        assertEquals(Theme.DARK, decoded.theme)
    }

    @Test fun missingKeysUseDefaults() {
        val decoded = json.decodeFromString<AppSettings>("""{"theme":"DARK"}""")
        assertEquals(Theme.DARK, decoded.theme)
        assertEquals(AppSettings.Default.uiScale, decoded.uiScale)
        assertEquals(AppSettings.Default.conflictPolicy, decoded.conflictPolicy)
    }

    @Test fun unknownEnumValueCoercesToDefault() {
        // coerceInputValues=true 时未知枚举值 → 该属性的默认值，而不是抛异常
        val decoded = json.decodeFromString<AppSettings>("""{"theme":"NEON","updateChannel":"NIGHTLY"}""")
        assertEquals(Theme.FOLLOW_SYSTEM, decoded.theme)
        assertEquals(UpdateChannel.STABLE, decoded.updateChannel)
    }

    @Test fun sanitizeClampsOutOfRangeScale() {
        val r = AppSettings.Default.copy(uiScale = 99f).sanitized()
        assertEquals(1.5f, r.value.uiScale)
        assertTrue(r.warnings.isNotEmpty(), "收敛必须留下警告")
    }

    @Test fun sanitizeClampsNegativeScale() {
        val r = AppSettings.Default.copy(uiScale = -1f).sanitized()
        assertEquals(1.0f, r.value.uiScale)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeFallsBackOnUnknownFonts() {
        val r = AppSettings.Default.copy(uiFont = "Comic Sans", terminalFont = "Papyrus").sanitized()
        assertEquals(AppSettings.UI_FONT_DEFAULT, r.value.uiFont)
        assertEquals(AppSettings.TERMINAL_FONT_DEFAULT, r.value.terminalFont)
        assertEquals(2, r.warnings.size)
    }

    @Test fun sanitizeFallsBackOnUnknownPaletteAndLanguage() {
        val r = AppSettings.Default.copy(terminalPalette = "solarized", language = "en-US").sanitized()
        assertEquals(AppSettings.TERMINAL_PALETTE_DEFAULT, r.value.terminalPalette)
        assertEquals(AppSettings.LANGUAGE_DEFAULT, r.value.language)
        assertEquals(2, r.warnings.size)
    }

    @Test fun sanitizeRejectsMalformedRepo() {
        val r = AppSettings.Default.copy(updateRepo = "not a repo").sanitized()
        assertNull(r.value.updateRepo)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeAcceptsValidRepo() {
        val r = AppSettings.Default.copy(updateRepo = "acme/barezen-ssh").sanitized()
        assertEquals("acme/barezen-ssh", r.value.updateRepo)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun sanitizeRejectsNonHttpFeedbackUrl() {
        val r = AppSettings.Default.copy(feedbackUrl = "ftp://example.com").sanitized()
        assertNull(r.value.feedbackUrl)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeBlanksAutoConnectId() {
        val r = AppSettings.Default.copy(autoConnectServerId = "   ").sanitized()
        assertNull(r.value.autoConnectServerId)
    }

    @Test fun sanitizeIsIdempotent() {
        val once = AppSettings.Default.copy(uiScale = 99f, uiFont = "nope").sanitized()
        val twice = once.value.sanitized()
        assertEquals(once.value, twice.value)
        assertTrue(twice.warnings.isEmpty(), "已合法的设置再收敛不应再产生警告")
    }
}
```

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*AppSettingsTest'
```

期望：**编译失败**（`AppSettings` 不存在）→ RED。

- [ ] **Step 3: 实现**

创建 `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt`：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.Serializable

/**
 * 主题。
 *
 * **故意不含 LIGHT** —— 浅色板尚未实现，持久化层不得持有一个不可能的值。
 * UI 上「浅色」渲染为禁用项，等浅色板落地时再加枚举分支。
 */
@Serializable
enum class Theme { DARK, FOLLOW_SYSTEM }

@Serializable
enum class UpdateChannel { STABLE, PREVIEW }

@Serializable
enum class ConflictPolicy { ASK, OVERWRITE, SKIP, RENAME }

/** 收敛结果：收敛后的设置 + 人类可读的警告（用于 UI 如实告知，不静默）。 */
data class Sanitized(val value: AppSettings, val warnings: List<String>)

/**
 * 全局设置。**每个字段都必须有默认值** —— 这既是「旧文件缺字段」的兼容手段，
 * 也是 `coerceInputValues` 能把未知枚举值收敛掉的前提。
 */
@Serializable
data class AppSettings(
    val schemaVersion: Int = SCHEMA_VERSION,
    // 外观
    val theme: Theme = Theme.FOLLOW_SYSTEM,
    val uiFont: String = UI_FONT_DEFAULT,
    val uiScale: Float = UI_SCALE_DEFAULT,
    val language: String = LANGUAGE_DEFAULT,
    // 终端
    val terminalFont: String = TERMINAL_FONT_DEFAULT,
    val terminalPalette: String = TERMINAL_PALETTE_DEFAULT,
    val copyOnSelect: Boolean = false,
    val shiftInsertPaste: Boolean = true,
    val sudoAutofill: Boolean = false,
    // 连接
    val autoConnectServerId: String? = null,
    val hideAddresses: Boolean = false,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    // 更新
    val updateRepo: String? = null,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val autoCheckUpdates: Boolean = false,
    // 关于
    val feedbackUrl: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val UI_FONT_DEFAULT = "Noto Sans SC"
        const val LANGUAGE_DEFAULT = "zh-CN"
        const val TERMINAL_FONT_DEFAULT = "JetBrains Mono"
        const val TERMINAL_PALETTE_DEFAULT = "graphite"
        const val UI_SCALE_DEFAULT = 1.0f

        /** 界面缩放合法档；越界值收敛到最近的一档 */
        val UI_SCALE_CHOICES = listOf(1.0f, 1.25f, 1.5f)

        /**
         * 当前**真实存在**的可选值。只有一个元素不是偷懒 ——
         * 本项目只随包分发一款 UI 字体/一款等宽字体/一套终端调色板/一种语言，
         * 照实渲染单值，不造假备选（不造数红线）。
         */
        val UI_FONT_CHOICES = listOf(UI_FONT_DEFAULT)
        val LANGUAGE_CHOICES = listOf(LANGUAGE_DEFAULT)
        val TERMINAL_FONT_CHOICES = listOf(TERMINAL_FONT_DEFAULT)
        val TERMINAL_PALETTE_CHOICES = listOf(TERMINAL_PALETTE_DEFAULT)

        val Default = AppSettings()

        private val REPO_RE = Regex("""^[\w.-]+/[\w.-]+$""")
        internal fun isValidRepo(v: String) = REPO_RE.matches(v)
        internal fun isValidUrl(v: String) = v.startsWith("http://") || v.startsWith("https://")
    }
}

/**
 * 把越界/未知值收敛到合法域。
 *
 * **绝不抛异常、绝不产生坏布局** —— 用户手改坏了 settings.json 也不该让应用崩掉。
 * 每次收敛都留下一条警告，由 UI 如实告知。
 */
fun AppSettings.sanitized(): Sanitized {
    val warnings = mutableListOf<String>()
    var s = this

    if (s.uiScale !in AppSettings.UI_SCALE_CHOICES) {
        // 收敛到最近的合法档（不四舍五入到"更小的"，就近即可）
        val nearest = AppSettings.UI_SCALE_CHOICES.minByOrNull { kotlin.math.abs(it - s.uiScale) }
            ?: AppSettings.UI_SCALE_DEFAULT
        warnings += "界面缩放 ${s.uiScale} 无效，已改为 ${(nearest * 100).toInt()}%"
        s = s.copy(uiScale = nearest)
    }
    if (s.uiFont !in AppSettings.UI_FONT_CHOICES) {
        warnings += "界面字体「${s.uiFont}」不可用，已改为 ${AppSettings.UI_FONT_DEFAULT}"
        s = s.copy(uiFont = AppSettings.UI_FONT_DEFAULT)
    }
    if (s.language !in AppSettings.LANGUAGE_CHOICES) {
        warnings += "显示语言「${s.language}」不可用，已改为 ${AppSettings.LANGUAGE_DEFAULT}"
        s = s.copy(language = AppSettings.LANGUAGE_DEFAULT)
    }
    if (s.terminalFont !in AppSettings.TERMINAL_FONT_CHOICES) {
        warnings += "终端字体「${s.terminalFont}」不可用，已改为 ${AppSettings.TERMINAL_FONT_DEFAULT}"
        s = s.copy(terminalFont = AppSettings.TERMINAL_FONT_DEFAULT)
    }
    if (s.terminalPalette !in AppSettings.TERMINAL_PALETTE_CHOICES) {
        warnings += "终端主题「${s.terminalPalette}」不可用，已改为 ${AppSettings.TERMINAL_PALETTE_DEFAULT}"
        s = s.copy(terminalPalette = AppSettings.TERMINAL_PALETTE_DEFAULT)
    }
    s.updateRepo?.let { repo ->
        if (!AppSettings.isValidRepo(repo)) {
            warnings += "更新源「$repo」格式无效，应为 owner/repo，已清空"
            s = s.copy(updateRepo = null)
        }
    }
    s.feedbackUrl?.let { url ->
        if (!AppSettings.isValidUrl(url)) {
            warnings += "反馈入口「$url」不是 http(s) 链接，已清空"
            s = s.copy(feedbackUrl = null)
        }
    }
    if (s.autoConnectServerId?.isBlank() == true) {
        s = s.copy(autoConnectServerId = null)
    }
    return Sanitized(s, warnings)
}
```

- [ ] **Step 4: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*AppSettingsTest'
```

期望：`BUILD SUCCESSFUL`，14 个测试通过。

> 注意 `sanitizeIsIdempotent`：第二次收敛必须**零警告**。若实现里用了「始终重写」而不是「仅在不合法时改」，这条会红。

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/AppSettings.kt \
  shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt
git commit -m "feat: 设置数据模型与非法值收敛（sanitize）"
```

---

## Task 3: `SettingsRepository` 接口 + `FileSettingsRepository`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/SettingsRepository.kt`
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt`
- Test: `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `AppSettings` / `sanitized()` / `Sanitized`
- Produces:
  - `interface SettingsRepository { fun load(): SettingsLoad; fun save(settings: AppSettings) }`
  - `sealed interface SettingsLoad { Ok(settings, warnings); Recovered(settings, quarantinePath, cause) }`
  - `class SettingsWriteException(message, cause? = null)`
  - `class FileSettingsRepository(file: File = .../settings.json)`

- [ ] **Step 1: 写失败测试**

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepositoryTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepositoryTest.kt
package com.barezen.barezen_ssh.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSettingsRepositoryTest {

    private fun tmpFile(): File = File.createTempFile("settings", ".json").apply { delete() }

    @Test fun missingFileYieldsDefaultsAndDoesNotCreate() {
        val f = tmpFile()
        val load = FileSettingsRepository(f).load()
        val ok = assertIs<SettingsLoad.Ok>(load)
        assertEquals(AppSettings.Default, ok.settings)
        assertTrue(ok.warnings.isEmpty())
        assertFalse(f.exists(), "只读加载不应创建文件")
    }

    @Test fun roundTripPersistsAllFields() {
        val f = tmpFile()
        val repo = FileSettingsRepository(f)
        val s = AppSettings.Default.copy(
            theme = Theme.DARK,
            uiScale = 1.5f,
            conflictPolicy = ConflictPolicy.RENAME,
            updateRepo = "acme/barezen-ssh",
            autoCheckUpdates = true,
            feedbackUrl = "https://example.com/f",
            autoConnectServerId = "srv-9",
        )
        repo.save(s)
        val ok = assertIs<SettingsLoad.Ok>(repo.load())
        assertEquals(s, ok.settings)
    }

    @Test fun corruptFileIsQuarantinedAndDefaultsUsed() {
        val f = tmpFile().apply { writeText("{ not json") }
        val original = f.readText()

        val load = FileSettingsRepository(f).load()

        val rec = assertIs<SettingsLoad.Recovered>(load)
        assertEquals(AppSettings.Default, rec.settings)
        val qp = assertNotNull(rec.quarantinePath, "必须报告隔离副本路径")
        val q = File(qp)
        assertTrue(q.isFile, "隔离副本必须真的落地")
        assertEquals(original, q.readText(), "隔离副本必须原样保留原文件内容")
        assertFalse(f.exists(), "原路径应已被让出（已改名）")
    }

    @Test fun saveDoesNotDestroyUnreadableFile() {
        val f = tmpFile().apply { writeText("{ not json") }
        val original = f.readText()
        val repo = FileSettingsRepository(f)

        repo.save(AppSettings.Default.copy(uiScale = 1.25f))

        // 新值写进去了……
        assertEquals(1.25f, assertIs<SettingsLoad.Ok>(repo.load()).settings.uiScale)
        // ……且原坏内容仍能从隔离副本找回
        val preserved = f.parentFile!!.listFiles().orEmpty()
            .any { it.name.startsWith("${f.name}.corrupt-") && it.readText() == original }
        assertTrue(preserved, "不可读的 settings.json 被覆盖销毁；应先隔离再写")
    }

    @Test fun writeIsAtomicAndLeavesNoTempResidue() {
        val f = tmpFile()
        FileSettingsRepository(f).save(AppSettings.Default.copy(theme = Theme.DARK))
        assertTrue(
            f.parentFile!!.listFiles().orEmpty().none { it.name.startsWith("${f.name}.tmp") },
            "写入后残留了 .tmp",
        )
    }

    @Test fun partialJsonUsesDefaults() {
        val f = tmpFile().apply { writeText("""{"theme":"DARK"}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.DARK, ok.settings.theme)
        assertEquals(AppSettings.Default.uiScale, ok.settings.uiScale)
    }

    @Test fun unknownFieldsAreIgnored() {
        val f = tmpFile().apply { writeText("""{"theme":"DARK","futureField":123}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.DARK, ok.settings.theme)
    }

    @Test fun invalidValuesAreClampedOnLoadWithWarnings() {
        val f = tmpFile().apply {
            writeText("""{"uiScale":99.0,"uiFont":"Comic Sans"}""")
        }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(1.5f, ok.settings.uiScale)
        assertEquals(AppSettings.UI_FONT_DEFAULT, ok.settings.uiFont)
        assertEquals(2, ok.warnings.size, "两处收敛应产生两条警告")
    }

    @Test fun unknownEnumValueInFileDoesNotThrow() {
        val f = tmpFile().apply { writeText("""{"theme":"NEON"}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.FOLLOW_SYSTEM, ok.settings.theme)
    }
}
```

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*FileSettingsRepositoryTest'
```

期望：**编译失败**（`SettingsRepository` / `FileSettingsRepository` 不存在）→ RED。

- [ ] **Step 3: 实现接口**

创建 `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/SettingsRepository.kt`：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/SettingsRepository.kt
package com.barezen.barezen_ssh.settings

/** 设置持久化。UI 无关。 */
interface SettingsRepository {
    /** **永不抛。** 失败时内部隔离副本并回落默认值，由返回值如实报告。 */
    fun load(): SettingsLoad

    /** 失败抛 [SettingsWriteException]。 */
    fun save(settings: AppSettings)
}

sealed interface SettingsLoad {
    /** 正常读取（含"文件不存在"与"值被收敛"两种情况，后者 warnings 非空）。 */
    data class Ok(val settings: AppSettings, val warnings: List<String>) : SettingsLoad

    /**
     * 文件不可读/解析失败 → 已把原文件隔离为副本，并回落默认值。
     * [quarantinePath] 为隔离副本的绝对路径。
     */
    data class Recovered(
        val settings: AppSettings,
        val quarantinePath: String?,
        val cause: String,
    ) : SettingsLoad
}

class SettingsWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [ ] **Step 4: 实现文件仓库**

创建 `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `~/.barezen/settings.json` 的读写。
 *
 * 两条路径刻意分开：
 * - [load] 只读，**不对磁盘产生副作用**（除了"文件坏掉时把它改名隔离"这一必要动作）。
 * - [save] 写前先确保不会覆盖掉一份**读不出来的**用户配置 —— 先隔离再写。
 *
 * 写入是**原子替换**（先写同目录 `.tmp` 再 move）：避免写到一半崩溃留下半截 JSON，
 * 那正是制造出"不可读文件"的最可能路径。
 */
class FileSettingsRepository(
    private val file: File = File(System.getProperty("user.home"), ".barezen/settings.json"),
) : SettingsRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // 前向兼容：新版本写的字段，旧版本读到不炸
        encodeDefaults = true      // 默认值也写盘；否则改回默认值时字段消失，导出/diff 莫名其妙
        coerceInputValues = true   // 未知枚举值 → 该属性默认值（而不是抛）
        isLenient = false
    }

    override fun load(): SettingsLoad {
        if (!file.exists()) return SettingsLoad.Ok(AppSettings.Default, emptyList())
        return try {
            val parsed = json.decodeFromString<AppSettings>(file.readText())
            val s = parsed.sanitized()
            SettingsLoad.Ok(s.value, s.warnings)
        } catch (cause: Exception) {
            // 读不出来 → 隔离（留证据）再回落默认值。绝不静默重置。
            val quarantined = quarantine(cause)
            SettingsLoad.Recovered(AppSettings.Default, quarantined, cause.message ?: cause.toString())
        }
    }

    override fun save(settings: AppSettings) {
        // 写之前先确认现有文件是可读的；不可读就先隔离，避免覆盖掉唯一一份用户配置。
        if (file.exists()) {
            try {
                json.decodeFromString<AppSettings>(file.readText())
            } catch (cause: Exception) {
                quarantine(cause)
            }
        }
        try {
            writeAtomically(settings)
        } catch (cause: Exception) {
            throw SettingsWriteException("设置写入失败：${cause.message}", cause)
        }
    }

    /**
     * 把不可读的原文件改名留存为 `<name>.corrupt-<epochMillis>`，返回副本绝对路径。
     * 改名失败或副本没落地时**抛异常中止** —— 宁可让这次操作失败，也不能丢掉用户的配置线索。
     */
    private fun quarantine(cause: Exception): String {
        val target = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        runCatching { file.renameTo(target) }
        if (!target.exists()) {
            throw SettingsWriteException("${file.name} 不可读，且无法留存副本；已中止以免丢失配置", cause)
        }
        return target.absolutePath
    }

    private fun writeAtomically(settings: AppSettings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(settings))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
```

- [ ] **Step 5: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*FileSettingsRepositoryTest'
```

期望：`BUILD SUCCESSFUL`，9 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/SettingsRepository.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepository.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/FileSettingsRepositoryTest.kt
git commit -m "feat: 设置文件持久化（原子写 + 不可读先隔离副本，杜绝静默重置）"
```

---

## Task 4: `SettingsModel`（状态 + 即时落盘 + 失败保留）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/SettingsModel.kt`
- Test: `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/SettingsModelTest.kt`

**Interfaces:**
- Consumes: Task 2 `AppSettings`；Task 3 `SettingsRepository` / `SettingsLoad` / `SettingsWriteException`
- Produces: `class SettingsModel(repo: SettingsRepository)` 与成员 `settings` / `loadNotice` / `saveError` / `load()` / `update{}` / `dismissLoadNotice()` / `dismissSaveError()`

- [ ] **Step 1: 写失败测试**

创建 `shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/SettingsModelTest.kt`：

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/SettingsModelTest.kt
package com.barezen.barezen_ssh.app

import com.barezen.barezen_ssh.settings.AppSettings
import com.barezen.barezen_ssh.settings.SettingsLoad
import com.barezen.barezen_ssh.settings.SettingsRepository
import com.barezen.barezen_ssh.settings.SettingsWriteException
import com.barezen.barezen_ssh.settings.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 内存假仓库：记录 save 调用次数，可设成抛异常。 */
private class FakeRepo(
    var toLoad: SettingsLoad = SettingsLoad.Ok(AppSettings.Default, emptyList()),
    var failSave: Boolean = false,
) : SettingsRepository {
    var saveCount = 0
    var lastSaved: AppSettings? = null
    override fun load(): SettingsLoad = toLoad
    override fun save(settings: AppSettings) {
        saveCount++
        if (failSave) throw SettingsWriteException("disk full")
        lastSaved = settings
    }
}

class SettingsModelTest {

    @Test fun loadPopulatesFromRepository() {
        val repo = FakeRepo(SettingsLoad.Ok(AppSettings.Default.copy(theme = Theme.DARK), emptyList()))
        val m = SettingsModel(repo)
        m.load()
        assertEquals(Theme.DARK, m.settings.theme)
        assertNull(m.loadNotice)
    }

    @Test fun updateAppliesAndPersistsImmediately() {
        val repo = FakeRepo()
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(uiScale = 1.25f) }
        assertEquals(1.25f, m.settings.uiScale, "内存态应立即变化")
        assertEquals(1, repo.saveCount, "应立刻落盘一次")
        assertEquals(1.25f, repo.lastSaved!!.uiScale)
        assertNull(m.saveError)
    }

    @Test fun saveFailureKeepsInMemoryStateAndExposesError() {
        val repo = FakeRepo(failSave = true)
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(copyOnSelect = true) }
        // 关键：不回滚内存态 —— 回滚会让用户以为白改了一次
        assertEquals(true, m.settings.copyOnSelect)
        assertNotNull(m.saveError, "写失败必须如实告知")
    }

    @Test fun recoveredLoadExposesNotice() {
        val repo = FakeRepo(
            SettingsLoad.Recovered(AppSettings.Default, "/tmp/settings.json.corrupt-1", "boom"),
        )
        val m = SettingsModel(repo)
        m.load()
        val notice = assertNotNull(m.loadNotice, "隔离恢复必须告知用户")
        assert(notice.contains("corrupt")) { "告知里应含副本文件名，实际：$notice" }
        m.dismissLoadNotice()
        assertNull(m.loadNotice)
    }

    @Test fun okLoadWithWarningsExposesNotice() {
        val repo = FakeRepo(SettingsLoad.Ok(AppSettings.Default, listOf("界面缩放 99.0 无效，已改为 150%")))
        val m = SettingsModel(repo)
        m.load()
        assertNotNull(m.loadNotice, "有收敛警告也必须告知")
    }

    @Test fun dismissSaveErrorClearsIt() {
        val repo = FakeRepo(failSave = true)
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(hideAddresses = true) }
        assertNotNull(m.saveError)
        m.dismissSaveError()
        assertNull(m.saveError)
    }
}
```

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsModelTest'
```

期望：编译失败（`SettingsModel` 不存在）→ RED。

- [ ] **Step 3: 实现**

创建 `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/SettingsModel.kt`：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/SettingsModel.kt
package com.barezen.barezen_ssh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barezen.barezen_ssh.settings.AppSettings
import com.barezen.barezen_ssh.settings.SettingsLoad
import com.barezen.barezen_ssh.settings.SettingsRepository
import com.barezen.barezen_ssh.settings.SettingsWriteException

/**
 * 设置的内存态 + 即时落盘。
 *
 * 保存策略是**即时生效**（无脏态、无"保存"按钮）：桌面工具即时反馈更顺，
 * 而脏标记 + 丢失提示是纯附加复杂度。
 *
 * 两条不可动摇的行为：
 * 1. **写失败不回滚内存态** —— 回滚会让用户以为白改了一次。
 * 2. **凡是"没做/被改/失败"都要说出来** —— 不静默（[loadNotice] / [saveError]）。
 */
class SettingsModel(private val repo: SettingsRepository) {

    var settings: AppSettings by mutableStateOf(AppSettings.Default)
        private set

    /** 加载期告知（文件被隔离 / 值被收敛）。可关闭。 */
    var loadNotice: String? by mutableStateOf(null)
        private set

    /** 最近一次写盘失败的原因。可关闭。 */
    var saveError: String? by mutableStateOf(null)
        private set

    fun load() {
        when (val r = repo.load()) {
            is SettingsLoad.Ok -> {
                settings = r.settings
                loadNotice = r.warnings.takeIf { it.isNotEmpty() }
                    ?.joinToString("；", prefix = "已修正无效设置：")
            }
            is SettingsLoad.Recovered -> {
                settings = r.settings
                val where = r.quarantinePath?.substringAfterLast('/') ?: "（隔离失败）"
                loadNotice = "设置文件无法读取，已重置为默认值；原文件已保存为 $where。"
            }
        }
    }

    /** 改一项并立即落盘。写失败时保留内存态并暴露 [saveError]。 */
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        try {
            repo.save(next)
            saveError = null
        } catch (e: SettingsWriteException) {
            saveError = "保存失败：${e.message}。改动已生效但未能写入磁盘。"
        }
    }

    fun dismissLoadNotice() { loadNotice = null }
    fun dismissSaveError() { saveError = null }
}
```

- [ ] **Step 4: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsModelTest'
```

期望：`BUILD SUCCESSFUL`，6 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/SettingsModel.kt \
  shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/SettingsModelTest.kt
git commit -m "feat: 设置内存态与即时落盘（写失败保留内存态并如实告知）"
```

---

## Task 5: 接线 —— `AppModel` / `App()` 主题与缩放

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/app/AppModel.desktop.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/App.kt`
- Modify: `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/*` （`forUiTest` 需带 settings）
- Test: 复用 `ThemeWiringTest` + 新增断言

**Interfaces:**
- Consumes: Task 4 `SettingsModel`
- Produces: `AppModel.settings: SettingsModel`；`BareZenTheme(darkTheme: Boolean = true, content)`；`AppModel.Companion.forUiTest()` 带默认设置

- [ ] **Step 1: 先看 `forUiTest` 现在长什么样**

已核实：**`AppModel.forUiTest()` 定义在 `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt:111`**（companion 内）。

```bash
sed -n '105,125p' shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt
```

**Task 5 必须让它也带上 `settings`**，否则所有 UI 测试会编译失败。调用点很多（`AppShellTest`、`ConnectFlowTest`、`ServersScreenTest`、`FocusRingTest` 等），
但因为都是 `AppModel.forUiTest()` 这个工厂，**只改工厂内部一处即可**，调用点无需变动。

- [ ] **Step 2: 写失败测试**

在 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/ThemeWiringTest.kt` 追加：

```kotlin
    /** 缩放必须作用在 density 上，且不得乘进 fontScale（否则字号被缩放两次）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test fun appAppliesUiScaleToDensityOnly() = runComposeUiTest {
        val model = AppModel.forUiTest()
        model.settings.update { it.copy(uiScale = 1.5f) }
        var density = 0f
        var fontScale = 0f
        setContent {
            App(model) {
                // App() 内部会提供 LocalDensity；此处直接读
            }
            density = LocalDensity.current.density
            fontScale = LocalDensity.current.fontScale
        }
        // 基准 density 由测试环境决定，只断言"被放大且 fontScale 未被改"
        assertEquals(1f, fontScale, "fontScale 不应被缩放影响")
    }
```

> 上面的写法依赖 `App()` 的可测形状。若 `App(model)` 不便直接测，**改为**在 `shared/src/jvmTest/.../app/UiScaleTest.kt` 里直接测 `App` 的 density 提供逻辑抽出来的纯函数
> `fun scaledDensity(base: Density, uiScale: Float): Density`（放 `ui/theme/` 或 `app/`），断言 `density * uiScale` 且 `fontScale` 不变。**优先选这条**，更稳。

**采用可测抽函数方案**——先写这条测试：

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/UiScaleTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/UiScaleTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class UiScaleTest {
    @Test fun scaleMultipliesDensityOnly() {
        val base = Density(density = 2f, fontScale = 1.3f)
        val scaled = scaledDensity(base, 1.5f)
        assertEquals(3f, scaled.density)
        assertEquals(1.3f, scaled.fontScale, "fontScale 不得被缩放影响，否则字号被缩放两次")
    }

    @Test fun scaleOneIsIdentity() {
        val base = Density(density = 1f, fontScale = 1f)
        assertEquals(1f, scaledDensity(base, 1.0f).density)
    }
}
```

- [ ] **Step 3: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*UiScaleTest'
```

期望：编译失败（`scaledDensity` 不存在）→ RED。

- [ ] **Step 4: 实现**

`shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt`：改 `BareZenTheme` 签名（既有 ~25 处 `BareZenTheme { ... }` 尾随 lambda 调用**不受影响**）：

```kotlin
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
```

在 `Theme.kt` 末尾追加抽出的纯函数：

```kotlin
/** 把界面缩放只作用在 density 上；fontScale 原样保留（否则字号被缩放两次）。 */
fun scaledDensity(base: androidx.compose.ui.unit.Density, uiScale: Float): androidx.compose.ui.unit.Density =
    androidx.compose.ui.unit.Density(base.density * uiScale, base.fontScale)
```

`shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt`：构造参数与属性加 `settings`：

```kotlin
class AppModel(
    val repo: ServerRepository,
    val ssh: SshClient,
    private val scope: CoroutineScope,
    val settings: SettingsModel,
)
```

同时把 `forUiTest()`（Step 1 里定位到的那个）改为传入一个内存版 `SettingsModel`。
**新写一个 test-only 的假仓库**（若已有内存 `ServerRepository` 的先例，照它的位置放）：

```kotlin
// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/NoopSettingsRepository.kt
package com.barezen.barezen_ssh.settings

/** 测试用：不碰磁盘。 */
class NoopSettingsRepository(
    private var current: AppSettings = AppSettings.Default,
) : SettingsRepository {
    override fun load(): SettingsLoad = SettingsLoad.Ok(current, emptyList())
    override fun save(settings: AppSettings) { current = settings }
}
```

`forUiTest()` 里：`settings = SettingsModel(NoopSettingsRepository()).also { it.load() }`。

`shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/app/AppModel.desktop.kt`：

```kotlin
fun AppModel.Companion.real(): AppModel = AppModel(
    repo = FileServerRepository(),
    ssh = JvmSshClient(),
    scope = CoroutineScope(Dispatchers.Default),
    settings = SettingsModel(FileSettingsRepository()).also { it.load() },
)
```

`shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/App.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/App.kt
package com.barezen.barezen_ssh

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.settings.Theme
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import com.barezen.barezen_ssh.ui.theme.scaledDensity

@Composable
fun App(model: AppModel) {
    val settings = model.settings.settings
    // 浅色板尚未实现，故 FOLLOW_SYSTEM 与 DARK 目前都解析为深色。
    // 保留 when 分支：浅色落地时只改这一处。
    val dark = when (settings.theme) {
        Theme.DARK -> true
        Theme.FOLLOW_SYSTEM -> isSystemInDarkTheme() || true
    }
    BareZenTheme(darkTheme = dark) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides scaledDensity(base, settings.uiScale),
        ) {
            BareZenAppContent(model)
        }
    }
}
```

- [ ] **Step 5: 跑绿 + 全量回归**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*UiScaleTest'
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

期望：`UiScaleTest` 2 通过；全量 `BUILD SUCCESSFUL`。

> 若全量出现大量「`AppModel` 构造参数不匹配」的编译错误，说明还有别的测试直接 `new AppModel(...)`。用
> `grep -rn "AppModel(" shared/src --include='*.kt'` 找齐，逐个补 `settings = ...`。

- [ ] **Step 6: 提交**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt \
  shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/theme/Theme.kt \
  shared/src/commonMain/kotlin/com/barezen/barezen_ssh/settings/NoopSettingsRepository.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/app/AppModel.desktop.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/App.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/UiScaleTest.kt
git commit -m "feat: 设置接线进 AppModel 与 App 根部（主题 + 界面缩放）"
```

---

## Task 6: 共享行控件 + 设置屏路由 + 通知区

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt`
- Test: `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: Task 4 `SettingsModel`
- Produces（其余 section 任务都依赖这 4 个）：
  - `@Composable fun SettingsSectionScaffold(title: String, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun ToggleRow(title: String, desc: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit)`
  - `@Composable fun ChoiceRow(title: String, desc: String?, options: List<String>, selectedIndex: Int, enabled: Boolean = true, onSelect: (Int) -> Unit)`
  - `@Composable fun StaticValueRow(title: String, desc: String?, value: String)`
  - `@Composable fun ActionRow(title: String, desc: String?, actions: List<Pair<String, () -> Unit>>)`
  - `@Composable fun SettingsNotice(text: String, onDismiss: () -> Unit)`

- [ ] **Step 1: 写失败测试**

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
package com.barezen.barezen_ssh.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.theme.BareZenTheme
import kotlin.test.Test

class SettingsScreenTest {

    private fun model() = AppModel.forUiTest()

    @OptIn(ExperimentalTestApi::class)
    @Test fun allEightCategoriesAreNavigable() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun loadNoticeIsShownAndDismissable() = runComposeUiTest {
        val m = model()
        m.settings.update { it.copy(uiScale = 1.5f) }   // 触发一次状态变化，确认通知区可渲染
        setContent { BareZenTheme { SettingsScreen(m) } }
        // 通知区只在有内容时出现；此处用假仓库注入告警的场景由 SettingsModelTest 覆盖，
        // UI 层只断言"有告警时能显示并能关掉"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun assistantAndCredentialsAreHonestPlaceholders() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("智能助手").performClick()
        onNodeWithText("智能助手属 M5，尚未接入。").assertIsDisplayed()
        onNodeWithText("凭据").performClick()
        onNodeWithText("凭据库属 M3，尚未接入。").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest'
```

期望：`assistantAndCredentialsAreHonestPlaceholders` 失败（现有占位文案是「「智能助手」设置页占位。」）→ RED。

- [ ] **Step 3: 实现共享行控件**

创建 `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置行控件集。形状照设计包 `.setting-row` —— **分隔线行，不是卡片**：
 * `padding: 16px 0` + 底边 `1px border-subtle`（末行无）；无底色、无圆角、无整圈描边。
 */

@Composable
private fun RowShell(
    title: String,
    desc: String?,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (desc != null) {
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun ToggleRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RowShell(title, desc) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 分段选择。`enabled=false` 用于「未实现」的选项（如浅色主题）。 */
@Composable
fun ChoiceRow(
    title: String,
    desc: String?,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    RowShell(title, desc) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                if (selected) {
                    Button(onClick = { onSelect(i) }, enabled = enabled) { Text(label, fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = { onSelect(i) }, enabled = enabled) { Text(label, fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 静态值行：**无可交互控件**。用于当前只有一个真实可选项的设置
 * （照实渲染单值，不造假备选 —— 不造数红线）。标题不进语义树，避免与值重复播报。
 */
@Composable
fun StaticValueRow(title: String, desc: String?, value: String) {
    RowShell(title, desc) {
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ActionRow(
    title: String,
    desc: String?,
    actions: List<Pair<String, () -> Unit>>,
) {
    RowShell(title, desc) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (label, onClick) ->
                OutlinedButton(onClick = onClick) { Text(label, fontSize = 12.sp) }
            }
        }
    }
}

/** 分类内容外壳：标题不进语义树（左栏选中项已播报同词）。 */
@Composable
fun SettingsSectionScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** 顶部通知（加载告警 / 保存失败）。可关闭。 */
@Composable
fun SettingsNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}
```

- [ ] **Step 4: 改设置屏路由 + 通知区**

`shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt`：
- 保留左列 8 分类导航（`SettingsCategories` 列表**不动**）与 `selected` 状态。
- 右列改为：`SettingsNotice`（`loadNotice` / `saveError` 非空时各一条）+ 一个 `when (selected)` 路由：
  - 0 外观 → `AppearanceSettingsSection(model.settings)`
  - 1 终端 → `TerminalSettingsSection(model.settings)`
  - 2 连接 → `ConnectionSettingsSection(model.settings, model.servers)`
  - 3 智能助手 → `SettingsSectionScaffold("智能助手") { Text("智能助手属 M5，尚未接入。") ; Text("（AI 会话侧栏与命令审批流在后续里程碑实现）") }`
  - 4 凭据 → `SettingsSectionScaffold("凭据") { Text("凭据库属 M3，尚未接入。") ; Text("（系统钥匙串集成在后续里程碑实现）") }`
  - 5 存储 → `StorageSettingsSection(model.settings)`
  - 6 更新 → `UpdateSettingsSection(model.settings)`
  - 7 关于 → `AboutSettingsSection()`
- **签名改为 `fun SettingsScreen(model: AppModel)`**（原为无参）。调用点 `AppShell.kt` 里 `Destination.SETTINGS -> SettingsScreen()` 需同步改成 `SettingsScreen(model)`。

> 本任务只把 6 个 section 中的 2 个占位改完；其余 4 个 section 由 Task 7–12 实现。
> **为了让本任务能独立编译**，先在 `SettingsScreen.kt` 里为尚未实现的 4 个 section 写**最小占位实现**
> （各自一个 `@Composable fun XxxSettingsSection(...) { SettingsSectionScaffold("X") { } }` 空壳），
> 后续任务再逐个替换真身。**提交信息里要说明这是过渡空壳。**

- [ ] **Step 5: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest'
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

期望：`BUILD SUCCESSFUL`。

> 这里**必然**会同时改到 `PlaceholderScreensTest` 与 `AppShellTest` 的 4 条断言（见 §14）。本任务一并改掉。

- [ ] **Step 6: 更新 4 条既有断言**

| 文件 | 行（开始前用内容定位复核） | 改成 |
|---|---|---|
| `PlaceholderScreensTest.kt:66` | `onNodeWithText("跟随系统").assertIsNotEnabled()` | `onNodeWithText("跟随系统").assertIsEnabled()`（或 `performClick()` 后断言生效） |
| `PlaceholderScreensTest.kt:69` | `onNodeWithText("简体中文").assertIsNotEnabled()` | `onNodeWithText("简体中文").assertIsDisplayed()` |
| `PlaceholderScreensTest.kt:72` | `onNodeWithText("「存储」设置页占位。")...` | **删除该断言**（改由 `SettingsScreenTest` 覆盖） |
| `AppShellTest.kt:35` | `onNodeWithText("跟随系统").assertIsNotEnabled()` | `onNodeWithText("跟随系统").assertIsDisplayed()` |

导入 `androidx.compose.ui.test.assertIsEnabled`。

- [ ] **Step 7: 提交**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/shell/AppShell.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/PlaceholderScreensTest.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/AppShellTest.kt
git commit -m "feat: 设置屏路由、共享行控件与通知区（6 分类中 4 个先落过渡空壳）"
```

---

## Task 7: 外观分类

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAppearanceSection.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt`（替换空壳）
- Test: `SettingsScreenTest.kt` 追加

**Interfaces:**
- Consumes: Task 4 `SettingsModel`；Task 6 的 4 个行控件
- Produces: `@Composable fun AppearanceSettingsSection(settings: SettingsModel)`

- [ ] **Step 1: 写失败测试**（追加进 `SettingsScreenTest.kt`）

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceLightThemeOptionIsDisabled() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("浅色（未实现）").assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceScaleChangeUpdatesModel() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { SettingsScreen(m) } }
        onNodeWithText("125%").performClick()
        assertEquals(1.25f, m.settings.settings.uiScale)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun appearanceSingleValueRowsAreStaticText() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("Noto Sans SC").assertIsDisplayed()
        onNodeWithText("随包分发，暂无可选项").assertExists()
    }
```

导入 `androidx.compose.ui.test.assertIsNotEnabled` / `assertExists` / `kotlin.test.assertEquals`。

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest'
```

期望：3 条新测试失败 → RED。

- [ ] **Step 3: 实现**

创建 `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAppearanceSection.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAppearanceSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.settings.AppSettings
import com.barezen.barezen_ssh.settings.Theme

@Composable
fun AppearanceSettingsSection(settings: SettingsModel) {
    val s = settings.settings

    SettingsSectionScaffold("外观") {
        // 主题：三选项，「浅色」禁用 —— 浅色板尚未实现，不给假选项（不造数红线）
        val themeLabels = listOf("跟随系统", "深色", "浅色（未实现）")
        val themeIndex = when (s.theme) {
            Theme.FOLLOW_SYSTEM -> 0
            Theme.DARK -> 1
        }
        ChoiceRow(
            title = "主题",
            desc = "跟随系统在浅色主题实现前等同于深色",
            options = themeLabels,
            selectedIndex = themeIndex,
            enabled = true,
            onSelect = { i ->
                // i == 2（浅色）为禁用项，正常点不到；这里也防御性忽略
                when (i) {
                    0 -> settings.update { it.copy(theme = Theme.FOLLOW_SYSTEM) }
                    1 -> settings.update { it.copy(theme = Theme.DARK) }
                }
            },
        )

        StaticValueRow(
            title = "界面字体",
            desc = "随包分发，暂无可选项",
            value = AppSettings.UI_FONT_DEFAULT,
        )

        ChoiceRow(
            title = "界面缩放",
            desc = null,
            options = listOf("100%", "125%", "150%"),
            selectedIndex = AppSettings.UI_SCALE_CHOICES.indexOf(s.uiScale).coerceAtLeast(0),
            onSelect = { i -> settings.update { it.copy(uiScale = AppSettings.UI_SCALE_CHOICES[i]) } },
        )

        StaticValueRow(
            title = "显示语言",
            desc = "当前仅提供简体中文",
            value = "简体中文",
        )
    }
}
```

> **「浅色（未实现）」怎么做到禁用**：`ChoiceRow` 目前对所有项用同一 `enabled`。需要让它支持**逐项禁用**。
> 把 `ChoiceRow` 的签名改为：
> ```kotlin
> fun ChoiceRow(
>     title: String, desc: String?, options: List<String>, selectedIndex: Int,
>     enabled: Boolean = true,
>     disabledIndices: Set<Int> = emptySet(),
>     onSelect: (Int) -> Unit,
> )
> ```
> 每个按钮用 `enabled = enabled && i !in disabledIndices`。外观分类传 `disabledIndices = setOf(2)`。
> **这个签名变更要回头改 Task 6 的实现**（同一提交内完成，勿留编译不过的中间态）。

`SettingsScreen.kt` 里把外观的空壳替换为 `AppearanceSettingsSection(model.settings)`。

- [ ] **Step 4: 跑绿 + 全量**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

期望：`BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAppearanceSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsRows.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
git commit -m "feat: 设置·外观分类（主题/字体/缩放/语言，浅色为禁用项）"
```

---

## Task 8: 终端分类（含 JediTerm 接线与 Shift+Insert 可行性判定）

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsTerminalSection.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/terminal/TerminalView.kt`
- Modify: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/TerminalScreen.kt`
- Test: `SettingsScreenTest.kt` 追加

**Interfaces:**
- Consumes: Task 4 `SettingsModel`；Task 6 行控件
- Produces: `@Composable fun TerminalSettingsSection(settings: SettingsModel)`；`TerminalView(..., settings: AppSettings)`（或等价参数）

- [ ] **Step 1: 先核实 JediTerm 的真实接口（不要跳过这一步）**

```bash
J=$(find ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.jediterm -name 'jediterm-ui-3.73.jar' | head -1)
JDK=/home/lin/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2/bin
$JDK/javap -cp "$J" com.jediterm.terminal.ui.settings.SettingsProvider | grep -iE "copy|paste|select"
# 已知结果（设计 §9.4 已核实）：
#   public boolean copyOnSelect();
#   public boolean pasteOnMiddleMouseClick();
#   public boolean emulateX11CopyPaste();
```

**判定 Shift+Insert 是否可实施**：

```bash
cd /tmp && rm -rf jt8 && mkdir jt8 && cd jt8 && unzip -q "$J"
grep -rl "emulateX11CopyPaste" .            # 只有 settings 类声明，无消费者 → 死方法
$JDK/javap -p -c -cp "$J" com.jediterm.terminal.ui.TerminalPanel | grep -iE "INSERT|getKeyStroke"
# 期望：无 INSERT 相关输出 → JediTerm 3.73 不提供 Shift+Insert
```

**分支决策**（写进报告）：
- 若上面能找到可用的粘贴动作（`ActionMap` 里有 paste 动作）→ 走 **A：自己实现**（Step 4A）。
- 否则 → 走 **B：删掉该行设置**（Step 4B），**不渲染无效开关**。

- [ ] **Step 2: 写失败测试**（追加进 `SettingsScreenTest.kt`）

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test fun terminalToggleBindsToModel() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { SettingsScreen(m) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("选中即复制").assertIsDisplayed()
        onNodeWithText("开启后，在终端里选中文本即写入剪贴板").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sudoAutofillIsLabelledAsPending() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("（待凭据库接入后生效）", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun terminalFontAndPaletteAreStaticSingleValueRows() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("终端").performClick()
        onNodeWithText("JetBrains Mono").assertIsDisplayed()
        onNodeWithText("石墨（graphite）").assertIsDisplayed()
    }
```

- [ ] **Step 3: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest'
```

期望：新测试失败 → RED。

- [ ] **Step 4A: 实现（选 A：自己实现 Shift+Insert）**

创建 `SettingsTerminalSection.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsTerminalSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.settings.AppSettings

@Composable
fun TerminalSettingsSection(settings: SettingsModel) {
    val s = settings.settings

    SettingsSectionScaffold("终端") {
        StaticValueRow("终端字体", "随包分发，暂无可选项", AppSettings.TERMINAL_FONT_DEFAULT)
        StaticValueRow("终端主题", "当前仅一套终端调色板", "石墨（graphite）")

        ToggleRow(
            title = "选中即复制",
            desc = "开启后，在终端里选中文本即写入剪贴板",
            checked = s.copyOnSelect,
            onCheckedChange = { v -> settings.update { it.copy(copyOnSelect = v) } },
        )
        ToggleRow(
            title = "Shift+Insert 粘贴",
            desc = "用 Shift+Insert 粘贴剪贴板内容",
            checked = s.shiftInsertPaste,
            onCheckedChange = { v -> settings.update { it.copy(shiftInsertPaste = v) } },
        )
        ToggleRow(
            title = "自动填入 sudo 密码",
            desc = "（待凭据库接入后生效）",
            checked = s.sudoAutofill,
            onCheckedChange = { v -> settings.update { it.copy(sudoAutofill = v) } },
        )
    }
}
```

`TerminalView.kt` 改造（**先读全文再改**）：

1. `private class BareZenTerminalSettings : DefaultSettingsProvider()`（`TerminalView.kt:70`）→ 带参数。
   **现有 4 个 override 原样保留，只新增 `copyOnSelect()`**：

   ```kotlin
   private class BareZenTerminalSettings(
       private val copyOnSelect: Boolean,
       private val shiftInsertPaste: Boolean,
   ) : DefaultSettingsProvider() {

       // —— 以下 4 个 override 照 TerminalView.kt 现状抄，行为不得改动 ——
       override fun getDefaultStyle(): TextStyle =
           TextStyle(TerminalPalette.rgb(TerminalPalette.Foreground), TerminalPalette.rgb(TerminalPalette.Background))
       override fun getTerminalColorPalette(): ColorPalette =
           BareZenColorPalette(super.getTerminalColorPalette())
       override fun getTerminalFont(): Font = Font(TERMINAL_FONT_FAMILY, Font.PLAIN, TERMINAL_FONT_SIZE)
       override fun getTerminalFontSize(): Float = TERMINAL_FONT_SIZE.toFloat()

       // —— 新增：把设置接进来（javap 已确认 3.73 可覆写）——
       override fun copyOnSelect(): Boolean = copyOnSelect
   }
   ```

   > `getDefaultStyle` / `getTerminalColorPalette` 的**确切写法以 `TerminalView.kt` 当前内容为准**
   > （上面是按该文件既有实现复述的）。本步**只新增 `copyOnSelect()` 并给类加两个构造参数**，不改其余 override 的行为。
2. `JediTermWidget(BareZenTerminalSettings())`（约 `:30`）→ 接收来自 `TerminalView` 参数的设置。
3. `TerminalView` 增加形参（保持默认值以免破坏既有调用点）：
   ```kotlin
   @Composable
   fun TerminalView(
       session: SshSession,
       settings: AppSettings = AppSettings.Default,
       // ...既有参数
   )
   ```
4. 在拿到 `JediTermWidget` 后为 Shift+Insert 注册 Swing 键绑定（**仅当 A 分支成立**）：
   ```kotlin
   widget.terminalPanel.inputMap.put(
       javax.swing.KeyStroke.getKeyStroke("shift INSERT"),
       "barezen-paste",
   )
   widget.terminalPanel.actionMap.put(
       "barezen-paste",
       object : javax.swing.AbstractAction() {
           override fun actionPerformed(e: java.awt.event.ActionEvent?) {
               widget.terminalPanel.pasteFromClipboard()
           }
       },
   )
   ```
   > `pasteFromClipboard()` 的确切方法名/可见性**以 `javap` 结果为准**；不可用时改调 `TerminalPanel` 里真实的粘贴入口。

`ui/screens/TerminalScreen.kt`：把 `model.settings.settings` 传给 `TerminalView`。

- [ ] **Step 4B: 实现（选 B：删掉 Shift+Insert 行）**

与 4A 相同，但**不写** `ToggleRow("Shift+Insert 粘贴", ...)`，并：
- 在 `AppSettings` 里保留 `shiftInsertPaste` 字段（不删数据字段，避免 schema 变动），
  但**在 KDoc 里注明"JediTerm 3.73 无此能力，UI 不渲染，字段留待其支持或自研键绑定后启用"**；
- 在实施报告里写明判定依据与 `javap` 证据；
- `SettingsScreenTest` 里那条 Shift+Insert 断言改为 `assertDoesNotExist()`。

- [ ] **Step 5: 跑绿 + 全量**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

期望：`BUILD SUCCESSFUL`。

- [ ] **Step 6: 真机验证 `copyOnSelect` 真的生效**

```bash
# 启动应用（记录 PID，收场只按 PID kill，禁 pkill -f）
nohup timeout 900 ./gradlew --no-daemon :desktopApp:run > /tmp/bz-run.log 2>&1 &
# 等窗口出现后：设置 → 终端 → 开「选中即复制」→ 连一台服务器 → 在终端里拖选文本 → 检查剪贴板
```
把观察结果写进报告（生效 / 未生效 + 证据）。

- [ ] **Step 7: 提交**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsTerminalSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/terminal/TerminalView.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/TerminalScreen.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
git commit -m "feat: 设置·终端分类（选中即复制接入 JediTerm；Shift+Insert 按实测定去留）"
```

---

## Task 9: 连接分类 + 隐藏地址 + 启动自动连接

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsConnectionSection.kt`
- Create: `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt`
- Modify: `ServersScreen.kt:341`、`ConnectDialog.kt`、`AppModel.kt`（启动自动连接）
- Test: `SettingsScreenTest.kt` 追加；`ServersScreenTest.kt` 追加

**Interfaces:**
- Consumes: Task 4；Task 6 行控件；`AppModel.servers`
- Produces: `@Composable fun ConnectionSettingsSection(settings: SettingsModel, servers: List<Server>)`；`const val ADDRESS_MASK`

- [ ] **Step 1: 写失败测试**

`shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt` 先建（否则测试编译不过）：

```kotlin
// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt
package com.barezen.barezen_ssh.ui

/**
 * 隐藏地址时使用的**固定**掩码。
 * 固定串、不随真实长度变化 —— 否则长度本身就是信息。
 */
const val ADDRESS_MASK = "•••.•••.•••.•••"
```

`SettingsScreenTest.kt` 追加：

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test fun connectionConflictPolicyIsLabelledPending() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("连接").performClick()
        onNodeWithText("（待文件传输接入后生效）", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectionHideAddressesBindsToModel() = runComposeUiTest {
        val m = model()
        setContent { BareZenTheme { SettingsScreen(m) } }
        onNodeWithText("连接").performClick()
        onNodeWithText("隐藏服务器地址").assertIsDisplayed()
    }
```

`ServersScreenTest.kt` 追加（掩码真的生效）：

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test fun hideAddressesMasksHostAndPort() = runComposeUiTest {
        val m = model()
        m.settings.update { it.copy(hideAddresses = true) }
        setContent { BareZenTheme { ServersScreen(m, onNewTerminal = {}, onOpenFiles = {}) } }
        onNodeWithText("10.0.0.11:22", substring = true).assertDoesNotExist()
        onNodeWithText(ADDRESS_MASK).assertIsDisplayed()
    }
```

（`ADDRESS_MASK` 需 import `com.barezen.barezen_ssh.ui.ADDRESS_MASK`。）

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest' --tests '*ServersScreenTest'
```

期望：新测试失败 → RED。

- [ ] **Step 3: 实现分类**

创建 `SettingsConnectionSection.kt`：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsConnectionSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.settings.ConflictPolicy

@Composable
fun ConnectionSettingsSection(settings: SettingsModel, servers: List<Server>) {
    val s = settings.settings

    SettingsSectionScaffold("连接") {
        // 启动时连接：选一台，不是连全部（启动连 N 台会一次打出 N 条 SSH 连接）
        val labels = listOf("不自动连接") + servers.map { it.name.ifBlank { it.host } }
        val selectedIndex = servers.indexOfFirst { it.id == s.autoConnectServerId }
            .let { if (it < 0) 0 else it + 1 }
        val target = servers.firstOrNull { it.id == s.autoConnectServerId }
        val desc = when {
            s.autoConnectServerId == null -> null
            target == null -> "该服务器已不存在"
            else -> null
        }
        ChoiceRow(
            title = "启动时连接",
            desc = desc,
            options = labels,
            selectedIndex = selectedIndex,
            onSelect = { i ->
                settings.update { it.copy(autoConnectServerId = if (i == 0) null else servers[i - 1].id) }
            },
        )

        ToggleRow(
            title = "隐藏服务器地址",
            desc = "直播/录屏时把服务器列表与连接对话框里的地址与端口显示为掩码（编辑对话框不受影响）",
            checked = s.hideAddresses,
            onCheckedChange = { v -> settings.update { it.copy(hideAddresses = v) } },
        )

        val policyLabels = listOf("询问", "覆盖", "跳过", "重命名")
        val policies = listOf(
            ConflictPolicy.ASK, ConflictPolicy.OVERWRITE, ConflictPolicy.SKIP, ConflictPolicy.RENAME,
        )
        ChoiceRow(
            title = "文件名冲突策略",
            desc = "（待文件传输接入后生效）",
            options = policyLabels,
            selectedIndex = policies.indexOf(s.conflictPolicy).coerceAtLeast(0),
            onSelect = { i -> settings.update { it.copy(conflictPolicy = policies[i]) } },
        )
    }
}
```

- [ ] **Step 4: 接掩码**

`ServersScreen.kt:341` 附近：

```kotlin
// 改前
Text("${server.host}:${server.port}", style = BareZenMonoBody, color = ...)

// 改后（hideAddresses 来自 model.settings.settings）
Text(
    if (model.settings.settings.hideAddresses) ADDRESS_MASK else "${server.host}:${server.port}",
    style = BareZenMonoBody,
    color = ...,
)
```

`ConnectDialog.kt` 的**只读端口**字段同理：`if (hideAddresses) ADDRESS_MASK else server.port.toString()`。
（`hideAddresses` 需作为参数传进 `ConnectDialog`，或从 `AppModel` 读——**按该文件现有取值方式选一种，保持一致**。）

> **不要**改 `ServerEditDialog` 的可编辑地址/端口字段 —— 遮了就没法编辑（设计 §9.2 明确的局限）。

- [ ] **Step 5: 启动自动连接**

在 `AppModel` 里加（`load()` 之后调用）：

```kotlin
    /** 启动时连接：只对「私钥认证」且 id 仍存在的服务器生效。
     *  密码认证的服务器不自动连接 —— 我们没有也不该有持久化密码。 */
    fun autoConnectIfConfigured() {
        val id = settings.settings.autoConnectServerId ?: return
        val server = servers.firstOrNull { it.id == id } ?: return   // 不存在：不连接、不弹窗、不打扰
        if (server.auth !is StoredAuth.Key) return
        startConnect(server, AuthMethod.PrivateKey((server.auth as StoredAuth.Key).keyPath))
    }
```

在 `AppModel.real()` 构造后调用一次（`AppModel.desktop.kt`）：
```kotlin
    settings = SettingsModel(FileSettingsRepository()).also { it.load() },
).also { it.autoConnectIfConfigured() }
```

- [ ] **Step 6: 跑绿 + 全量**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

- [ ] **Step 7: 提交**

```bash
git add shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt \
  shared/src/commonMain/kotlin/com/barezen/barezen_ssh/app/AppModel.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/app/AppModel.desktop.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsConnectionSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/ServersScreen.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/ConnectDialog.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/ServersScreenTest.kt
git commit -m "feat: 设置·连接分类 + 隐藏地址掩码 + 启动自动连接（仅私钥认证）"
```

---

## Task 10: 导入导出（JSON / CSV / OpenSSH）

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParser.kt`
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransfer.kt`
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsStorageSection.kt`
- Test: `OpenSshConfigParserTest.kt`、`ConnectionTransferTest.kt`

**Interfaces:**
- Produces:
  - `data class ImportResult(val added: List<Server>, val skipped: List<SkipReason>)`
  - `sealed interface SkipReason { NoUser; WildcardHost; Duplicate }`（带计数的封装由 UI 层做）
  - `object ConnectionTransfer { fun exportJson(servers, includeKeyPath): String; fun exportCsv(servers): String; fun importJson(text): List<Server>; fun importOpenSsh(text): ImportResult }`
  - `@Composable fun StorageSettingsSection(settings: SettingsModel, servers: List<Server>, onImport: (List<Server>) -> Unit)`

- [ ] **Step 1: 写失败测试 —— OpenSSH 解析**

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParserTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParserTest.kt
package com.barezen.barezen_ssh.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSshConfigParserTest {

    private val fixture = """
        # 这是注释
        Host web-01
            HostName 10.0.0.11
            User root
            Port 2222
            IdentityFile ~/.ssh/id_ed25519

        Host db-01 db-alias
            HostName 10.0.0.12
            User admin

        Host noportal
            HostName 10.0.0.13

        Host wild-*
            HostName 10.0.0.99
            User root

        Include ~/.ssh/extra_config

        Host bastion
            HostName 10.0.0.14
            User ops
            Port 2200
    """.trimIndent()

    @Test fun parsesBasicEntriesAndAliases() {
        val r = OpenSshConfigParser.parse(fixture)
        val names = r.added.map { it.name }
        // web-01 + db-01 + db-alias + bastion（noportal 缺 User 被跳过；wild-* 通配被跳过）
        assertEquals(listOf("web-01", "db-01", "db-alias", "bastion"), names)
    }

    @Test fun parsesFieldsPortAndAuth() {
        val r = OpenSshConfigParser.parse(fixture)
        val web = r.added.first { it.name == "web-01" }
        assertEquals("10.0.0.11", web.host)
        assertEquals(2222, web.port)
        assertEquals("root", web.user)
        assertTrue(web.auth is StoredAuth.Key)
        assertEquals("~/.ssh/id_ed25519", (web.auth as StoredAuth.Key).keyPath)

        val db = r.added.first { it.name == "db-01" }
        assertEquals("10.0.0.12", db.host)
        assertEquals(22, db.port, "缺 Port 时默认 22")
        assertEquals(StoredAuth.Password, db.auth, "缺 IdentityFile 时默认密码认证")
    }

    @Test fun skippedReasonsAreReported() {
        val r = OpenSshConfigParser.parse(fixture)
        assertEquals(1, r.skipped.count { it is SkipReason.NoUser })
        assertEquals(1, r.skipped.count { it is SkipReason.WildcardHost })
        assertTrue(r.includeSkipped, "遇到 Include 必须如实告知未跟随")
    }

    @Test fun hostNameFallsBackToFirstAlias() {
        val text = "Host myalias\n    User root\n"
        val r = OpenSshConfigParser.parse(text)
        assertEquals("myalias", r.added.single().host, "无 HostName 时 host = 第一个别名")
    }
}
```

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransferTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransferTest.kt
package com.barezen.barezen_ssh.settings

import com.barezen.barezen_ssh.servers.StoredAuth
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionTransferTest {

    private fun sample() = listOf(
        com.barezen.barezen_ssh.servers.Server(
            "id-1", "web-01", "10.0.0.11", 22, "root",
            listOf("生产"), StoredAuth.Key("/home/u/.ssh/id_ed25519"),
        ),
        com.barezen.barezen_ssh.servers.Server(
            "id-2", "db,01", "10.0.0.12", 2222, "admin",
            emptyList(), StoredAuth.Password,
        ),
    )

    @Test fun jsonExportOmitsKeyPathByDefault() {
        val text = ConnectionTransfer.exportJson(sample(), includeKeyPath = false)
        assertFalse(text.contains("/home/u/.ssh/id_ed25519"), "默认导出不得含私钥路径：\n$text")
        assertTrue(text.contains("\"type\": \"key\""))
    }

    @Test fun jsonExportIncludesKeyPathWhenRequested() {
        val text = ConnectionTransfer.exportJson(sample(), includeKeyPath = true)
        assertTrue(text.contains("/home/u/.ssh/id_ed25519"))
    }

    @Test fun csvStartsWithUtf8Bom() {
        val bytes = ConnectionTransfer.exportCsv(sample()).toByteArray(Charsets.UTF_8)
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
    }

    @Test fun csvHasExpectedHeaderAndEscapes() {
        val csv = ConnectionTransfer.exportCsv(sample())
        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals("名称,地址,端口,用户名,标签,认证方式", lines[0])
        assertTrue(lines[1].contains("web-01"))
        // 名称里含逗号 → 必须被引号包裹
        assertTrue(lines.any { it.contains("\"db,01\"") }, "含逗号的字段需被引号包裹：\n$csv")
    }

    @Test fun jsonRoundTripRegeneratesIds() {
        val exported = ConnectionTransfer.exportJson(sample(), includeKeyPath = true)
        val imported = ConnectionTransfer.importJson(exported)
        assertEquals(2, imported.size)
        assertEquals(setOf("web-01", "db,01"), imported.map { it.name }.toSet())
        assertTrue(imported.none { it.id in setOf("id-1", "id-2") }, "导入必须重新生成 id")
    }

    @Test fun importDedupesByHostPortUser() {
        val existing = listOf(sample()[0])
        val incoming = ConnectionTransfer.importJson(
            ConnectionTransfer.exportJson(sample(), includeKeyPath = false),
        )
        val r = ConnectionTransfer.merge(existing, incoming)
        assertEquals(1, r.added.size)                       // 只剩 db,01
        assertEquals(1, r.skipped.count { it is SkipReason.Duplicate })
    }
}
```

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*OpenSshConfigParserTest' --tests '*ConnectionTransferTest'
```

期望：编译失败 → RED。

- [ ] **Step 3: 实现 OpenSSH 解析**

创建 `OpenSshConfigParser.kt`（完整实现）：

```kotlin
// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParser.kt
package com.barezen.barezen_ssh.settings

import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.StoredAuth

sealed interface SkipReason {
    data object NoUser : SkipReason
    data object WildcardHost : SkipReason
    data object Duplicate : SkipReason
}

data class ImportResult(
    val added: List<Server>,
    val skipped: List<SkipReason>,
    /** 遇到 Include 指令时为 true —— 必须如实告知用户我们没跟随它 */
    val includeSkipped: Boolean = false,
)

/**
 * `~/.ssh/config` 解析器。
 *
 * 规则（逐条有测试）：
 * - 每个 `Host` 块的每个别名各生成一台服务器；`HostName` 缺省时用第一个别名
 * - 缺 `User` → 跳过（计入 NoUser）；`Port` 缺省 22；`IdentityFile` 缺省=密码认证
 * - `Host` 值含 `*` `?` `!` → 跳过（WildcardHost）
 * - `Include` → 跳过并置 includeSkipped
 * - `#` 注释/空行/未知关键字 → 忽略
 */
object OpenSshConfigParser {

    private val WILDCARD = Regex("""[*?!]""")

    fun parse(text: String): ImportResult {
        val added = mutableListOf<Server>()
        val skipped = mutableListOf<SkipReason>()
        var includeSkipped = false

        var aliases: List<String> = emptyList()
        var hostName: String? = null
        var user: String? = null
        var port: Int = 22
        var identity: String? = null

        fun flush() {
            if (aliases.isEmpty()) return
            if (aliases.any { WILDCARD.containsMatchIn(it) }) {
                skipped += SkipReason.WildcardHost
            } else {
                val u = user
                if (u.isNullOrBlank()) {
                    skipped += SkipReason.NoUser
                } else {
                    val host = hostName?.takeIf { it.isNotBlank() } ?: aliases.first()
                    val auth = identity?.takeIf { it.isNotBlank() }
                        ?.let { StoredAuth.Key(it) } ?: StoredAuth.Password
                    aliases.forEach { alias ->
                        added += Server(
                            id = newId(),
                            name = alias,
                            host = host,
                            port = port,
                            user = u,
                            tags = emptyList(),
                            auth = auth,
                        )
                    }
                }
            }
            aliases = emptyList(); hostName = null; user = null; port = 22; identity = null
        }

        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val key = line.substringBefore(' ').trim()
            val value = line.substringAfter(' ', "").trim()
            when (key.lowercase()) {
                "host" -> { flush(); aliases = value.split(Regex("\\s+")).filter { it.isNotBlank() } }
                "hostname" -> hostName = value
                "user" -> user = value
                "port" -> port = value.toIntOrNull() ?: 22
                "identityfile" -> identity = value
                "include" -> includeSkipped = true
                else -> Unit   // 未知关键字忽略
            }
        }
        flush()
        return ImportResult(added, skipped, includeSkipped)
    }

    // 与 ServersScreen.kt:578 的既有惯例一致（kotlin.uuid.Uuid + OptIn）
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newId(): String = kotlin.uuid.Uuid.random().toString()
}
```

> **已核实**：仓库生成 `Server.id` 的既有惯例是 `kotlin.uuid.Uuid.random().toString()` + `@OptIn(ExperimentalUuidApi::class)`（见 `ui/screens/ServersScreen.kt:70-71,578`）。上面用的就是它，保持一致。

- [ ] **Step 4: 实现导出/导入**

创建 `ConnectionTransfer.kt`：包含 `exportJson(servers, includeKeyPath)`（手写 JSON 或用一个**不含 id** 的中间 `@Serializable` DTO）、
`exportCsv(servers)`（BOM + 转义）、`importJson(text)`（解析并重新生成 id）、`merge(existing, incoming)`（按 `(host.lowercase(), port, user)` 去重）。

CSV 转义规则：

```kotlin
private fun csvCell(v: String): String =
    if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + v.replace("\"", "\"\"") + "\""
    } else v
```

导出 JSON 的 DTO：

```kotlin
@Serializable private data class ExportAuth(val type: String, val keyPath: String? = null)
@Serializable private data class ExportServer(
    val name: String, val host: String, val port: Int, val user: String,
    val tags: List<String>, val auth: ExportAuth,
)
@Serializable private data class ExportFile(
    val schemaVersion: Int = 1, val exportedAt: String, val servers: List<ExportServer>,
)
```

（`exportedAt` 用 `java.time.Instant.now().toString()`。）

- [ ] **Step 5: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*OpenSshConfigParserTest' --tests '*ConnectionTransferTest'
```

期望：全绿（解析 4 + 传输 6）。

- [ ] **Step 6: 实现存储分类 UI**

创建 `SettingsStorageSection.kt`，含：`导出 JSON…` / `导出 CSV…` / `导入 JSON…` / `导入 OpenSSH 配置…` 四个按钮 + 数据目录路径 + `打开目录`。
导出走 §8.4 的两步流程（`JFileChooser` → 确认对话框含默认不勾的「包含私钥路径」）。
数据目录用 `File(System.getProperty("user.home"), ".barezen")`，`打开目录` 用 `Desktop.getDesktop().open(dir)`，失败降级为复制路径到剪贴板并提示。

- [ ] **Step 7: 跑绿 + 全量**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

- [ ] **Step 8: 提交**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParser.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransfer.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsStorageSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/OpenSshConfigParserTest.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/ConnectionTransferTest.kt
git commit -m "feat: 设置·存储分类 + 连接导入导出（默认脱敏 + OpenSSH config）"
```

---

## Task 11: 更新检查

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/UpdateChecker.kt`
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsUpdateSection.kt`
- Test: `UpdateCheckerTest.kt`

**Interfaces:**
- Produces: `class UpdateChecker(getJson: suspend (String) -> String)`；`sealed interface UpdateResult`；`@Composable fun UpdateSettingsSection(settings: SettingsModel, checker: UpdateChecker)`

- [ ] **Step 1: 写失败测试**

创建 `shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/UpdateCheckerTest.kt`：

```kotlin
// shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/UpdateCheckerTest.kt
package com.barezen.barezen_ssh.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun releases(vararg items: String) = items.joinToString(",", "[", "]")

class UpdateCheckerTest {

    private val stableJson = releases(
        """{"tag_name":"v0.3.0","prerelease":true,"draft":false,"html_url":"u3"}""",
        """{"tag_name":"v0.2.0","prerelease":false,"draft":false,"html_url":"u2"}""",
        """{"tag_name":"v0.1.5","prerelease":false,"draft":true,"html_url":"u15"}""",
    )

    @Test fun stablePicksLatestNonPrereleaseNonDraft() = runBlocking {
        val c = UpdateChecker { stableJson }
        val r = c.check("acme/repo", UpdateChannel.STABLE, "0.1.0")
        val ok = assertIs<UpdateResult.NewerAvailable>(r)
        assertEquals("0.2.0", ok.latest)
        assertEquals("u2", ok.url)
    }

    @Test fun previewMayPickPrerelease() = runBlocking {
        val c = UpdateChecker { stableJson }
        val r = c.check("acme/repo", UpdateChannel.PREVIEW, "0.1.0")
        assertEquals("0.3.0", assertIs<UpdateResult.NewerAvailable>(r).latest)
    }

    @Test fun notConfiguredMakesNoRequest() = runBlocking {
        var calls = 0
        val c = UpdateChecker { calls++; "[]" }
        val r = c.check("", UpdateChannel.STABLE, "0.1.0")
        assertIs<UpdateResult.NotConfigured>(r)
        assertEquals(0, calls, "未配置更新源时不得发起任何请求")
    }

    @Test fun sameVersionIsUpToDate() = runBlocking {
        val c = UpdateChecker { releases("""{"tag_name":"v0.1.0","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun olderVersionIsUpToDate() = runBlocking {
        val c = UpdateChecker { releases("""{"tag_name":"v0.0.9","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun vPrefixIsStripped() = runBlocking {
        val c = UpdateChecker { releases("""{"tag_name":"0.2.0","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertEquals("0.2.0", assertIs<UpdateResult.NewerAvailable>(c.check("a/b", UpdateChannel.STABLE, "0.1.0")).latest)
    }

    @Test fun nonSemverIsUncomparableNotLiedAbout() = runBlocking {
        val c = UpdateChecker { releases("""{"tag_name":"release-2026","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.Uncomparable>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun emptyReleaseListIsUpToDate() = runBlocking {
        val c = UpdateChecker { "[]" }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun httpErrorMapsToFriendlyMessage() = runBlocking {
        val c = UpdateChecker { throw java.io.IOException("404 Not Found") }
        val f = assertIs<UpdateResult.Failed>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
        assertEquals(true, f.message.isNotBlank())
    }
}
```

> **已核实**：本项目**没有** `kotlinx-coroutines-test` 依赖（`gradle/libs.versions.toml` 里只有 `kotlinx-coroutines` 与 `kotlinx-coroutinesSwing`），
> 所以用 `kotlinx.coroutines.runBlocking` 而不是 `runTest` —— **不为此新增依赖**。

- [ ] **Step 2: 跑红**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*UpdateCheckerTest'
```

期望：编译失败 → RED。

- [ ] **Step 3: 实现**

创建 `UpdateChecker.kt`：注入 `getJson`、按频道选版、semver 数字段比较、错误映射。
生产用的 `getJson` 实现在 `jvmMain` 里用 `java.net.http.HttpClient`（`User-Agent: BareZen-SSH`、`Accept: application/vnd.github+json`、超时 10s/20s），**不入 commonMain**。

- [ ] **Step 4: 跑绿**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*UpdateCheckerTest'
```

期望：9 个测试通过。

- [ ] **Step 5: 实现更新分类 UI**

创建 `SettingsUpdateSection.kt`：更新源输入（校验 `owner/repo`）、频道 2 选项、自动检查开关、`立即检查`（`updateRepo` 空时禁用 + desc「请先填写更新源」）、结果行（§11.4 文案）。

- [ ] **Step 6: 跑绿 + 全量 + 提交**

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/settings/UpdateChecker.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsUpdateSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/UpdateCheckerTest.kt
git commit -m "feat: 设置·更新分类（可配置更新源，未配置零请求）"
```

---

## Task 12: 关于分类

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAboutSection.kt`
- Modify: `SettingsScreen.kt`
- Test: `SettingsScreenTest.kt` 追加

**Interfaces:**
- Consumes: Task 1 `BuildInfo.VERSION`；Task 4 `SettingsModel`
- Produces: `@Composable fun AboutSettingsSection(settings: SettingsModel)`

- [ ] **Step 1: 写失败测试**

```kotlin
    @OptIn(ExperimentalTestApi::class)
    @Test fun aboutShowsVersionAndLicences() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("关于").performClick()
        onNodeWithText(BuildInfo.VERSION, substring = true).assertExists()
        listOf("sshj", "JediTerm", "JetBrains Mono", "Noto Sans SC").forEach {
            onNodeWithText(it, substring = true).assertExists()
        }
        onNodeWithText("Apache-2.0", substring = true).assertExists()
        onNodeWithText("OFL-1.1", substring = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun aboutHidesFeedbackWhenUrlIsBlank() = runComposeUiTest {
        setContent { BareZenTheme { SettingsScreen(model()) } }
        onNodeWithText("关于").performClick()
        onNodeWithText("反馈").assertDoesNotExist()
    }
```

- [ ] **Step 2: 跑红 → Step 3: 实现 → Step 4: 跑绿**

实现要点：产品名 + `BuildInfo.VERSION`；5 条致谢（组件 · 许可 · 链接，**只列真实依赖**）；`feedbackUrl` 非空才渲染反馈行。

```bash
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*SettingsScreenTest'
```

- [ ] **Step 5: 提交**

```bash
git add shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsAboutSection.kt \
  shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsScreen.kt \
  shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
git commit -m "feat: 设置·关于分类（真实版本 + 开源组件致谢）"
```

---

## Task 13: 收尾 —— 全量回归 + 真机走查（含三档缩放）+ 证据固化

**Files:** 无源码预期改动（发现问题按最小修复）

- [ ] **Step 1: 全量回归**

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
rm -rf ~/.cache/fontconfig/
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

期望：`BUILD SUCCESSFUL`。记录 suites/tests 数（基线 18 suites / 77 tests，P1 后应显著增加）。

- [ ] **Step 2: 解析并固化测试证据**

```bash
D=.superpowers/sdd/2026-10-01-settings-completion/evidence/task13
mkdir -p $D
cp shared/build/test-results/jvmTest/*.xml $D/
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=s=n=0
for p in glob.glob('shared/build/test-results/jvmTest/*.xml'):
    r=ET.parse(p).getroot(); n+=1
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print(f"suites={n} tests={t} failures={f} errors={e} skipped={s}")
PY
```

- [ ] **Step 3: 真机走查（`DISPLAY=:1`）**

```bash
nohup timeout 1800 ./gradlew --no-daemon :desktopApp:run > /tmp/bz-p1-run.log 2>&1 &
# 记录 java PID；收场只按 PID kill（禁 pkill -f）
```

逐项核验（截图存 `evidence/task13/screens/`）：

- [ ] 设置 8 分类可导航；6 类是真页（无占位文案），2 类是「属 M5/M3，尚未接入」诚实占位
- [ ] 改「界面缩放」到 125% / 150% → **逐屏目检无破版**（仪表盘/服务器/终端/文件/端口转发/设置）
- [ ] 改任一开关 → 关闭应用 → 重开 → **设置被保留**
- [ ] 手工把 `~/.barezen/settings.json` 改成 `{ not json` → 重开应用 → 出现顶部横幅、设置回落默认、目录里有 `.corrupt-*` 副本
- [ ] 手工把 `uiScale` 改成 `99` → 重开 → 收敛为 150% 且横幅告知
- [ ] 开「隐藏服务器地址」→ 服务器卡地址变掩码；连接对话框端口变掩码；**编辑对话框仍是真值**
- [ ] 导出 JSON（默认不勾私钥路径）→ `grep` 导出文件**不含** keyPath；再勾选导出一次 → 含
- [ ] 导出 CSV → 用 `xxd` 确认前 3 字节是 `EF BB BF`
- [ ] 导入一个真实 `~/.ssh/config` → 服务器出现在列表；跳过项有计数说明
- [ ] 更新源留空 → 「立即检查」禁用；填一个公开 repo（如 `microsoft/vscode`）→ 检查有真实结果（这一步会走网络）
- [ ] 关于页显示 `0.1.0` 与 4+ 个组件的许可
- [ ] 全程**无渐变 / 无悬浮效果 / 无 emoji**

- [ ] **Step 4: 写实施报告**

写入 `.superpowers/sdd/2026-10-01-settings-completion/report.md`：
每个任务的 RED/GREEN 证据、Task 8 的 Shift+Insert 判定结论与依据、真机走查逐项结果（**不通过就写不通过，不粉饰**）、
三条硬约束审计（渐变/悬浮/emoji 计数）、以及**所有未做到/跳过的项**。

- [ ] **Step 5: 最终提交（信息先给用户过目）**

```bash
git add -A -- shared/src desktopApp gradle.properties docs
git commit -m "chore: P1 收尾——全量回归与设置域真机走查"
```

- [ ] **Step 6: 请用户 review 后再进入下一个子项目（P2 主机指标）**

---

## Self-Review（写完计划后自查）

**1. Spec 覆盖**

| 设计章节 | 对应任务 |
|---|---|
| §4.1 文件清单 | Task 1–12 的 Files 块逐一覆盖 |
| §4.2 精确签名 | Task 2（AppSettings/Sanitized）、Task 3（Repository/SettingsLoad）、Task 4（SettingsModel）、Task 11（UpdateChecker） |
| §4.4 生效点四分类 | Task 5（应用级）、Task 9（屏级）、Task 8（组件级）、Task 9/8（延迟生效标注） |
| §5 数据模型 | Task 2 |
| §6 持久化 | Task 3 |
| §7 错误处理 | Task 3（隔离/原子）+ Task 4（失败保留 + notice） |
| §8 UI 规格 | Task 6（行控件）+ Task 7/8/9/10/11/12（各分类） |
| §9 接线 | Task 5/8/9 |
| §10 导入导出 | Task 10 |
| §11 更新检查 | Task 11 |
| §12 关于 | Task 12 |
| §13 测试规格 | 每个任务的 Step 1 |
| §14 既有断言 | Task 6 Step 6 |
| §15 风险 | R1→Task 13 Step 3；R2→Task 5；R3→Task 2/3；R7→Task 8 Step 1 |
| §17 验收清单 | Task 13 |

**2. 占位扫描**：本计划**无** TBD/TODO/"稍后实现"/"类似 Task N"。所有代码步骤都给了可直接粘贴的实现或明确到方法名的改造指令。

**3. 类型一致性**：`AppSettings` / `Sanitized` / `SettingsLoad` / `SettingsWriteException` / `SettingsModel` / `SkipReason` / `ImportResult` / `UpdateResult` / `ADDRESS_MASK` / `scaledDensity` / `BuildInfo.VERSION` 在各任务中的名称与签名一致。

**4. 写计划时已核实掉的事实**（不是占位符，实施者可直接依赖）：

| 事实 | 结论 | 影响 |
|---|---|---|
| `AppModel.forUiTest()` 位置 | `AppModel.kt:111`（companion 内），**只此一处** | Task 5 只改工厂内部，所有调用点无需变动 |
| `kotlinx-coroutines-test` 依赖 | **不存在**（`libs.versions.toml` 只有 `kotlinx-coroutines` + `kotlinx-coroutinesSwing`） | Task 11 用 `runBlocking`，**不新增依赖** |
| `Server.id` 生成惯例 | `kotlin.uuid.Uuid.random().toString()` + `@OptIn(ExperimentalUuidApi::class)`（`ServersScreen.kt:70-71,578`） | Task 10 的解析器照此写 |
| JediTerm 3.73 的复制粘贴 API | `copyOnSelect()` ✅ 可覆写且被 `TerminalPanel` 消费；`emulateX11CopyPaste()` ❌ 死方法；**无 INSERT 键处理** | Task 8 的 Shift+Insert 走 A/B 分支判定 |
| `BareZenTheme` 签名 | 单参数 + 约 25 处尾随 lambda 调用 | 加带默认值的形参**不会**破坏任何调用点 |

**5. 仍留给实施者当场判定的一处**（Task 8 Step 1 已给出判定命令与两个分支，不是占位符）：
Shift+Insert 能否自己实现（取决于 `TerminalPanel` 的 `ActionMap` 里粘贴动作是否可达）。**不可达就删行**，不渲染无效开关。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-10-01-settings-completion.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 每个任务派一个新子代理，任务间逐个 review，迭代快

**2. Inline Execution** — 在本会话内用 executing-plans 批量执行，带检查点 review

**Which approach?**
