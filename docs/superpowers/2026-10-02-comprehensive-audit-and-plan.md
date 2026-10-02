# 全面审计、分析与规划（混编决策落地版）

日期：2026-10-02
前置决策（用户拍板）：**Java 与 Kotlin 混编——Java 更适合的地方用 Java，Kotlin 更适合的地方用 Kotlin**。
本文档回答三件事：代码现状审计（是什么样）、语言与架构分析（该怎么分）、执行规划（接下来怎么走）。

---

## 第一部分 代码审计

### 1.1 规模与分层

| 层 | 行数 | 说明 |
|---|---|---|
| commonMain（共享逻辑/接口） | 710 | AppModel、Ssh 抽象、Settings 抽象、UiConstants |
| jvmMain（桌面实现/Compose UI） | 3583 | 全部 UI、JvmSshClient、文件/设置仓库 |
| commonTest + jvmTest | 489 + 1584 = 2073 | 28 个测试套件，149 用例，0 失败 |

测试/源码比约 0.48:1，P1 之后核心逻辑层覆盖良好；缺口集中在 UI 层与未实现的空壳功能（见 1.3）。

### 1.2 最大文件（潜在拆分点）

| 文件 | 行数 | 评估 |
|---|---|---|
| ServersScreen.kt | 596 | 偏大，含列表/卡片/连接对话框/编辑表单四块职责，P3 动卡凭据时顺势拆分 |
| TerminalScreen.kt | 264 | 尚可，jediterm 封装边界清晰 |
| AppShell.kt | 249 | 布局骨架，P5 多标签时必然重构 |
| SettingsStorageSection.kt | 207 | 含导入导出逻辑，可接受 |
| FilesScreen.kt | 149 | 空壳，P4 重写 |

### 1.3 空壳清单（功能缺口，即 P2–P6 范围）

| 界面 | 占位现状 | 对应阶段 |
|---|---|---|
| DashboardScreen | 4 个指标值恒为"—"（CPU/内存/磁盘/网络）+ 2 个图表占位 | P2 主机监控 |
| PortsScreen | "新建转发"按钮禁用 + 转发规则占位卡片；凭据仅存明文路径 | P3 端口转发 + 凭据/钥匙串 |
| FilesScreen | SFTP 文件浏览占位 | P4 SFTP |
| 会话模型 | 单会话，无多标签/分屏 | P5 |
| AI 运维侧栏 | 无 | P6（待决策） |

### 1.4 关键架构缺口（阻断项）

**SshSession 能力不足**：当前接口只有 `pingMs()` / `startShell()` / `close()` 三个方法（`Ssh.kt`），底层 sshj 0.40.0 的 `SSHClient` 具备 exec channel 与 SFTP 能力但未暴露。P2/P3/P4 全部依赖此扩展：

- `exec(command: String, timeoutMs: Long): ExecResult`（exitCode + stdout + stderr）→ P2 指标采集
- `newSftp(): SftpFs`（列目录/读文件/写文件/删改）→ P4
- 本地/远程/动态转发 → P3

**SSHD 并发安全性**：sshj 的 `SSHClient` 非线程安全，多 channel 并发需要在 `JvmSshClient` 内加锁或做单线程调度，设计阶段必须定案。

### 1.5 技术债

1. 亮色主题分支存在但被禁用（P1 T7 的既定决策），DBX 改版时一并落地。
2. 测试依赖沙箱 init 脚本（`-Duser.home` / headless），脚本在项目外 `/home/lin/tmp/`，随 tmpfs 丢失需重建（配方已入记忆）。
3. `UiConstants` 中部分颜色硬编码于组件内，DBX 改版要求全部收敛到主题令牌。

---

## 第二部分 分析

### 2.1 语言适配表（混编决策落地）

原则：**UI 与协程状态层归 Kotlin；协议解析、文件系统包装、JNA 系统调用归 Java**。理由三条：Compose 仅支持 Kotlin；sshj/JNA 本身是 Java 库，Java 侧调用无 platform-type 摩擦、可写 checked exception 语义清晰的解析器；纯解析逻辑在 Java 里更易做穷举单测。

| 模块 | 现状语言 | 目标语言 | 理由 |
|---|---|---|---|
| `ui/`（全部界面） | Kotlin | **Kotlin（不变）** | Compose 编译器仅支持 Kotlin |
| `app/`（AppModel、会话状态、路由） | Kotlin | **Kotlin（不变）** | 协程状态机，Kotlin 惯用域 |
| settings/repo 层（P1 产物） | Kotlin | **Kotlin（不变）** | 已稳定，无迁移价值 |
| P2 指标解析（/proc/meminfo、df、/proc/net/dev 解析器） | 无 | **Java** | 纯文本解析、正则 + 穷举单测，无协程依赖 |
| P2 采集协调（轮询调度、StateFlow） | 无 | Kotlin | 协程调度是 Kotlin 域 |
| P3 凭据/系统钥匙串（libsecret/Win cred/macOS JNA 绑定） | 无 | **Java** | JNA 官方示例即 Java，struct/callback 映射最直接 |
| P3 转发规则模型 + UI | 无 | Kotlin | 同 app 层 |
| P4 SFTP 文件列表/传输包装（sshj SFTPClient 封装、路径处理） | 无 | **Java** | sshj 是 Java API，Java 侧零类型摩擦；`SftpFs` 实现类 |
| P4 文件列表 UI/传输状态 | 无 | Kotlin | 同 ui/app 层 |
| P5 多标签/分屏（会话注册表） | 无 | Kotlin | 纯状态层 |
| 未来纯逻辑工具类 | — | 就近原则 | 与所在包语言一致，避免无意义跳转 |

