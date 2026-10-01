# P1 设计 · 设置全域 + 持久化（交接版）

> 状态：**待用户 review**
> 目标读者：**执行本设计的下一个 AI**（不共享本设计的对话上下文，本文自包含）
> 上游需求：`docs/superpowers/specs/2026-09-25-ui-redesign-requirements.md` 域 7「设置」
> 设计权威：`docs/ui-redesign/index.html` + `styles.css`（配色/间距/圆角/词表一律以它为准）
> 定位：「占位符全部完成」拆出的 **6 个子项目中的第 1 个（P1）**

---

## 0. 执行须知（先读这一节）

### 0.1 你要交付什么

把设置屏从「分类骨架 + 全禁用行 + 7 个分类占位」变成**可用的设置中心**：6 个分类做成真页、
设置能持久化、连接能导入导出、更新能检查、关于页有真实版本与开源致谢。

### 0.2 仓库与硬性工作方式

| 项 | 规定 |
|---|---|
| 仓库 | `/home/lin/All_projects/Javaproject/BareZen-SSH`，`master` 直跑（无 remote） |
| 架构 | Kotlin Multiplatform，**仅 JVM 目标**；功能层在 `commonMain`，UI 与平台实现在 `jvmMain` |
| 测试 | `commonTest` + `jvmTest`（Compose UI 测试用 `runComposeUiTest`） |
| **TDD** | **每个任务先落测试跑红（编译失败也算红），再实现跑绿**，RED/GREEN 证据写进报告 |
| **不造数红线** | 没有真源的数据一律**不渲染数字**。指标位用 `—`；未配置的更新源**不声称任何版本**；只有一个真选项的设置行**照实显示单值**，不造假备选 |
| 词表 | 纯简体中文 UI；术语照 `index.html` 附录 A 词表 |
| 提交 | 每条提交信息**必须先给用户过目**；工作树保持可编译、测试全绿 |
| 篡改历史 | 不用 `--amend`/`rebase` 改已提交内容，除非用户明确要求 |

### 0.3 环境规约（每条命令都要遵守）

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH

# 单测试套件（第一次跑前先清 fontconfig，否则可能挂起）
rm -rf ~/.cache/fontconfig/
timeout -k 15 300 ./gradlew --no-daemon :shared:jvmTest --tests '*AppSettingsTest'

# 全量 + 编译
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
```

- Gradle **必须** `--no-daemon`。
- **`--rerun` 是必需的**：不加时 `:shared:jvmTest` 会报 `UP-TO-DATE`，任务根本没执行，**不构成任何证据**。
- 外网走代理：`curl -x http://127.0.0.1:7897`。
- **禁止 `pkill -f`**（会匹配到自身 shell，本项目已被误杀过）。只按记录的 PID `kill <pid>`。
- 测试证据要**另存到独立路径**（见 §13.4）：`--rerun` 会覆盖 `shared/build/test-results/jvmTest/` 下的同名 XML。

### 0.4 当前基线（开始前先自己确认一遍）

```bash
git log --oneline -1          # 期望 HEAD = 1a48791（或其后继）
timeout -k 15 900 ./gradlew --no-daemon :desktopApp:compileKotlin :shared:jvmTest --rerun
# 期望：BUILD SUCCESSFUL，18 suites / 77 tests / 0 failures
```

若基线不绿，**先停下来问用户**，不要在红基线上开工。

---

## 1. 背景

计划 2（控制台石墨 UI 改版）刻意把设置屏做成占位：8 个分类可导航，但「外观」4 行全禁用、
其余 7 类是「「X」设置页占位。」文案。当时的非目标是「不做 M2–M5 功能」，且不造数红线要求没有真源就不渲染数值。

用户现已要求把占位符**全部做完**。经盘点，这实际上是 **6 个独立子系统**，必须逐个走
spec → plan → 实施：

| 序 | 子项目 | 消掉的占位 | 依赖 |
|---|---|---|---|
| **P1** | **设置全域 + 持久化**（本文） | 设置 6 分类、更新、关于、连接导入导出 | **无**（不需要新 SSH 协议能力） |
| P2 | 主机指标（M4） | 仪表盘四指标 + 两图、服务器卡瓦片、终端状态栏指标位 | 需扩展 `SshSession`（exec 通道） |
| P3 | 端口转发（M3）+ 凭据/系统钥匙串 | 端口转发屏、凭据分类 | sshj 隧道 API；OS 钥匙串 |
| P4 | SFTP 文件传输（M2） | 文件传输屏 | 需扩展 `SshSession`（SFTP） |
| P5 | 多标签 + 分屏（M4） | 终端 `+`、分屏 | 需把 `AppModel` 的单会话模型重构成会话注册表 |
| P6 | AI 助手（M5） | AI 侧栏、智能助手分类 | LLM 接入 + 审批流 + 安全模型 |

P1 排第一，因为它是唯一**不需要任何新协议能力**的自洽闭环。

---

## 2. 范围

### 2.1 做

设置持久化地基 + **6 个分类做成真页**：外观 · 终端 · 连接 · 存储 · 更新 · 关于。

> 设置屏**左侧分类导航仍是 8 项**（照设计包 IA 不变）。其中 6 项进真页，
> 智能助手与凭据 2 项仍进诚实占位页。即「**8 分类可导航，6 真 2 占位**」。

### 2.2 不做（明确）

- **智能助手 / 凭据两个分类**：保持诚实占位。P1 不做壳 —— 做壳等于把 P6/P3 各做两遍。
- **浅色主题**：见决策 D2。
- **PuTTY / XShell 导入**：见决策 D4。
- **不引入 SQLite**：现有持久层是 JSON 文件，照实显示数据目录。
- 不动 SSH / 终端 / 服务器仓库的功能层逻辑。
- **不为界面缩放预先重构布局**：见 §15 风险 R1。

---

## 3. 决策记录（用户已确认，**不要重新讨论**）

| # | 决策 | 取值 |
|---|---|---|
| D1 | P1 边界 | 6 分类 + 持久化地基；智能助手/凭据留诚实占位 |
| D2 | 浅色主题 | **拆出去**。只做「深色 / 跟随系统（当前恒解析为深色）」；「浅色」渲染为**禁用项 + 未实现标注** |
| D3 | 更新 / 关于 | 更新源可配置（`owner/repo`），**默认未配置 → 不检查、不声称任何远端版本**；关于页 = 本地版本 + 开源致谢 + 可配置反馈入口 |
| D4 | 导入导出 | 导出 JSON/CSV（默认脱敏）+ 导入自家 JSON + 导入 OpenSSH `~/.ssh/config` + 数据目录显示；PuTTY/XShell 拆到下一轮 |
| D5 | 持久化架构 | **单一 `AppSettings` + 单一 `settings.json` + 即时生效**（无脏态/无显式保存） |
| D6 | 自动连接 | `autoConnect` = **选一台**，不是启动连全部（启动连 N 台会一次打出 N 条 SSH 连接） |
| D7 | 「只有一个真选项」的行 | 界面字体 / 显示语言 / 终端字体 / 终端调色板 **照实渲染单值**，不造假备选 |
| D8 | 版本号 | 单一真相源 `gradle.properties` 的 `barezen.version`，初值 **`0.1.0`**；**这会改掉 `desktopApp/build.gradle.kts` 里硬编码的 `packageVersion = "1.0.0"`**（见 §12.1） |

