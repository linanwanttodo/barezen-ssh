# P1「设置全域 + 持久化」代码实现说明

> 本文是以下两份文档的导读与实现说明，不替代其中任何一份：
>
> - **设计文档**（下称「设计」）：`docs/superpowers/specs/2026-10-01-settings-completion-design.md`（964 行）
> - **实施计划**（下称「计划」）：`docs/superpowers/plans/2026-10-01-settings-completion.md`（2753 行）
>
> 两者的从属关系由计划头部明确声明：**设计文档是唯一需求源**。若发现不一致，以设计为准，并回到设计修订后再动计划。

---

## 1. 两份文档的职责划分

| 维度 | 设计（spec，964 行） | 计划（plan，2753 行） |
|---|---|---|
| 回答的问题 | **做什么、为什么这么做** | **怎么做、按什么顺序做** |
| 权威范围 | 需求、8 条决策（D1–D8）、精确签名、数据模型、UI 逐字文案、错误行为、测试断言、验收清单 | 13 个任务的分解、90 个 TDD 步骤、可直接粘贴的实现代码、已核实事实、执行纪律 |
| 目标读者 | 执行本设计的下一个 AI（自包含，不共享对话上下文）+ 待用户 review | 执行代理（subagent-driven 或 inline 两种方式） |
| 变更规则 | 决策 D1–D8「用户已确认，不要重新讨论」 | 步骤用 checkbox 跟踪；每任务一次提交，提交信息先给用户过目 |
| 「完成」的定义 | §17 验收清单（17 项） | Task 13 收尾 = 验收清单的逐项执行 |

职责边界的三条要点：

1. **设计负责契约冻结**。§4.2 给出精确 Kotlin 签名、§5 给出完整数据模型、§6.1 给出 Json 四开关配置、§8 给出逐行 UI 控件与文案（精确到标点）、§13 给出 9 组测试的用例名与断言。执行者只从这里取「必须满足什么」。
2. **计划负责事实核实与落地顺序**。写计划时已完成 5 项事实核查（见计划 Self-Review 第 4 节）：`forUiTest()` 位置、`kotlinx-coroutines-test` 依赖不存在、`Server.id` 生成惯例、JediTerm 3.73 的复制粘贴 API（`copyOnSelect` 可用、`emulateX11CopyPaste` 是死方法、无 INSERT 键处理）、`BareZenTheme` 约 25 处尾随 lambda 调用不受加参影响。这些核实把「需要实施者当场判断」压缩到**唯一一处**：Task 8 的 Shift+Insert A/B 分支判定。
3. **单向依赖**。计划消费设计（每个任务的 Interfaces 块引用 Task 编号；Self-Review 给出设计章节到任务的完整映射）；设计不引用计划的任何任务。

---

## 2. 设计文档的内容结构（17 章）

按职能分五组：

| 分组 | 章节 | 内容 |
|---|---|---|
| 执行框架 | §0–§3 | 交付物定义；硬性工作方式（TDD、不造数红线、词表、提交规范）；环境规约（`--no-daemon`、`--rerun` 必加、fontconfig 清理、禁 `pkill -f`、代理）；基线（HEAD `1a48791`，18 suites / 77 tests 全绿，红基线不开工）；范围（6 分类真做 + 2 分类诚实占位，即「8 分类可导航，6 真 2 占位」）；决策 D1–D8 |
| 架构与数据 | §4–§6 | 文件清单（新增/修改）、精确签名、单向数据流、**生效点四分类**（应用级/屏级/组件级/延迟生效）；`AppSettings` 全字段与 `sanitized()` 收敛契约；`~/.barezen/settings.json` 的原子写与失败隔离 |
| 行为与界面 | §7–§9 | 7 种错误情形与两条原则（写失败不回滚内存态；凡「没做/跳过/失败」都说出来，不静默）；6 个分类的逐行控件与文案；4 个生效点的接线位置与**局限说明**（`hideAddresses` 遮不到编辑对话框；终端状态栏显示的是服务器名，无地址可遮，不动它） |
| 功能规格 | §10–§12 | 连接导入导出（默认脱敏、CSV BOM、OpenSSH 解析 9 条规则、去重键 `(host 小写, port, user)`）；更新检查（端点/选版/版本比较/结果文案/注入式 fetch）；关于页（版本单一真相源 `0.1.0` + 5 条真实开源致谢） |
| 质量与收口 | §13–§17 | 9 组测试规格；4 条必须更新的既有断言；11 条风险对策（R1–R11）；非目标；17 项验收清单 |