互操作事实（已核实）：Kotlin/JVM 与 Java 同源目录互调无缝；KMP jvmMain 下 Java 源放 `jvmMain/java`，Gradle 自动参与编译；唯一需要对齐的是 `jvmTarget = 21`（已全局一致）。无新增构建配置需求。

### 2.2 SshSession 扩展设计要点

1. **接口在 commonMain、实现在 jvmMain**（沿用 `pingMs` 模式），测试用 fake 实现注入——P1 的 CapturingSshClient 已验证此模式可行。
2. **ExecResult 用 data class**（exitCode/stdout/stderr/durationMs），Java 解析器输入输出均为不可变值。
3. **指标采集走 exec channel 而非解析 shell 输出**：shell 会被用户交互污染；每指标一条轻量命令（`cat /proc/loadavg` 等）或一条聚合脚本，设计时权衡延迟与连接占用。
4. **并发策略**：`JvmSshClient` 内部 `Mutex` 串行化所有 channel 操作（与记忆中跨客户端刷新去重的教训一致），P2 轮询间隔 ≥5s 时不构成瓶颈。

### 2.3 DBX 设计令牌 → Compose 映射

来自 `apps/desktop/src/styles/tokens.css` 的权威值（已从 GitHub raw 核实）：

| DBX 令牌 | 值 | Compose 落点 |
|---|---|---|
| bg（chrome 层） | rgb(19 20 22) | `BareZenTheme` 背景色 |
| card（content 层） | rgb(27 27 30) | surface / 卡片容器 |
| sidebar（gutter 层） | rgb(25 25 28) | 侧栏 AppShell 左栏 |
| primary（中性主色） | rgb(208 208 214) | 按钮/选中态前景 |
| border | rgb(110 110 114 / 28%) | 分隔线、卡片描边 |
| destructive/success/warning/info | 4 个语义色，背景统一 12–16% alpha | M3 error/tertiary 映射表 |
| 圆角 | 4px 控件 / 6px 容器 | MaterialTheme.shapes |
| 字体 | Geist Variable + PingFang SC / Fira Code | 界面字体 + 终端字体分离 |

改版策略：先做**主题底座**（一次 PR：Token 集 + BareZenTheme 暗色分支替换 + 亮色分支启用），后续 P2–P4 的 UI 直接消费令牌，避免边做功能边改色。

### 2.4 VPS 实测方案

存在一台可登录的 Linux VPS，但**测试凭据尚未提供**。建议：凭据写入本机 `~/.ssh/config` 或环境变量（不进仓库、不进文档），集成测试通过环境变量探测——有则跑真机冒烟，无则自动跳过，保证 CI 与沙箱零凭据仍全绿。

---

## 第三部分 规划

### 3.1 混编编码约定（写入 PRODUCT.md 的规则）

1. UI（Compose）、协程状态、路由 → Kotlin；文本/协议解析、JNA 绑定、第三方 Java 库包装 → Java。
2. Java 文件一律放 `jvmMain/java/` 对应包；跨平台接口与 fake 仍在 commonMain Kotlin。
3. 语言边界即模块边界：Java 类不 import Compose，Kotlin UI 不直接解析协议文本——解析器产出 data class，UI 只消费。
4. 单测跟着解析器走：Java 解析器必须有穷举单测（合法/截断/非法输入）。
5. 不为混编而混编：现有 Kotlin 代码不迁移，新代码按此表归置。

### 3.2 执行顺序（每项走 spec → plan → 子代理 TDD 全流程）

| 阶段 | 内容 | 语言分布 | 前置 |
|---|---|---|---|
| R0 | 主题底座：DBX 令牌 + 暗色替换 + 亮色启用 + 颜色收敛 UiConstants | Kotlin | 无（可与 P2 并行） |
| P2 | 主机监控：SshSession.exec + 指标解析器（Java）+ 轮询协调 + Dashboard 真数据 + 图表 | Java 解析 / Kotlin 其余 | SshSession 扩展 |
| P3 | 端口转发 + 凭据：转发规则模型/UI + 系统钥匙串（Java JNA）+ ServersScreen 拆分 | Java JNA / Kotlin 其余 | P2 完成后 |
| P4 | SFTP：SftpFs（Java）+ 文件浏览/上传/下载 UI + 传输进度 | Java fs / Kotlin 其余 | 依赖 exec/会话并发策略定案 |
| P5 | 多标签/分屏：会话注册表重构 + AppShell 改造 | Kotlin | 建议在 P4 后（会话数量倍增前稳定单会话语义） |
| P6 | AI 运维侧栏 | Kotlin | 待用户决策 |

每阶段收尾：全量测试门禁（当前基线 28 套件/149 用例）+ 真机走查（VPS 冒烟）+ 提交。

### 3.3 待确认决策点（阻塞项已标注）

1. **VPS 测试凭据**：host/port/user/认证方式（密码或密钥路径）——P2 集成冒烟需要，不提供则只做解析器穷举单测。
2. **UI 基线**：确认按 DBX 令牌整体替换（R0 一次到位），还是渐进式？建议一次到位（改动集中、避免双体系并存）。
3. **优先级**：默认 R0 → P2 → P3 → P4 → P5，是否调整？
4. **P6 AI 助手**：做或不做？若做，需另议审批注入的安全边界。

### 3.4 里程碑

- M1（R0+P2）：Dashboard 从"—"变成真数据，应用整体呈现 DBX 视觉。
- M2（P3）：端口转发可用 + 凭据入系统钥匙串（安全可见原则落地）。
- M3（P4）：SFTP 文件管理可用，达到"不打开其他 SSH 工具"的产品目的主体。
- M4（P5+P6）：多会话并行 +（可选）AI 侧栏，v1.0 形态。