---

## 4. 架构

### 4.1 文件清单

**新增（commonMain）**

```
shared/src/commonMain/kotlin/com/barezen/barezen_ssh/
├── BuildInfo.kt                                  object BuildInfo { const val VERSION = "0.1.0" }
├── settings/AppSettings.kt                       AppSettings + 3 个枚举 + sanitized()
├── settings/SettingsRepository.kt                interface + SettingsLoad sealed
├── app/SettingsModel.kt                          Compose 状态持有者
└── ui/UiConstants.kt                             ADDRESS_MASK（隐藏地址用的固定掩码）
```

**新增（jvmMain）**

```
shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/
├── settings/FileSettingsRepository.kt            ~/.barezen/settings.json 读写
├── settings/ConnectionTransfer.kt                导出/导入（JSON · CSV · OpenSSH）
├── settings/OpenSshConfigParser.kt               ~/.ssh/config 解析
├── settings/UpdateChecker.kt                     GitHub Releases 检查（JDK HttpClient）
└── ui/screens/
    ├── SettingsRows.kt                           共享行控件（开关行/选择行/静态值行/动作行）
    ├── SettingsAppearanceSection.kt
    ├── SettingsTerminalSection.kt
    ├── SettingsConnectionSection.kt
    ├── SettingsStorageSection.kt
    ├── SettingsUpdateSection.kt
    └── SettingsAboutSection.kt
```

**新增（测试）**

```
shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt
shared/src/commonTest/kotlin/com/barezen/barezen_ssh/app/SettingsModelTest.kt
shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/settings/
├── FileSettingsRepositoryTest.kt
├── OpenSshConfigParserTest.kt
├── ConnectionTransferTest.kt
└── UpdateCheckerTest.kt
shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/BuildInfoTest.kt
shared/src/jvmTest/kotlin/com/barezen/barezen_ssh/app/SettingsScreenTest.kt
```

**修改**

| 文件 | 改动 |
|---|---|
| `shared/src/jvmMain/.../ui/screens/SettingsScreen.kt` | 8 分类导航保留；6 类路由到新 section 文件；2 类仍走占位；顶部加两条可关闭通知（加载告警 / 保存失败） |
| `shared/src/jvmMain/.../App.kt` | `App(model)` 读 `model.settings.settings` → 决定主题 + `LocalDensity` 覆盖 |
| `shared/src/jvmMain/.../app/AppModel.desktop.kt` | `real()` 里并列构造 `SettingsModel(FileSettingsRepository())` 并 `load()` |
| `shared/src/commonMain/.../app/AppModel.kt` | 新增 `val settings: SettingsModel` 构造参数与属性 |
| `desktopApp/build.gradle.kts` | `packageVersion = providers.gradleProperty("barezen.version").get()` |
| `gradle.properties` | 追加 `barezen.version=0.1.0` |
| `shared/src/jvmTest/.../app/PlaceholderScreensTest.kt` | 3 条断言随 P1 更新（§14） |
| `shared/src/jvmTest/.../app/AppShellTest.kt` | 1 条断言随 P1 更新（§14） |

**为什么这样切文件**：`SettingsScreen.kt` 现在 169 行，加 6 个分类会膨胀到近千行。按分类一文件、
共享控件单独一文件，每个文件一个明确职责，也便于单独测试。`SettingsScreen()` 的**公开签名与所在包不变**，
所以既有 `import com.barezen.barezen_ssh.ui.screens.SettingsScreen` 不受影响。

### 4.2 精确签名

```kotlin
// commonMain/settings/SettingsRepository.kt
interface SettingsRepository {
    /** 永不抛。失败时内部隔离并回落默认值，由返回值如实报告。 */
    fun load(): SettingsLoad
    /** 失败抛 SettingsWriteException。 */
    fun save(settings: AppSettings)
}

sealed interface SettingsLoad {
    data class Ok(val settings: AppSettings, val warnings: List<String>) : SettingsLoad
    /** 文件不可读/坏 → 已隔离副本 + 回落默认值。quarantinePath 为 null 表示隔离失败（此时必须已抛） */
    data class Recovered(val settings: AppSettings, val quarantinePath: String?, val cause: String) : SettingsLoad
}

class SettingsWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

// commonMain/settings/AppSettings.kt
data class Sanitized(val value: AppSettings, val warnings: List<String>)
fun AppSettings.sanitized(): Sanitized

// commonMain/app/SettingsModel.kt
class SettingsModel(private val repo: SettingsRepository) {
    var settings: AppSettings by mutableStateOf(AppSettings.Default); private set
    var loadNotice: String? by mutableStateOf(null); private set      // 可关闭横幅
    var saveError: String? by mutableStateOf(null); private set       // 可关闭错误行
    fun load()
    fun update(transform: (AppSettings) -> AppSettings)              // 改内存 + 立即 save
    fun dismissLoadNotice()
    fun dismissSaveError()
}

// jvmMain/settings/UpdateChecker.kt —— 注入 fetch 以便离线测试
class UpdateChecker(private val getJson: suspend (String) -> String) {
    suspend fun check(repo: String, channel: UpdateChannel, currentVersion: String): UpdateResult
}
sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data class UpToDate(val latest: String, val checkedAt: String) : UpdateResult
    data class NewerAvailable(val latest: String, val url: String, val checkedAt: String) : UpdateResult
    /** 非 semver tag：不谎报"有新版本"，如实说无法比较 */
    data class Uncomparable(val latest: String, val checkedAt: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}
```

### 4.3 数据流（单向）

```
~/.barezen/settings.json
      │ load()  ──► SettingsLoad.Ok / Recovered
      │ sanitized() 收敛非法值
      ▼
SettingsModel.settings（Compose state）─────────┐
      │                                          │
      ├── 应用级（App() 根部）：主题 · LocalDensity
      ├── 屏级：隐藏地址（ServersScreen/TerminalScreen）· 启动时连接
      └── 组件级：TerminalView 的 3 个终端选项
                                                  ▲
   设置屏某行被改 ──► settings.update{ copy } ──► save()（原子写）──► state 变化 ──► 重组
```