其中 **§4.4 生效点四分类**是整个设计的骨架，实现时按此归位，放错位置就换不了全应用外观：

| 类 | 设置项 | 落点 |
|---|---|---|
| 应用级 | `theme`、`uiScale` | 只能包在 `App()` 最外层 |
| 屏级 | `hideAddresses`、`autoConnectServerId` | 服务器屏/连接对话框；启动流程 |
| 组件级 | `copyOnSelect` 等 3 个终端选项 | JediTerm `TerminalView` 配置 |
| 延迟生效 | `conflictPolicy`（待 P4 SFTP）、`sudoAutofill`（待 P3 凭据库） | 只做「可配置 + 存得下 + 如实标注待生效」 |

---

## 3. 计划文档的内容结构

- **头部**：Goal / Architecture / Tech Stack，以及「设计文档（唯一需求源）」声明。
- **Global Constraints**：12 条项目级硬约束，隐含包含于每个任务（含基线命令与期望输出）。
- **File Structure**：三张表——新增生产代码 16 个文件、新增测试 8 个文件、修改 12 处。
- **Task 1–13**：每个任务统一格式 = `Files`（增/改/测）→ `Interfaces`（Consumes/Produces，声明任务间依赖）→ TDD 步骤（checkbox）→ 提交命令。
- **Self-Review**：spec 覆盖映射表、占位扫描（无 TBD/TODO）、类型一致性核对、已核实事实表、留给实施者的唯一判定点。
- **Execution Handoff**：subagent-driven（推荐）与 inline 两种执行方式。

13 个任务一览（含依赖关系）：

| Task | 主题 | 产出核心 | 消费 | 对应设计章节 |
|---|---|---|---|---|
| 1 | 版本单一真相源 | `BuildInfo` + `gradle.properties` 的 `barezen.version=0.1.0` + 守卫测试 | 无 | §12.1、§13.7 |
| 2 | 数据模型 | `AppSettings` + 3 枚举 + `sanitized()` | 无 | §5、§13.1 |
| 3 | 持久化 | `SettingsRepository` / `SettingsLoad` / `FileSettingsRepository` | T2 | §4.2、§6、§13.2 |
| 4 | 状态层 | `SettingsModel`（即时落盘 + 失败保留） | T2、T3 | §4.2、§7、§13.3 |
| 5 | 应用级接线 | `AppModel.settings`、`BareZenTheme(darkTheme)`、`scaledDensity` | T4 | §9.1、§15 R2 |
| 6 | 行控件 + 路由 | 4 种行控件 + `SettingsScreen(model)` 路由 + 通知区 + 4 条既有断言更新 | T4 | §8.0、§8.7、§14 |
| 7 | 外观分类 | 主题（浅色禁用）/字体/缩放/语言 | T4、T6 | §8.1 |
| 8 | 终端分类 | `copyOnSelect` 接入 JediTerm；Shift+Insert A/B 判定 | T4、T6 | §8.2、§9.4、§15 R7 |
| 9 | 连接分类 | 启动时连接 + `ADDRESS_MASK` 掩码 + 冲突策略 | T4、T6 | §8.3、§9.2、§9.3 |
| 10 | 导入导出 | `OpenSshConfigParser` + `ConnectionTransfer` + 存储分类 UI | T2 | §8.4、§10、§13.4、§13.5 |
| 11 | 更新检查 | `UpdateChecker`（注入 fetch）+ 更新分类 UI | T2、T4 | §8.5、§11、§13.6 |
| 12 | 关于分类 | 版本展示 + 5 条致谢 + 反馈入口 | T1、T4 | §8.6、§12 |
| 13 | 收尾 | 全量回归 + 真机三档缩放走查 + 证据固化 + 实施报告 | 全部 | §13.9、§17、§15 R1 |

---

## 4. 分层实现说明

以下按架构层说明关键机制与设计理由（「为什么」在设计里，代码本体在计划里）。

### 4.1 数据与收敛层 — `commonMain/settings/AppSettings.kt`（Task 2）

