# 架构（ARCHITECTURE）

更新：2026-10-02。回答「系统怎么组织、为什么这样组织」。约束与操作分别见 CONVENTIONS.md / DEVELOPMENT.md。

## 1. 技术栈与形态

| 项 | 值 |
|---|---|
| 语言 | Kotlin 2.4.20 为主 + Java 21（解析/JNA/库包装，见 4 节） |
| UI | Compose Multiplatform 1.12.1（Desktop，JVM-only 目标） |
| 终端 | JediTerm 3.73（swing 面板嵌入，`TerminalTtyConnector` 桥接） |
| SSH | sshj 0.40.0（`JvmSshClient` 封装，TOFU 主机密钥） |
| 凭据 | 平台钥匙串（Linux secret-tool / macOS security / Windows advapi32 bridge） |
| 序列化 | kotlinx-serialization-json（设置/服务器/转发规则持久化） |
| 构建 | Gradle wrapper 9.5.1，KMP `jvm()` 单目标 |

## 2. 模块与分层

```
desktopApp/   入口壳：main.kt + Desktop 明暗/缩放接线，无业务逻辑
shared/
  commonMain  跨平台纯逻辑：Ssh 接口、AppModel、AppSettings、Server、主题令牌
  jvmMain     桌面实现：全部 Compose UI、JvmSshClient、文件仓库、Java 纯逻辑、凭据
  commonTest  跨平台单测（fake 注入，零 IO）
  jvmTest     JVM 单测 + Compose UI 测试（skiko 软渲染，headless 可跑）
```

依赖方向（单向，禁止逆向）：

```
ui/screens, ui/shell(*Host 接线层)  ->  app(AppModel)  ->  ssh / servers / settings / credentials
        ssh.jvmMain 子包：metrics(指标) sftp(文件) forward(转发)  <- Java 纯逻辑在 jvmMain/java
```

- `ui/` 只消费状态与 data class，不持有连接、不解析文本。
- `ui/shell/` 的 `*Host` 是**接线层范式**：连接建立时创建屏幕模型（`DashboardHost`/`FilesHost`/`PortsHost`），断开时释放（停轮询/取消传输/关隧道）。新屏幕照此办理。
- `app/` 是状态协调层：连接状态机（`ConnectionState`：Disconnected/Connecting/Connected/Failed，幂等防重入）、路由（`Destination`）、设置聚合。
- 数据包（ssh/servers/settings/credentials）互相不依赖，经 app 层组合。

## 3. 关键数据流

### 3.1 连接生命周期

```
ServersScreen/ConnectDialog -> AppModel.requestConnect -> ConnectDialog 确认
  -> AppModel.startConnect（防重入）-> SshClient.connect（JvmSshClient，TOFU 验证）
  -> Connected(shellSession)  -> *Host 层据此创建各屏模型
断开/失败：shellSession.close()，各 Host 经 LaunchedEffect(connected) 复位
```

### 3.2 SSH 通道能力（Ssh.kt，commonMain 接口）

`SshSession` 五能力：`pingMs` / `exec(command, timeoutMs): ExecResult` / `startShell` / `newSftp(): SftpFs` / `startForward(spec): ForwardTunnel`。

- **exec**：每条命令独立 session channel；有界 join 超时先关通道强制 EOF 再读流（防读流阻塞）；超时/异常 exitCode=null（语义：退出状态不可信）。
- **并发**：sshj `SSHClient` 非线程安全——`JvmSshSession.execLock` 串行化 exec/pingMs；shell 通道独立。转发 accept 循环与远程转发 pump 在自有守护线程。
- **SftpFs**：`list/mkdir/delete/rename` + 流式 `download(remotePath, onChunk)` / `upload(remotePath, size, nextChunk)`（32/64KiB 分块，大文件不驻留内存）。**`nextChunk` 必须以 `null` 表示结束；返回空数组属契约违例，实现须快速失败而非 `continue`**——曾因此空转忙等（真机表现为上传假死），详见 STATUS 1.11。
- **转发**：LOCAL 经 sshj `LocalPortForwarder`（127.0.0.1 绑定）；REMOTE 经 `RemotePortForwarder.bind` + 自写双向对拷线程；动态（SOCKS5）sshj 0.40 无实现，未提供。

### 3.3 指标管线