### 4.4 生效点四分类（**实现时按此归位，放错位置就换不了全应用外观**）

| 类 | 项目 | 落点 |
|---|---|---|
| **应用级** | `theme`、`uiScale` | 只能包在 `App()` 最外层（§9.1） |
| **屏级** | `hideAddresses` | 服务器屏卡片 + 终端状态栏（§9.2） |
| **屏级** | `autoConnectServerId` | 启动流程（§9.3） |
| **组件级** | `copyOnSelect` / `shiftInsertPaste` / `sudoAutofill` | JediTerm `TerminalView` 配置（§9.4） |
| **延迟生效** | `conflictPolicy`（消费者是 P4 的 SFTP）、`sudoAutofill`（需 P3 的凭据库提供密码来源） | P1 只做「可配置 + 存得下 + **如实标注「待 X 接入后生效」**」（§8.2/§8.3） |

> **禁止**为了让 `sudoAutofill` 看起来生效而把密码写入明文文件。本项目**故意不落盘密码** ——
> `StoredAuth.Password` 是无字段 `data object`，连接对话框的密码只活在内存里。这条不可破。

---

## 5. 数据模型（完整）

```kotlin
// commonMain/settings/AppSettings.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.Serializable

@Serializable
enum class Theme { DARK, FOLLOW_SYSTEM }          // 不含 LIGHT：持久化层不得持有不可能的值
@Serializable
enum class UpdateChannel { STABLE, PREVIEW }
@Serializable
enum class ConflictPolicy { ASK, OVERWRITE, SKIP, RENAME }

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
        /** 界面缩放合法档；sanitize 会把越界值收敛到最近的一档 */
        val UI_SCALE_CHOICES = listOf(1.0f, 1.25f, 1.5f)
        /** 当前真实存在的可选值（只有一个 = 照实渲染，不造假备选） */
        val UI_FONT_CHOICES = listOf(UI_FONT_DEFAULT)
        val LANGUAGE_CHOICES = listOf(LANGUAGE_DEFAULT)
        val TERMINAL_FONT_CHOICES = listOf(TERMINAL_FONT_DEFAULT)
        val TERMINAL_PALETTE_CHOICES = listOf(TERMINAL_PALETTE_DEFAULT)

        val Default = AppSettings()
    }
}
```

### 5.1 `sanitized()` 契约

**绝不抛异常、绝不产生坏布局。** 规则：

| 字段 | 规则 | 违法时 |
|---|---|---|
| `uiScale` | 必须是 `UI_SCALE_CHOICES` 之一 | 收敛到**最近的合法档**；并记 warning |
| `uiFont` | 必须在 `UI_FONT_CHOICES` | 回默认 + warning |
| `language` | 必须在 `LANGUAGE_CHOICES` | 回默认 + warning |
| `terminalFont` | 必须在 `TERMINAL_FONT_CHOICES` | 回默认 + warning |
| `terminalPalette` | 必须在 `TERMINAL_PALETTE_CHOICES` | 回默认 + warning |
| `theme` / `updateChannel` / `conflictPolicy` | 枚举（类型系统已保证） | 未知字符串由 Json 层 `coerceInputValues` 收敛（§6） |
| `updateRepo` | `null` 或匹配 `^[\w.-]+/[\w.-]+$` | 不匹配 → `null` + warning |
| `feedbackUrl` | `null` 或 `http://` / `https://` 开头 | 不匹配 → `null` + warning |
| `autoConnectServerId` | `null` 或非空白 | 空白 → `null` |
| `schemaVersion` | 原样保留（迁移留待将来） | — |

### 5.2 语义备注

- `uiScale` 用 `Float` 而非 Double —— 只做 `LocalDensity` 乘法，精度足够。
- `autoConnectServerId` **指向的服务器是否存在**不由 `sanitized()` 判断（它拿不到服务器列表）。
  存在性由 UI 层处理：目标不存在时该行显示「该服务器已不存在」，且启动时不连接、**不弹窗、不打扰**。
- `conflictPolicy` 默认 `ASK`（需求里「询问」是列表首项，也最安全）。

---

## 6. 持久化

### 6.1 位置与格式

- **路径**：`~/.barezen/settings.json`（与 `servers.json` 同目录，使「数据目录显示」有意义）。
- **Json 配置（三个开关都必要）**：

```kotlin
private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true     // 前向兼容：新版本写的字段，旧版本读到不炸
    encodeDefaults = true        // 把默认值也写进文件；否则改回默认值时字段消失，导出/diff 会莫名其妙
    coerceInputValues = true     // 未知枚举值 → 该属性的默认值（而不是抛异常）
    isLenient = false
}
```

> `coerceInputValues = true` 是关键：kotlinx.serialization 默认对未知枚举值**抛异常**，
> 而我们要的是「手改坏了也不崩，收敛到默认并告知」。

- **文件精确形状**（示例）：

```json
{
  "schemaVersion": 1,
  "theme": "FOLLOW_SYSTEM",
  "uiFont": "Noto Sans SC",
  "uiScale": 1.0,
  "language": "zh-CN",
  "terminalFont": "JetBrains Mono",
  "terminalPalette": "graphite",
  "copyOnSelect": false,
  "shiftInsertPaste": true,
  "sudoAutofill": false,
  "autoConnectServerId": null,
  "hideAddresses": false,
  "conflictPolicy": "ASK",
  "updateRepo": null,
  "updateChannel": "STABLE",
  "autoCheckUpdates": false,
  "feedbackUrl": null
}
```

### 6.2 写入：原子替换

```kotlin
private fun write(settings: AppSettings) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(json.encodeToString(settings))
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
}
```

先写同目录 `.tmp` 再 move —— 避免写到一半崩溃留下半截 JSON（那正是制造"不可读文件"的最可能路径）。
写失败（磁盘满/权限）→ 包成 `SettingsWriteException` 抛出。

### 6.3 读取：失败即隔离，绝不静默重置

```
file 不存在                → SettingsLoad.Ok(AppSettings.Default, warnings = [])
解码成功                   → sanitized() → Ok(收敛后, warnings)
解码/IO 失败               → ① rename 成 settings.json.corrupt-<epochMillis>
                             ② 成功 → Recovered(Default, quarantinePath, cause)
                             ③ 失败（rename 不成功/副本没落地）→ 抛 SettingsWriteException
```

> ③ 的意义：宁可让读取失败并被上层报告，也不能在"用户配置可能还有救"的情况下一声不响地丢掉线索。

### 6.4 `schemaVersion`