- 不可变 `@Serializable data class`，**每个字段都带默认值**——双重目的：旧文件缺字段可兼容；`coerceInputValues` 收敛未知枚举值的前提（收敛目标就是默认值）。
- `Theme` **故意不含 `LIGHT`**：持久化层不得持有不可能的值。UI 上「浅色」渲染为禁用项 + 「未实现」标注（决策 D2）。
- `sanitized()` 的契约是**绝不抛异常、绝不产生坏布局**：`uiScale` 就近收敛到 `1.0/1.25/1.5` 三档；字体/调色板/语言回默认；`updateRepo` 须匹配 `^[\w.-]+/[\w.-]+$`；`feedbackUrl` 仅接受 http(s)；每次收敛记一条中文 warning，由 UI 横幅如实告知。幂等性由 `sanitizeIsIdempotent` 测试守住（第二次收敛必须零警告）。
- 「只有一个真选项」的字体/语言/调色板照实渲染单值（`StaticValueRow`），不造假备选（决策 D7、不造数红线）。

### 4.2 持久层 — `SettingsRepository` + `FileSettingsRepository`（Task 3）

- `SettingsLoad` 密封接口：`Ok(settings, warnings)` / `Recovered(settings, quarantinePath, cause)`；`load()` **永不抛**，`save()` 失败抛 `SettingsWriteException`。
- Json 四开关缺一不可：`ignoreUnknownKeys`（前向兼容）、`encodeDefaults`（默认值也写盘，否则改回默认时字段消失）、`coerceInputValues`（未知枚举值收敛为默认而非抛异常）、`prettyPrint`。
- **写入走原子替换**：先写同目录 `.tmp` 再 `Files.move(REPLACE_EXISTING)`——避免写到一半崩溃留下半截 JSON（那正是制造「不可读文件」的最可能路径）。
- **读取失败即隔离**：把坏文件改名留存为 `settings.json.corrupt-<epochMillis>`，回落默认值并返回 `Recovered`；隔离失败则抛异常中止——宁可操作失败，也不丢用户配置线索。`save()` 前先试读现有文件，不可读先隔离再写，杜绝覆盖销毁。

### 4.3 状态层 — `app/SettingsModel.kt`（Task 4）

- 两个可关闭的通知态：`loadNotice`（文件被隔离 / 值被收敛）、`saveError`（最近一次写盘失败）。
- `update { copy }` = 改内存 + **立即 save**：无脏态、无保存按钮（决策 D5「即时生效」）。
- 两条不可动摇的行为：**写失败不回滚内存态**（回滚会让用户以为白改了一次）；**凡收敛/隔离/失败都告知**，不静默。

### 4.4 生效点接线（Task 5 / 8 / 9）

- **应用级**：`App()` 读 `theme` 决定 `darkTheme`（`FOLLOW_SYSTEM` 恒解析为深色——`isSystemInDarkTheme() || true`，保留 when 分支待浅色落地只改一处）；`uiScale` 经抽出的纯函数 `scaledDensity(base, scale)` **只乘 `density` 不乘 `fontScale`**（否则字号被缩放两次）。抽纯函数而非测 Composable，是规避 R9（测试窗口 density 断言不可靠）的对策。
- **屏级·隐藏地址**：`ServersScreen` 服务器卡与 `ConnectDialog` 只读端口替换为 `ADDRESS_MASK = "•••.•••.•••.•••"`——固定串、不随真实长度变化（否则长度本身就是信息）。**编辑对话框明确不遮**（遮了没法编辑），且该局限写进设置行 desc 提前告知。终端状态栏显示的是服务器名，无地址可遮，不动它（R11）。
- **屏级·启动自动连接**：`autoConnectIfConfigured()` 只对 `StoredAuth.Key` 且 id 仍存在的服务器跳过对话框直连；密码认证不自动连（本项目**故意不落盘密码**，这条不可破）；目标不存在则不连接、不弹窗、不打扰。
- **组件级**：`copyOnSelect` 接入 `BareZenTerminalSettings`（javap 已核实 JediTerm 3.73 提供且被 `TerminalPanel` 消费）；`shiftInsertPaste` 经核实**无 INSERT 键处理、`emulateX11CopyPaste()` 是死方法**——Task 8 给出 A/B 分支：`ActionMap` 粘贴动作可达则自实现 Swing 键绑定，否则**删掉该行设置**，绝不渲染点了没反应的开关（R7 通用规则）。
- **延迟生效**：`conflictPolicy`（待 P4 SFTP）、`sudoAutofill`（待 P3 凭据库）只存不生效，desc 末尾标注「待 X 接入后生效」。