```
MetricsCollector（轮询协程，默认 5s，StateFlow<MetricsSnapshot?>）
  -> SshSession.exec(cat /proc/...; df -P)
  -> Java 解析器（jvmMain/java/.../ssh/metrics/）：String -> 不可变 data class
  -> DashboardScreen 只消费 Snapshot
```

命令失败（exitCode!=0/null）整体置 null 并继续轮询；单项解析失败仅该字段置 null。红线：无连接显示「—」，不造数。

### 3.4 凭据（能力已建，接入待办 T-1）

`CredentialStore`（jvmMain `credentials/`）：`save/load/delete/isAvailable`，id 约定 `server/<serverId>/password|keypass`。平台实现：GnomeKeyringStore（secret-tool 子进程，进程注入可测）、MacKeychainStore（security 子进程）、WindowsCredStore（bridge 注入，真实调用待 JNA 依赖）、InMemory 降级。**已接入连接流程**（T-1）：commonMain 侧为 `CredentialResolver` 端口（`NoopCredentialResolver` 默认＝不落盘），jvmMain 侧为 `KeychainCredentialResolver`（`platformDefault()` 工厂；keyPath 恒取自 Server.auth，钥匙串只供口令；`isAvailable` 区分真钥匙串与内存降级）。`AppModel.removeServer` 同步 `forget` 清理。**壳层尚未注入该工厂**（仍走 Noop），待 T-2 真机验证时接上。

### 3.5 持久化契约（三处同构）

设置 `FileSettingsRepository`、服务器 `FileServerRepository`、转发规则 `ForwardRuleStore`：原子写（`.tmp` + `Files.move`）、损坏隔离（`*.corrupt-<epochMillis>`）、容错。新增文件持久化照抄此模式。

注：`coerceInputValues` 仅 `FileSettingsRepository` 与 `ForwardRuleStore` 具备，`FileServerRepository` 当前只有 `ignoreUnknownKeys`（三处中唯一例外，见 STATUS 技术债）；补齐前 Server 的旧 JSON 遇到未知判别值会整体读空并隔离。

## 4. 混编语言边界

| 归 Kotlin | 归 Java（`shared/src/jvmMain/java/`） |
|---|---|
| ui/ 全部 Compose | 指标解析 5 类：LoadAvg/MemInfo/Uptime/DiskUsage/CpuUsage |
| app/ 状态协调（协程） | SFTP 纯逻辑 5 类：SftpPaths/EntrySorter/FileSizeFormatter/TransferThrottler/LocalChunkReader |
| 跨平台接口与 fake | 凭据 5 类：GnomeKeyring/MacKeychain/WindowsCred/CommandRunner/CredentialStoreException |

边界规则：Java 不 import Compose；UI 不解析文本；解析器产出不可变 data class。当前 Java 共 15 文件约 700 行，全部有配套穷举单测。

## 5. 主题系统

- 令牌 `ui/theme/Color.kt`（DBX 对齐）：暗色 chrome `0xFF131416` / content `0xFF1B1B1E` / sidebar `0xFF19191C`、中性主色 `0xFFD0D0D6`、语义色带 12-16% alpha 背景；亮色板同色相派生（`BareZenLight*`）。
- `Theme.kt` 装配 dark/light 两套 colorScheme；`App.kt` 按 `Theme.DARK/LIGHT/FOLLOW_SYSTEM` 解析；形状 6/4px。
- 令牌 val 名称是稳定 API：只许改值，不许改名；组件只消费 `MaterialTheme.colorScheme`。

## 6. 测试策略

- 跨平台逻辑：commonTest，fake 注入（`FakeSshClient/FakeSshSession`（支持 exec 计数与 sftpFactory 注入）/`CapturingSshClient`）。
- JVM 实现：jvmTest，临时目录 + 穷举单测（Java 解析器 35+ 例）。
- Compose UI：jvmTest skiko 软渲染；`testTag` 优先；`*Host` 全链路用 fake + `waitUntil` 验证真值上屏。
- 基线：**367 用例 / 0 失败**（jvmTest）+ commonTest 若干。门禁见 CONVENTIONS 第 7 节。

## 7. 文档索引

见 [README.md](README.md)。历史文档（混编规划、旧契约、旧路线图）在 `docs/archive/`，设计定稿在 `docs/superpowers/specs/`。