只记录版本号，**不写迁移框架**（YAGNI）。`ignoreUnknownKeys` + 默认值已覆盖「加字段」这类演进；
真出现破坏性变更时再加迁移。

---

## 7. 错误处理

| 情形 | 行为 | 用户可见文案（§8.0） |
|---|---|---|
| 文件不可读 / JSON 坏 | 隔离副本 → 用 `Default` → 设置屏顶部**可关闭横幅** | 「设置文件无法读取，已重置为默认值；原文件已保存为 `settings.json.corrupt-<时间戳>`。」 |
| 字段值非法被收敛 | 顶部**可关闭横幅**列出被收敛的项 | 「已修正 2 处无效设置：界面缩放、更新源。」 |
| 写盘失败 | **保留内存态**（用户刚改的仍在）+ 设置屏**可关闭错误行** | 「保存失败：<原因>。改动已生效但未能写入磁盘。」 |
| 更新源未配置 | 更新分类显示说明；**不发起任何请求、不声称任何版本** | 「未配置更新源」+「请先填写 GitHub 仓库（owner/repo）」 |
| 更新检查失败 / 离线 | 显示失败原因 | 「检查失败：网络不可达」等（§11.4） |
| 导入解析失败 | 显示失败原因，**不改动现有服务器** | 「导入失败：<原因>」 |
| 导入部分跳过 | 显示结果计数与原因 | 「新增 3 台，跳过 2 台（1 台缺少用户名、1 台为通配 Host）」 |

**两条原则**：

1. **写失败不回滚内存态** —— 回滚会让用户以为白改了一次。
2. **凡是"没做/跳过/失败"，都要说出来** —— 不静默。

---

## 8. UI 规格

### 8.0 全屏通用

- 左列 200dp 分类导航（8 项，形态与现有一致）；右列滚动内容区。
- **顶部通知区**（仅在有内容时出现）：加载告警横幅与保存失败错误行，各带一个「知道了」关闭按钮。
- 行控件四种形态（`SettingsRows.kt`）：
  - `ToggleRow(title, desc?, checked, onCheckedChange)`
  - `ChoiceRow(title, desc?, options: List<String>, selectedIndex, enabled, onSelect)`
  - `StaticValueRow(title, desc?, value)` —— **无可交互控件**，用于只有一个真值的项
  - `ActionRow(title, desc?, actions: List<Pair<String, () -> Unit>>)`
- 行样式沿用设计包 `.setting-row`（**分隔线行**：`padding 16/0` + 末行除外的底边线，无底色无圆角）。
- 延迟生效项的标注用 `desc` 末尾追加：`（待 <子项目> 接入后生效）`。

### 8.1 外观

| 行 | 控件 | 文案 |
|---|---|---|
| 主题 | `ChoiceRow`，3 项 | 选项：`跟随系统` / `深色` / `浅色（未实现）`；**「浅色」禁用**；desc：`跟随系统在浅色主题实现前等同于深色` |
| 界面字体 | `StaticValueRow` | 值 `Noto Sans SC`；desc `随包分发，暂无可选项` |
| 界面缩放 | `ChoiceRow`，3 项 | `100%` / `125%` / `150%` |
| 显示语言 | `StaticValueRow` | 值 `简体中文`；desc `当前仅提供简体中文` |

### 8.2 终端

| 行 | 控件 | 文案 |
|---|---|---|
| 终端字体 | `StaticValueRow` | 值 `JetBrains Mono`；desc `随包分发，暂无可选项` |
| 终端主题 | `StaticValueRow` | 值 `石墨（graphite）`；desc `当前仅一套终端调色板` |
| 选中即复制 | `ToggleRow` | desc `开启后，在终端里选中文本即写入剪贴板` |
| Shift+Insert 粘贴 | `ToggleRow` | desc `用 Shift+Insert 粘贴剪贴板内容`；**⚠️ 仅当 §9.4 证明其可真实施时才渲染这一行，否则整行删除** |
| 自动填入 sudo 密码 | `ToggleRow` | desc `关` / `开`；**追加** `（待凭据库接入后生效）` |

### 8.3 连接

| 行 | 控件 | 文案 |
|---|---|---|
| 启动时连接 | `ChoiceRow` | 选项 `不自动连接` + 各服务器名；**若 `autoConnectServerId` 指向已不存在的服务器**，该行 desc 显示 `该服务器已不存在` |
| 隐藏服务器地址 | `ToggleRow` | desc `直播/录屏时把服务器列表与连接对话框里的地址与端口显示为掩码（编辑对话框不受影响）` |
| 文件名冲突策略 | `ChoiceRow`，4 项 | `询问` / `覆盖` / `跳过` / `重命名`；**追加** `（待文件传输接入后生效）` |

### 8.4 存储

| 行 | 控件 | 文案 |
|---|---|---|
| 导出连接 | `ActionRow`，2 个按钮 | `导出 JSON…` / `导出 CSV…` |
| 导入连接 | `ActionRow`，2 个按钮 | `导入 JSON…` / `导入 OpenSSH 配置…` |
| 数据目录 | `ActionRow`，1 个按钮 | 显示 `~/.barezen` 的**绝对路径**；按钮 `打开目录` |

**导出流程**（两步，安全优先）：

```
点「导出 JSON…」/「导出 CSV…」
  → JFileChooser 选保存位置（默认文件名 connections.json / connections.csv）
  → 弹确认对话框：「包含私钥路径？」
        · 复选框，**默认不勾**
        · 说明文字：「私钥路径会暴露你的用户名与目录结构，仅在需要完整迁移时勾选。」
        · 按钮：取消 / 导出
  → 写文件 → 结果提示「已导出 N 台服务器到 <路径>」
```

> **为什么不做成常驻设置项**：一个"记住勾选"的「包含私钥路径」很容易被用户遗忘，
> 于是每次导出都在悄悄泄露路径。逐次确认更安全。

### 8.5 更新

| 行 | 控件 | 文案 |
|---|---|---|
| 更新源 | 文本输入 | placeholder `owner/repo`；失焦或回车时校验并保存；非法格式显示 `格式应为 owner/repo` |
| 发布频道 | `ChoiceRow`，2 项 | `稳定` / `预览`；desc `稳定仅匹配正式发布；预览包含预发布` |
| 自动检查 | `ToggleRow` | desc `启动时检查更新` |
| 立即检查 | 按钮 | **`updateRepo` 为空时禁用**，desc 显示 `请先填写更新源` |
| 检查结果 | 结果行 | 见 §11.4 |

### 8.6 关于

| 行 | 内容 |
|---|---|
| 产品 | `BareZen-SSH` + 版本号（来自 `BuildInfo.VERSION`） |
| 开源组件致谢 | 下表逐项：组件名 · 许可 · 链接 |
| 反馈 | **仅当 `feedbackUrl` 非空**时显示 `反馈` 链接；为空则整行不渲染 |