### 4.5 UI 层 — 行控件 + 6 分类 + 路由（Task 6 / 7–12）

- 4 种共享行控件（`ToggleRow` / `ChoiceRow` / `StaticValueRow` / `ActionRow`）+ `SettingsSectionScaffold` + `SettingsNotice`；样式照设计包 `.setting-row`——**分隔线行**（16/0 内边距 + 末行除外的底边线），不是卡片，无底色无圆角。
- `ChoiceRow` 在 Task 7 回头加 `disabledIndices: Set<Int>` 参数以支持**逐项禁用**（外观分类传 `setOf(2)` 禁掉「浅色（未实现）」）。
- `SettingsScreen` 签名从无参改为 `SettingsScreen(model: AppModel)`：左列 8 分类导航不动，右列 `when (selected)` 路由到 6 个真 section + 2 个诚实占位（文案「智能助手属 M5，尚未接入。」「凭据库属 M3，尚未接入。」）。
- 顶部通知区仅在 `loadNotice` / `saveError` 非空时出现，各带「知道了」关闭按钮。
- Task 6 为让本任务独立编译，先为未实现的 4 个 section 落**过渡空壳**（提交信息里说明），Task 7–12 逐个替换真身。

### 4.6 功能层

- **`ConnectionTransfer`**（Task 10）：导出 JSON 用不含 `id` 的中间 DTO（导入重新生成 UUID，避免跨机器冲突）；**默认脱敏**——`auth` 只写 `{"type":"key"}` 不写 `keyPath`，勾选「包含私钥路径」才含（逐次确认而非常驻开关，防遗忘泄露）；CSV 以 UTF-8 BOM（`EF BB BF`）开头、表头 `名称,地址,端口,用户名,标签,认证方式`、标签用 `;` 连接、含 `,`/`"`/换行的字段双引号包裹转义；`merge` 按 `(host 小写, port, user)` 去重，跳过项按原因聚合计数。
- **`OpenSshConfigParser`**（Task 10）：逐行状态机。`Host a b c` 每个别名各生成一台；缺 `HostName` 用第一个别名（OpenSSH 语义）；缺 `User` 跳过计入 `NoUser`；`Port` 缺省 22；`IdentityFile` 决定 `Key`/`Password`；通配 Host 跳过计入 `WildcardHost`；`Include` 跳过并置 `includeSkipped` 如实告知；注释/空行/未知关键字忽略。
- **`UpdateChecker`**（Task 11）：构造注入 `getJson: suspend (String) -> String`——生产用 JDK 自带 `HttpClient`（`User-Agent: BareZen-SSH` 必填，缺了 403；超时 10s/20s），测试注入假 fetch **完全零网络**。`STABLE` 取首个非 draft 非 prerelease；版本比较去 `v` 前缀按数字段；非 semver tag 返回 `Uncomparable`，**不谎报有新版本也不谎报已最新**；`updateRepo` 为空时返回 `NotConfigured` 且 fetch 零调用（测试硬断言）。
- **`BuildInfo`**（Task 1）：`VERSION = "0.1.0"` 常量；`BuildInfoTest` 从 `user.dir` 向上逐级找 `gradle.properties`，断言 `barezen.version` 与常量相等——任一处被改而另一处没跟上就红（R8 对策）。同时修掉 `desktopApp/build.gradle.kts` 里不实的硬编码 `packageVersion = "1.0.0"`。

---

## 5. 对应关系总表（设计 ↔ 计划 ↔ 测试）

