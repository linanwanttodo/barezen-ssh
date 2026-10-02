# 架构（ARCHITECTURE）

更新：2026-10-02。本文回答「系统怎么组织、为什么这样组织」。

## 1. 技术栈与形态

| 项 | 值 |
|---|---|
| 语言 | Kotlin 2.4.20 为主 + Java 21（解析器/JNA/库包装，见 4 节混编边界） |
| UI | Compose Multiplatform 1.12.1（Desktop，JVM-only 目标） |
| 终端 | JediTerm 3.73（swing 面板嵌入，`TerminalTtyConnector` 桥接） |
| SSH | sshj 0.40.0（`JvmSshClient` 封装，TOFU 主机密钥校验） |
| 序列化 | kotlinx-serialization-json（设置/服务器配置持久化） |
| 构建 | Gradle 9.5.1 wrapper，KMP `jvm()` 单目标 |

产品形态：单窗口桌面 SSH 客户端，暗色优先（DBX 设计令牌），中文界面。

## 2. 模块与分层

```
desktopApp/   入口壳：main.kt + Desktop 明暗/缩放接线，无业务逻辑
shared/
  commonMain  跨平台纯逻辑：Ssh 接口、AppModel、AppSettings、Server、令牌
  jvmMain     桌面实现：全部 Compose UI、JvmSshClient、文件仓库、Java 解析器
  commonTest  跨平台单测（fake 注入，零 IO）
  jvmTest     JVM 单测 + Compose UI 测试（skiko 软渲染，headless 可跑）
```

依赖方向（单向，禁止逆向）：

```
ui/screens, ui/shell  ->  app(AppModel)  ->  ssh / servers / settings  ->  commonMain 基础类型
        ^ Java 解析器位于 ssh.metrics 包，仅被 app 层协调器调用
```

- `ui/` 只消费状态与 data class，不持有连接、不解析文本。
- `app/` 是唯一的状态协调层：连接生命周期、路由（`Destination`）、设置聚合（`SettingsModel`）。
- `ssh/servers/settings` 三个数据包互相不依赖，全部通过 `app` 层组合。

## 3. 关键数据流

### 3.1 连接生命周期

```
UI（ServersScreen/ConnectDialog）
  -> AppModel.connect(request)            幂等防重入
  -> SshClient.connect()                  JvmSshClient：sshj SSHClient，TOFU 验证
  -> ConnectionState 状态机               Disconnected/Connecting/Connected/Failed
  -> Connected 后 Dashboard/终端复用同一 SshSession
```

- 状态机在 `ssh/Ssh.kt`（commonMain），UI 通过组合订阅。
- 主机密钥 TOFU（`TofuHostKeyVerifier`）：首次信任并落盘，变更即拒绝。

### 3.2 设置与持久化

- `AppSettings`（commonMain，纯数据 + `sanitized()` 脱敏契约）。
- `FileSettingsRepository`（jvmMain）：原子写（`.tmp` + `Files.move`）、损坏隔离（`*.corrupt-<epochMillis>`）、`coerceInputValues` 容错。
- `ServerRepository` 同构：`FileServerRepository`，导入导出时走 `ConnectionTransfer`（默认脱敏，`ADDRESS_MASK` 固定串掩码）。

### 3.3 SSH 通道扩展（P2 起）

`SshSession` 接口从三方法（`pingMs/startShell/close`）扩展出：

- `exec(command, timeoutMs): ExecResult`——指标采集、探活；每条命令独立 session channel，不与 shell 抢 PTY。
- `newSftp()`（P4）——文件传输。
- 并发约束：sshj `SSHClient` 非线程安全，`JvmSshSession` 内部对通道操作串行化（同一把锁），上层轮询间隔 >=5s 不构成瓶颈。

### 3.4 指标管线（P2）

```
MetricsCollector（Kotlin，轮询协程，StateFlow<MetricsSnapshot?>）
  -> SshSession.exec("cat /proc/...; df -P ...")
  -> Java 解析器（jvmMain/java/com/barezen/ssh/ssh/metrics/）：String -> 不可变 data class
  -> DashboardScreen 只消费 Snapshot 渲染，不碰文本
```

红线：无连接时展示「—」占位，不造数。

## 4. 混编语言边界（契约全文见 docs/superpowers/2026-10-02-dev-contract.md）

| 归 Kotlin | 归 Java |
|---|---|
| ui/ 全部 Compose | 文本/协议解析（/proc、df 输出） |
| app/ 状态与协调（协程） | JNA 系统绑定（P3 钥匙串） |
| 跨平台接口与 fake | 第三方 Java 库包装（sshj SFTP 封装） |

- Java 源目录：`shared/src/jvmMain/java/com/barezen/ssh/...`，Gradle 自动编译。
- 语言边界即模块边界：Java 不 import Compose；UI 不解析协议文本——解析器产出 data class，UI 只消费。
- 包名 `com.barezen.ssh`（2026-10-02 从 `barezen_ssh` 重构）；Compose 资源生成包显式固定为 `com.barezen.ssh.generated.resources`（shared/build.gradle.kts `packageOfResClass`）。

## 5. 主题系统

- 令牌集中在 `ui/theme/Color.kt`（DBX 对齐：chrome/content/gutter 三层表面 + 中性主色 + 四语义色带 12–16% alpha 背景），`Theme.kt` 装配 dark/light 两套 colorScheme，形状 6/4px。
- 唯一允许改动令牌值的任务是主题底座（R0）；功能任务只消费 `MaterialTheme.colorScheme`，禁止组件内硬编码颜色。
- 亮色主题经由设置外观分类的明暗选择生效（`BareZenTheme(darkTheme)`）。

## 6. 测试策略

- 跨平台逻辑（AppModel/Settings/过滤）：commonTest，fake 注入（如 `CapturingSshClient`）。
- JVM 实现（文件仓库、解析器、UpdateChecker）：jvmTest，临时目录 + 穷举单测。
- Compose UI：jvmTest 走 skiko 软渲染（headless），选择器优先 `testTag`，避免多节点文本歧义。
- 基线：28 套件 / 149 用例 / 0 失败；任何提交前门禁必须全绿（命令见 docs/DEVELOPMENT.md）。

## 7. 文档索引

| 文档 | 内容 |
|---|---|
| docs/DEVELOPMENT.md | 环境、构建门禁、TDD、提交规范、沙箱排障 |
| docs/superpowers/2026-10-02-dev-contract.md | 子代理共同契约（硬约束） |
| docs/superpowers/2026-10-02-comprehensive-audit-and-plan.md | 全量审计 + 语言适配表 + P2-P6 规划 |
| docs/superpowers/2026-10-02-next-phase-roadmap.md | 路线图与 DBX 令牌摘录 |
| docs/superpowers/specs/ 2026-09-24 设计文档 | 产品/界面设计源头 |
| PRODUCT.md | 产品定位 + 混编约定 |