致谢表内容（**这些是真实依赖，不许漏也不许编**）：

| 组件 | 许可 | 链接 |
|---|---|---|
| sshj | Apache-2.0 | https://github.com/hierynomus/sshj |
| JediTerm | Apache-2.0 | https://github.com/JetBrains/jediterm |
| JetBrains Mono | OFL-1.1 | https://github.com/JetBrains/JetBrainsMono |
| Noto Sans SC | OFL-1.1 | https://github.com/notofonts/noto-cjk |
| Material Symbols / Icons | Apache-2.0 | https://github.com/google/material-design-icons |

### 8.7 智能助手 / 凭据（保持占位）

文案照旧的克制风格，但要**说清归属**，不再是一句含糊的「占位」：

- 智能助手：「智能助手属 M5，尚未接入。」+ `（AI 会话侧栏与命令审批流在后续里程碑实现）`
- 凭据：「凭据库属 M3，尚未接入。」+ `（系统钥匙串集成在后续里程碑实现）`

---

## 9. 生效点接线（file 级，照着改）

### 9.1 应用级：主题 + 界面缩放

`shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/App.kt` —— 现在是：

```kotlin
@Composable
fun App(model: AppModel) = BareZenTheme { BareZenAppContent(model) }
```

改为：

```kotlin
@Composable
fun App(model: AppModel) {
    val settings = model.settings.settings
    // 主题：浅色未实现，故 FOLLOW_SYSTEM 与 DARK 目前都解析为深色。
    // 这里保留 when 分支，等浅色主题落地时只改这一处。
    val dark = when (settings.theme) {
        Theme.DARK -> true
        Theme.FOLLOW_SYSTEM -> isSystemInDarkTheme() || true   // 恒 true：仅有深色板
    }
    BareZenTheme(darkTheme = dark) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * settings.uiScale, base.fontScale),
        ) {
            BareZenAppContent(model)
        }
    }
}
```

注意事项：

- `BareZenTheme` 现在**没有参数**，需增加 `darkTheme: Boolean = true` 形参（当前只实现深色分支；
  传 `false` 时仍是深色板，并加一行注释说明浅色待实现）。
- `isSystemInDarkTheme()` 来自 `androidx.compose.foundation`。
- **不要**把 `uiScale` 乘进 `fontScale` —— 只缩放 `density`，否则字号会被缩放两次。

### 9.2 屏级：隐藏服务器地址

**先说清楚它到底能遮什么**（初版设计在这里写错过，已核实修正）：

| 位置 | 是否能遮 | 处置 |
|---|---|---|
| `ServersScreen.kt:341` 服务器卡地址行 `"${server.host}:${server.port}"` | ✅ 能 | 开启时替换为固定掩码 |
| `ConnectDialog` 的**只读**「端口」字段 | ✅ 能 | 开启时端口字段显示掩码（连接仍用真实端口） |
| `TerminalScreen` 状态栏 | ❌ **没有地址可遮** | 它显示的是**服务器名**（`已连接 · oracle 生产 · SSH 往返 N ms`），不是 `host:port`。**不动它** |
| `ServerEditDialog` 的**可编辑**地址/端口字段 | ❌ **不能遮** | 遮了就没法编辑。**明确不遮**，并在设置行 desc 里说明 |

实现：

- 掩码常量放**新建的** `shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/UiConstants.kt`：
  ```kotlin
  /** 隐藏地址时使用的固定掩码。固定串、不随真实长度变化——否则长度本身就是信息。 */
  const val ADDRESS_MASK = "•••.•••.•••.•••"
  ```
- 设置行 desc 照实写明局限：`直播/录屏时把服务器列表与连接对话框里的地址与端口显示为掩码（编辑对话框不受影响）`。

> 把"遮不到的地方"写进 desc，是因为用户会**以为**开了就全遮住了。不如提前说清。

### 9.3 屏级：启动时连接

- `AppModel` 在 `load()` 拿到 `autoConnectServerId` 后，若该 id 在 `repo.list()` 里存在，
  则在应用启动后发起一次连接（复用既有 `requestConnect` 语义，但**跳过对话框**）。
- **只对 `StoredAuth.Key` 的服务器自动连接。** `Password` 认证的服务器**不自动连接**，
  该行 desc 显示 `该服务器使用密码认证，启动时不会自动连接`。
- 目标不存在 → 不连接、不弹窗、不打扰；该行 desc 显示 `该服务器已不存在`。

> 这条限制是**有意的**：我们没有也不该有持久化密码。与其做一个点了没反应的开关，不如说清楚。

### 9.4 组件级：终端三选项 —— **其中一项 JediTerm 不支持，必须自己实现或删行**

接线前先用 `javap` 核对过 **JediTerm 3.73** 的真实接口（下面是核对结果，**不要凭记忆改**）：

| 设置 | JediTerm 3.73 是否提供 | 做法 |
|---|---|---|
| 选中即复制 | ✅ **提供** `SettingsProvider.copyOnSelect(): Boolean`，且被 `TerminalPanel` 消费 | 在 `BareZenTerminalSettings` 里 `override fun copyOnSelect() = settings.copyOnSelect` |
| Shift+Insert 粘贴 | ❌ **不提供** | 见下 |
| 自动填入 sudo 密码 | — | P1 **只存不生效**，**不得接进 `TerminalView`** |

**关于 Shift+Insert —— 已核实的坏消息**：`jediterm-ui:3.73` 里
- 没有任何 `INSERT` 键处理（`TerminalPanel` 中搜不到 `VK_INSERT` / `"INSERT"`）；
- `SettingsProvider.emulateX11CopyPaste(): Boolean` **声明了但没有任何调用点**（只在 `DefaultSettingsProvider` /
  `UserSettingsProvider` 里被实现，全 jar 无消费者）—— 覆写它**等于没有效果**。

`TerminalView.kt:70` 的 `BareZenTerminalSettings : DefaultSettingsProvider()` 是 `private`，
且 `JediTermWidget(BareZenTerminalSettings())` 在 `:30` 内联构造 —— 要传参得先把它改成带参数的构造。

因此 `shiftInsertPaste` 有两条**都必须诚实**的路：

1. **自己实现**：拿到 `JediTermWidget` 内部的 Swing 组件后，向它的 `InputMap` 注册
   `KeyStroke.getKeyStroke("shift INSERT")`，并在 `ActionMap` 里触发粘贴动作。
   **动手前先验证粘贴动作可达**（核对 `ActionMap` 的键名/动作是否存在）；
2. **若不可达 → 删掉这一行设置行**，并在本设计的实施报告里记录原因。