| 设计章节 | 内容 | 计划任务 | 主要测试（新增） |
|---|---|---|---|
| §5、§13.1 | 数据模型 + 收敛 | Task 2 | `AppSettingsTest`（14 例） |
| §4.2、§6、§13.2 | 持久化 + 隔离 + 原子写 | Task 3 | `FileSettingsRepositoryTest`（9 例） |
| §4.2、§7、§13.3 | 状态 + 即时落盘 + 失败保留 | Task 4 | `SettingsModelTest`（6 例） |
| §9.1、§15 R2 | 主题/缩放接线 | Task 5 | `UiScaleTest`（2 例） |
| §8.0、§8.7、§14 | 行控件、路由、通知区、4 条既有断言更新 | Task 6 | `SettingsScreenTest`（起始 3 例） |
| §8.1 | 外观分类 | Task 7 | `SettingsScreenTest` 外观 3 例 |
| §8.2、§9.4、§15 R7 | 终端分类 + JediTerm 判定 | Task 8 | 终端 3 例 + 真机验证 |
| §8.3、§9.2、§9.3 | 连接分类 + 掩码 + 自动连接 | Task 9 | 连接 2 例 + `ServersScreenTest` 掩码 1 例 |
| §8.4、§10、§13.4、§13.5 | 导入导出 + 存储分类 | Task 10 | `OpenSshConfigParserTest`（4 例）+ `ConnectionTransferTest`（6 例） |
| §8.5、§11、§13.6 | 更新检查 | Task 11 | `UpdateCheckerTest`（9 例） |
| §8.6、§12、§13.7 | 关于分类 + 版本守卫 | Task 12 | 关于 2 例 + `BuildInfoTest`（1 例） |
| §13.9、§17、§15 R1 | 回归 + 真机走查 + 证据固化 | Task 13 | 全量 `:desktopApp:compileKotlin` + `:shared:jvmTest --rerun` |

测试规模：新增 8 个测试类、约 60 条用例；另须按设计 §14 更新 `PlaceholderScreensTest`（3 条）与 `AppShellTest`（1 条）的既有断言——这些是 P1 令其不再成立的**预期变更**，不是回归。

---

## 6. 执行纪律（两份文档共同强调）

1. **严格 TDD 红绿循环**：每个任务先落测试跑红（编译失败也算红）→ 实现 → 跑绿 → 提交；RED/GREEN 证据写入实施报告。
2. **环境命令模板**：`--no-daemon` 必加；`--rerun` 必加（否则 `UP-TO-DATE`，任务没执行，不构成证据）；跑测试前清 `~/.cache/fontconfig/`；禁 `pkill -f`，只按记录的 PID kill；外网走代理。
3. **证据固化**：`--rerun` 会覆盖 `shared/build/test-results/jvmTest/` 下同名 XML，每任务做完必须把 XML 另存到 `.superpowers/sdd/2026-10-01-settings-completion/evidence/<taskN>/`。
4. **不造数红线**：没有真源的数据一律不渲染数字；只有一个真选项的行照实显示单值；底层做不到的设置**删行**，绝不渲染无效开关。
5. **提交纪律**：每条提交信息先给用户过目；工作树保持可编译、测试全绿；不用 `--amend`/`rebase` 篡改已提交内容。
6. **收尾交付**：Task 13 产出实施报告（`.superpowers/sdd/2026-10-01-settings-completion/report.md`），含每任务红绿证据、Shift+Insert 判定结论、真机走查逐项结果（不通过就写不通过，不粉饰）、未做到/跳过项清单；经用户 review 后才进入 P2（主机指标）。

---

## 7. 关系示意

```
设计（964 行，做什么）
  §3 决策 D1–D8 ──────────► 约束所有任务的取值
  §4 架构/签名 ───────────► Task 2–6 的 Interfaces 契约
  §5 数据模型  ───────────► Task 2
  §6 持久化    ───────────► Task 3
  §7 错误处理  ───────────► Task 3 + Task 4（隔离/原子 + 失败保留）
  §8 UI 规格   ───────────► Task 6（控件）+ Task 7–12（六分类）
  §9 生效点接线 ──────────► Task 5（应用级）/ Task 8（组件级）/ Task 9（屏级）
  §10 导入导出 ───────────► Task 10
  §11 更新检查 ───────────► Task 11
  §12 关于     ───────────► Task 1（版本源）+ Task 12（关于页）
  §13 测试规格 ───────────► 每个任务的 Step 1（先写失败测试）
  §14 既有断言 ───────────► Task 6 Step 6
  §15 风险     ──────────► 分散为各任务的具体步骤（R1→T13，R2→T5，R3→T2/3，R7→T8）
  §17 验收清单 ───────────► Task 13（逐项执行 + 实施报告）
```

一句话总结：**设计冻结「什么是对的」，计划把「对的」翻译成 13 个可独立红绿验证的任务**；执行的每一步要么在实现设计的某条契约，要么在用测试证明该契约成立。