> **通用规则（适用于本设计的每一行设置）**：如果底层做不到，就**删掉这一行**，
> **绝不渲染一个点了没反应的开关**。设置项存在的意义是它真的会改变行为。

---

## 10. 导入导出（作用于**连接**，不是设置）

### 10.1 导出 JSON 形状

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-10-01T18:00:00Z",
  "servers": [
    {
      "name": "web-01",
      "host": "10.0.0.11",
      "port": 22,
      "user": "root",
      "tags": ["生产"],
      "auth": { "type": "key" }
    }
  ]
}
```

- **不导出 `id`**（导入时重新生成 UUID，避免跨机器 id 冲突）。
- **脱敏时 `auth` 只写 `{"type":"key"}`，不写 `keyPath`**；勾选「包含私钥路径」时写 `{"type":"key","keyPath":"/home/u/.ssh/id_ed25519"}`。
- `Password` 认证写 `{"type":"password"}`（本就无秘密可泄）。

### 10.2 导出 CSV

- **UTF-8 with BOM**（`EF BB BF` 三字节开头）—— 否则 Excel 打开中文列名/标签会乱码。
- 表头：`名称,地址,端口,用户名,标签,认证方式`
- 标签用 `;` 连接（避免与 `,` 分隔符冲突）。
- 含 `,` `"` 或换行的字段用双引号包裹，内部 `"` 转义为 `""`。
- 认证方式列取值：`密码` / `私钥`。

### 10.3 导入 OpenSSH `~/.ssh/config`

解析规则（**逐条实现，逐条有测试**）：

| 输入 | 处理 |
|---|---|
| `Host a b c` | 每个别名各生成一台服务器，`name` = 别名 |
| `HostName x` | `host = x`；缺省时 `host` = 该块第一个别名（OpenSSH 语义） |
| `User u` | `user = u`；**缺省 → 跳过该条**，计入「跳过：缺少用户名」 |
| `Port p` | `port = p`；缺省 `22` |
| `IdentityFile path` | `auth = Key(path)`；缺省 `auth = Password` |
| `Host` 值含 `*` `?` `!` | **跳过**，计入「跳过：通配 Host」 |
| `Include ...` | **跳过**，导入结果里提示「未跟随 Include 指令」 |
| `#` 注释 / 空行 | 忽略 |
| 块内未知关键字 | 忽略（不报错） |

### 10.4 去重与结果

- 去重键：`(host 小写, port, user)`。已存在 → 跳过。
- 结果文案：「新增 3 台，跳过 2 台（1 台缺少用户名、1 台为通配 Host）」——
  括号内按原因聚合计数，无跳过则不显示括号。

### 10.5 文件对话框

- 用 `javax.swing.JFileChooser`（与既有 `ConnectDialog.choosePrivateKeyFile()` 同款，保持一致）。
- 导出默认文件名：`connections.json` / `connections.csv`。
- 导入默认目录：`~/.ssh/`（OpenSSH 项）。

---

## 11. 更新检查

### 11.1 端点与选版

- `GET https://api.github.com/repos/{owner}/{repo}/releases`
- 请求头：`Accept: application/vnd.github+json`、`User-Agent: BareZen-SSH`（**GitHub 要求 UA，缺了会 403**）。
- `STABLE`：取第一个 `draft == false && prerelease == false`。
- `PREVIEW`：取第一个 `draft == false`（可含 prerelease）。
- 超时：连接 10s / 整体 20s。

### 11.2 版本比较

- 从 `tag_name` 去掉前导 `v` 后按 `.` 切段做数字比较。
- **非 semver tag**（如 `release-2026`）→ 返回 `Uncomparable`，UI 如实说「无法比较版本」，
  **不谎报"有新版本"**，也不谎报"已是最新"。

### 11.3 注入式 fetch（为了能离线测试）

```kotlin
class UpdateChecker(private val getJson: suspend (String) -> String)
```

生产注入用 `java.net.http.HttpClient` 的实现（**JDK 自带，零新依赖**）；
测试注入假的 `getJson`，**完全不走网络**。

### 11.4 结果文案

| 结果 | 文案 |
|---|---|
| `NotConfigured` | `未配置更新源` + `请先填写 GitHub 仓库（owner/repo）` |
| 检查中 | `正在检查…` |
| `UpToDate` | `已是最新版本（{checkedAt} 检查）` |
| `NewerAvailable` | `发现新版本 {latest}（当前 {BuildInfo.VERSION}）` + 可点链接 |
| `Uncomparable` | `最新发布为 {latest}，但版本号格式无法比较` |
| `Failed` 404 | `检查失败：仓库不存在或没有 Release` |
| `Failed` 403 | `检查失败：GitHub 限流，请稍后再试` |
| `Failed` 网络 | `检查失败：网络不可达` |
| `Failed` 非 JSON | `检查失败：响应格式异常` |

- **不下载、不自动安装**：只做检查与跳转。
- `autoCheckUpdates` 默认 `false`；为 `true` 且 `updateRepo` 非空时，启动后台检查一次。

---

## 12. 关于

### 12.1 版本单一真相源

**现状问题**：`desktopApp/build.gradle.kts` 里硬编码了 `packageVersion = "1.0.0"`，
而 UI 里没有任何版本展示。`1.0.0` 对一个 M2–M5 全未实现的早期应用是**不实**的。

**改为**：

1. `gradle.properties` 追加：
   ```properties
   # 应用版本（单一真相源）
   barezen.version=0.1.0
   ```
2. `desktopApp/build.gradle.kts`：`packageVersion = providers.gradleProperty("barezen.version").get()`
3. `shared/src/commonMain/.../BuildInfo.kt`：
   ```kotlin
   object BuildInfo { const val VERSION = "0.1.0" }
   ```
4. **测试守卫** `BuildInfoTest`：从 `user.dir` 向上逐级找 `gradle.properties`，
   断言 `barezen.version` 与 `BuildInfo.VERSION` 相等 → **任一处被改而另一处没跟上就红**。

> **这是一处行为变更**：打包版本从 `1.0.0` 变成 `0.1.0`。理由是 `0.1.0` 反映真实进度（M0/M1）。
> 若用户希望保持 `1.0.0`，把 `barezen.version` 设为 `1.0.0` 即可，其余不变。

### 12.2 关于页内容

见 §8.6。致谢**只列真实依赖**，不得为凑数编造组件。

---

## 13. 测试规格

### 13.1 `AppSettingsTest`（commonTest）

| 用例 | 断言 |
|---|---|
| `defaultsAreStable` | 逐字段断言默认值（防止无意改动默认值） |
| `roundTripSerialization` | `encode` 再 `decode` 相等 |
| `unknownKeysAreIgnored` | 含未知字段的 JSON 能解码（前向兼容） |
| `missingKeysUseDefaults` | 只给部分字段的 JSON 能解码，缺的用默认（向后兼容） |
| `unknownEnumValueCoercesToDefault` | `"theme":"NEON"` → `FOLLOW_SYSTEM`，**不抛**（依赖 `coerceInputValues`） |
| `sanitizeClampsOutOfRangeScale` | `uiScale = 99f` → `1.5f`，且 `warnings` 非空 |
| `sanitizeClampsNegativeScale` | `uiScale = -1f` → `1.0f`，且有 warning |
| `sanitizeFallsBackOnUnknownFont` | 未知 `uiFont` / `terminalFont` → 默认 + warning |
| `sanitizeFallsBackOnUnknownPalette` | 未知 `terminalPalette` → 默认 + warning |
| `sanitizeRejectsMalformedRepo` | `updateRepo = "not a repo"` → `null` + warning |
| `sanitizeAcceptsValidRepo` | `"owner/repo"` → 保留，无 warning |
| `sanitizeRejectsNonHttpFeedbackUrl` | `"ftp://x"` → `null` + warning |
| `sanitizeBlanksAutoConnectId` | `"   "` → `null` |
| `sanitizeIsIdempotent` | 对已合法的设置调用两次，结果与 warnings 都不变 |

### 13.2 `FileSettingsRepositoryTest`（jvmTest）

| 用例 | 断言 |
|---|---|
| `missingFileYieldsDefaults` | 返回 `Ok(Default, warnings=[])`，且**不创建文件** |
| `roundTripPersistsAllFields` | save 后 load 相等 |
| `corruptFileIsQuarantinedAndDefaultsUsed` | 返回 `Recovered`，`quarantinePath` 存在且**内容等于原文**，`settings == Default` |
| `unreadableFileIsNotOverwrittenOnSave` | save 时原坏文件被隔离，**内容仍可找回** |
| `writeIsAtomicLeavesNoTempResidue` | 目录里无 `settings.json.tmp` |
| `partialJsonUsesDefaults` | 部分字段的 JSON → 缺的用默认 |
| `unknownFieldsIgnored` | 未知字段不炸 |
| `scaleIsClampedOnLoad` | 文件里 `uiScale: 99` → 加载后为 `1.5`，且 `warnings` 非空 |

### 13.3 `SettingsModelTest`（commonTest，用内存假仓库）

| 用例 | 断言 |
|---|---|
| `loadPopulatesFromRepository` | 假仓库返回非默认值 → `model.settings` 跟随 |
| `updateAppliesAndPersistsImmediately` | `update{...}` 后内存态已变，且假仓库**收到一次 save** |
| `saveFailureKeepsInMemoryStateAndExposesError` | 假仓库 save 抛 → 内存态**保持新值**、`saveError` 非空 |
| `recoveredLoadExposesNotice` | load 返回 `Recovered` → `loadNotice` 非空；`dismissLoadNotice()` 后为空 |
| `dismissSaveErrorClearsIt` | 关闭后 `saveError == null` |

### 13.4 `OpenSshConfigParserTest`（jvmTest）

用一段**真实风格**的夹具文本（含注释、多别名、HostName、User、Port、IdentityFile、`Host *`、`Include`），断言：

- 解析出的服务器数量与各字段值
- `Host *` 被跳过且计入「通配 Host」
- `Include` 被跳过且结果里带提示
- 缺 `User` 的块被跳过并计入「缺少用户名」
- 无 `HostName` 时 `host` = 第一个别名
- 无 `Port` 时 `port = 22`；无 `IdentityFile` 时 `auth = Password`

### 13.5 `ConnectionTransferTest`（jvmTest）

| 用例 | 断言 |
|---|---|
| `jsonExportOmitsKeyPathByDefault` | 导出文本**不含** keyPath 的值（硬断言） |
| `jsonExportIncludesKeyPathWhenRequested` | 勾选后文本含该值 |
| `csvExportStartsWithBom` | 前 3 字节为 `EF BB BF` |
| `csvHasExpectedHeader` | 表头等于 `名称,地址,端口,用户名,标签,认证方式` |
| `csvEscapesCommasQuotesNewlines` | 含 `,` `"` 换行的字段被正确引用/转义 |
| `importJsonRoundTrips` | 导出自家 → 导入，字段一致，`id` **重新生成** |
| `importDedupesByHostPortUser` | 已有同键 → 跳过，结果计数正确 |
| `importJsonRejectsMalformed` | 坏 JSON → 失败结果，**且不修改现有服务器** |

### 13.6 `UpdateCheckerTest`（jvmTest，注入假 fetch，**零网络**）

| 用例 | 断言 |
|---|---|
| `stablePicksLatestNonPrerelease` | 跳过 prerelease/draft |
| `previewMayPickPrerelease` | 取第一个非 draft |
| `notConfiguredMakesNoRequest` | 断言假 fetch **一次都没被调用** |
| `newerTagReportsUpdate` | `v0.2.0` vs 当前 `0.1.0` → `NewerAvailable` |
| `sameTagIsUpToDate` | `v0.1.0` vs `0.1.0` → `UpToDate` |
| `olderTagIsUpToDate` | `v0.0.9` → `UpToDate` |
| `vPrefixIsStripped` | `v0.2.0` 与 `0.2.0` 等价 |
| `nonSemverIsUncomparable` | `release-2026` → `Uncomparable`（**不谎报**） |
| `httpErrorMapsToMessage` | 404/403/坏 JSON → 对应文案 |

### 13.7 `BuildInfoTest`（jvmTest）

| 用例 | 断言 |
|---|---|
| `versionMatchesGradleProperties` | 从 `user.dir` 向上找到 `gradle.properties`，`barezen.version == BuildInfo.VERSION` |

### 13.8 `SettingsScreenTest`（jvmTest，Compose UI）

| 用例 | 断言 |
|---|---|
| `allEightCategoriesAreNavigable` | 8 个分类名都可点且切换后内容变化 |
| `appearanceShowsThemeWithLightDisabled` | `跟随系统` / `深色` 可交互；**`浅色（未实现）` `assertIsNotEnabled()`** |
| `appearanceScaleChangeUpdatesModel` | 点 `125%` → 模型 `uiScale == 1.25f` |
| `appearanceSingleValueRowsAreStatic` | `Noto Sans SC` 显示且**不是按钮**（无可点语义） |
| `terminalTogglesBindToModel` | 切「选中即复制」→ 模型变化（**经实证此路可达**）；若 §9.4 判定 Shift+Insert 不可实施并删行，则该行断言改为 `assertDoesNotExist()` |
| `sudoAutofillIsLabelledPending` | 该行 desc 含「待凭据库接入后生效」 |
| `connectionHidesAddresses` | 开「隐藏服务器地址」→ 服务器屏卡片地址变掩码 |
| `connectionConflictPolicyIsLabelledPending` | desc 含「待文件传输接入后生效」 |
| `storageShowsAbsoluteDataDir` | 显示含 `.barezen` 的绝对路径 |
| `updateCheckDisabledWhenRepoBlank` | `立即检查` `assertIsNotEnabled()` |
| `updateCheckEnabledAfterRepoFilled` | 填合法 repo 后变为可用 |
| `aboutShowsVersionAndLicences` | 显示 `BuildInfo.VERSION` 与 5 个组件的许可名 |
| `assistantAndCredentialsAreHonestPlaceholders` | 两者文案含「属 M5 / M3，尚未接入」，**且不出现任何假数据** |

### 13.9 证据固化（每个任务做完都要做）

```bash
D=.superpowers/sdd/2026-10-01-settings-completion/evidence
mkdir -p $D/<taskN>
cp shared/build/test-results/jvmTest/*.xml $D/<taskN>/
```

> **教训（本项目已踩过两次）**：`--rerun` 会覆盖 `shared/build/test-results/jvmTest/` 下的同名 XML。
> 不另存，后一次运行就会把"当次证据"顶掉，届时无法再独立复现。

---

## 14. 必须一并修改的既有断言

这 4 条是 **P1 令其不再成立的预期变更**，不是回归：

| 位置 | 现断言 | P1 后 |
|---|---|---|
| `shared/src/jvmTest/.../app/PlaceholderScreensTest.kt:66` | `跟随系统`.assertIsNotEnabled() | 变**可用** → 改为断言可点击 |
| `shared/src/jvmTest/.../app/PlaceholderScreensTest.kt:69` | `简体中文`.assertIsNotEnabled() | 变**可用**（静态值行） → 改为断言存在 |
| `shared/src/jvmTest/.../app/PlaceholderScreensTest.kt:72` | 「存储」设置页占位。 | 存储成真页 → 删该断言，改由 `SettingsScreenTest` 覆盖 |
| `shared/src/jvmTest/.../app/AppShellTest.kt:35` | `跟随系统`.assertIsNotEnabled() | 变**可用** |

实施时必须**先确认这 4 处的行号仍然如此**（若已变动，按内容定位而不是死认行号）。

---

## 15. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| **R1** | 界面缩放在非 100% 下暴露布局问题：`height(56.dp)`、`width(200.dp)`、`size(20.dp)` 等写死尺寸经 `LocalDensity` 缩放后可能拥挤或溢出 | **先落地、再逐屏真机走查 100/125/150 三档**；发现问题按「设计符规」方式修，**不预先重构**。若某屏在 150% 下明显破版，最小改动改该容器为 `fillMax*`/权重 |
| **R2** | `BareZenTheme` 增参影响既有调用点 | 形参给默认值 `darkTheme: Boolean = true`，`App()` 之外无调用点（先 `grep` 确认） |
| **R3** | 未知枚举值让 kotlinx 抛异常，导致坏文件场景崩 | `Json { coerceInputValues = true }` + `unknownEnumValueCoercesToDefault` 测试守住 |
| **R4** | 每个开关一次写盘 → 写放大 | 先不做去抖（YAGNI）；若实测明显，在 `SettingsModel.update` 内加 300ms 合并写，并补一条测试 |
| **R5** | 用户在设置里填错更新源 | 失败时给**明确原因**（404 / 限流 / 网络 / 格式），不自动纠正 |
| **R6** | OpenSSH config 有 `Include`/复杂通配 | 跳过 + **明确告知跳过了什么**，不静默 |
| **R7** | **底层不支持某个设置项**（已实证：JediTerm 3.73 不提供 Shift+Insert，且 `emulateX11CopyPaste()` 是死方法） | 动手前用 `javap`/`grep` 核对真实接口；**做不到就删掉那一行设置**，绝不渲染无效开关（§9.4） |
| **R10** | `hideAddresses` 被用户误以为"全遮住了" | 在 desc 里写明覆盖范围与不覆盖的地方（编辑对话框不遮）（§9.2） |
| **R11** | 终端状态栏并无地址可遮，若照初版设计"顺手遮一下"会写成无效代码 | 已核实并修正：状态栏只显示服务器名，**不动它**（§9.2） |
| **R8** | 版本号两处不同步 | `BuildInfoTest` 守卫（§13.7） |
| **R9** | `LocalDensity` 覆盖影响 `Compose UI 测试` 的断言（测试窗口默认 density 为 1） | 缩放测试用模型层断言（点 `125%` → 模型值），**不**用像素断言 |

---

## 16. 非目标（本设计不做）

- 浅色主题（拆为后续子项目）
- PuTTY / XShell 导入
- 更新包的下载与自动安装
- 智能助手 / 凭据分类的实体实现
- 多标签 / 分屏 / 主机指标 / 端口转发 / SFTP（分别属 P5 / P2 / P3 / P4）
- 对密码认证服务器的自动连接（我们没有也不该有持久化密码）

---

## 17. 验收清单（Definition of Done）

- [ ] 6 个分类全部可用；智能助手/凭据为**诚实占位**（无假数据）
- [ ] 设置改动**即时持久化**到 `~/.barezen/settings.json`，重启应用后保留
- [ ] 坏设置文件 → 隔离出 `.corrupt-<ts>` 副本 + 回落默认 + 顶部横幅告知
- [ ] 写失败 → 内存态保留 + 错误行告知（不回滚）
- [ ] 手改出的非法值被 `sanitized()` 收敛且告知，**不崩、不坏布局**
- [ ] 界面缩放 100/125/150 三档**真机走查过**，无破版
- [ ] 隐藏地址开启后，服务器卡与终端状态栏的 `host:port` 变固定掩码
- [ ] 导出 JSON/CSV **默认不含 keyPath**（有测试硬断言）
- [ ] 导入 JSON 往返一致；导入 OpenSSH 按规则处理并**如实报告跳过项**
- [ ] 更新源未配置时**不发起任何请求**（有测试断言 fetch 零调用）
- [ ] 关于页显示真实本地版本与 5 个组件的许可；`BuildInfoTest` 守住版本一致性
- [ ] 全量 `:desktopApp:compileKotlin` + `:shared:jvmTest --rerun` **全绿**，证据已另存
- [ ] 4 条既有断言按 §14 更新
- [ ] 无新增渐变 / 悬浮效果 / emoji；未改动 `Color.kt` / `Theme.kt` / `Type.kt` 的既有 token 值
- [ ] 每条提交信息均经用户过目
