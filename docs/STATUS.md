# 进度台账（STATUS）

更新：2026-10-02（HEAD `a0b5c8a` + T-1 提交）。本文是唯一进度事实源：完成了什么（提交对照）、没完成什么、已知问题。

## 1. 已完成

### 1.1 M0-M1 基础（2026-09 下旬）

| 内容 | 提交 |
|---|---|
| 壳 + SSH 连接 + JediTerm 终端（TOFU 主机密钥、心跳） | `7ab5fae` 起，设计定稿见 specs/2026-09-24 |
| 服务器管理（增删改查、掩码显示、导入导出脱敏） | P1 链 |
| 通知统一走 TransferNotifier、传输中心重构、拍摄时间毫秒修复 | 早期链 |

### 1.2 P1 设置补全（2026-10-01，13 任务 / 15 提交）

| 内容 | 提交 |
|---|---|
| 版本号单一真相源 + 一致性守卫 | `f8a6adb` |
| AppSettings 模型 + sanitize 收敛 + 文件持久化（原子写/损坏隔离）+ 内存态 | `e0dbc52`/`87b2a38`/`e3b9a26` |
| 设置接入 AppModel（主题/缩放）+ 屏路由 + 共享行控件 | `deee9e3`/`1f4f31b` |
| 六分类真实化：外观/终端/连接（含启动自动连接）/存储（含 OpenSSH config 导入）/更新（可配源零请求）/关于 | `6518979` 至 `dff8d2a` |
| 测试加固（自动连接三用例等） | `e9adf03` |

### 1.3 重构与规范（2026-10-02 上午）

| 内容 | 提交 |
|---|---|
| 混编决策落地：全面审计/语言适配表/规划 | `e1b303d`（已归档） |
| 文档体系 v1 + 子代理契约 | `3784a45`（已被本轮重写取代） |
| **包名重构** `com.barezen.barezen_ssh -> com.barezen.ssh`（80 文件；资源生成包显式 `com.barezen.ssh.generated.resources`） | `3f7059d` |

### 1.4 R0 主题底座（2026-10-02）

| 内容 | 提交 |
|---|---|
| DBX 暗色令牌（chrome/content/gutter 三层 + 中性主色 + 四语义色）+ 亮色板 15 令牌 + 形状 6/4 | `56a686b` |
| 亮色端到端：`Theme.LIGHT` 枚举、FOLLOW_SYSTEM 真实解析、外观选项解禁 | `bfc26f5` |

### 1.5 P2 主机监控（2026-10-02）

`SshSession.exec`（有界 join 超时先关通道再读流 + 会话锁串行化）；5 个 Java 解析器（loadavg/meminfo/uptime/df -P/CPU 双采样，穷举单测）；`MetricsCollector` 轮询（StateFlow，失败置 null 不造数）；仪表盘四指标真数据 + Canvas 折线 + 磁盘用量条。提交 `d947c5a`。

### 1.6 共享基线（2026-10-02，主代理亲写）

`Ssh.kt` 扩展：`SftpFs`（流式分块下载/上传）、`ForwardSpec/ForwardTunnel`、`SftpEntry`；`JvmSshClient` 实现 SFTP 与本地/远程转发（sshj API 全部 javap 核实）。提交 `8ca322e`。

### 1.7 P3 端口转发 + 凭据（2026-10-02，子代理 forward-p3）

`CredentialStore` 接口 + GnomeKeyring/MacKeychain（子进程封装、进程注入可测）+ WindowsCredStore（bridge 注入）；`ForwardManager` 状态机 + `ForwardRuleStore`（JSON 原子写）；PortsScreen 重写（表单校验/规则列表/自动启用开关）+ 10 UI 测试。提交 `56ab1f0`。

### 1.8 P4 SFTP 文件管理（2026-10-02，子代理 sftp-p4）

`SftpModel`（并发传输任务/取消标志位/进度节流）+ 5 个 Java 纯逻辑类（路径/排序/可读大小/节流/本地分块）+ FilesScreen 重写（路径栏/列表/上传下载/传输区）+ 83 测试。提交 `a0b5c8a`。

### 1.9 壳级接线（2026-10-02，主代理）

`AppShell` 增 `FilesHost/PortsHost`（连接创建模型、断开取消传输/关隧道；autoStart 规则自动启用），含于 `a0b5c8a`。

## 1.10 T-1 钥匙串凭据接入连接流程（2026-10-02，主代理基线 + 子代理并行）

| 内容 | 提交 |
|---|---|
| 共享基线：commonMain `CredentialResolver` 端口（load/remember/forget/isAvailable）+ `NoopCredentialResolver`；`AppModel` 注入 `credentials`（默认空实现，既有行为不变）、`removeServer` 同步清理钥匙串、`pendingPrefill`/`initializePendingCredential`/`keychainAvailable` | `d010f0d` |
| jvmMain 适配器 `KeychainCredentialResolver`（keyPath 恒取自 Server.auth、钥匙串只供口令；load/remember/forget 全程不外溢异常；isAvailable 区分真钥匙串与内存降级）+ 26 用例 | 见本批提交 |
| 连接流程接线：`ConnectDialog` 增 prefill/keychainAvailable/「记住凭据」勾选与三分支安全文案；`AppShell` 经 `LaunchedEffect` 单次解析预填并回写钥匙串 | 见本批提交 |
| ServersScreen 拆分：`ServersScreen.kt`(113) / `ServersScreenParts.kt`(395) / `ServerEditDialog.kt`(149)，596 行四职责耦合解除 | 见本批提交 |

- 验收对照：① 未存凭据的连接流程与现状一致（`NoopCredentialResolver` 默认 + 回归全绿）；② 钥匙串可用时勾选「记住凭据」下次免输入、不可用时回退并提示（`ConnectDialog` 三分支文案 + 测试）；③ 删除服务器同步清理（`AppModel.removeServer` → `forget`，幂等）；④ UI 测试覆盖勾选/未勾选/不可用，门禁 411/0。
- **待真机验证**：本沙箱无 `secret-tool`（实测退出码 1、无 D-Bus 服务），`probeNativeStore()` 恒返回 null，故「真钥匙串写入后下次连接免输密码」与「原生可用的 macOS/Windows 分支」只能由用户在本机验证；沙箱内覆盖的是注入式 fake 与内存降级路径。`KeychainCredentialResolver.platformDefault()` 为壳层接线入口（当前 `AppShell` 尚未注入它，仍走 `NoopCredentialResolver`）。

## 2. 未完成（按优先级，任务书见 ROADMAP.md）

1. **T-1 剩余：壳层注入 `KeychainCredentialResolver.platformDefault()` 并真机验证**：端口、适配器、UI、测试均已就绪；`AppShell` 目前仍用默认 `NoopCredentialResolver`（凭据不落盘），接上工厂即启用，属一行改动，与真机验证一并在 T-2 完成。
2. **T-2 VPS 真机冒烟**：指标/转发/SFTP 在真实服务器上的验证（待用户提供凭据）。
3. **T-3 SFTP 删除/重命名 UI**：`SftpFs.delete/rename` 能力已备，无界面入口。
4. **T-4 P5 多标签/分屏**：会话注册表重构 + AppShell 改造。
5. **T-5 P6 AI 运维侧栏**：待用户决策。
6. 小项池：动态转发 SOCKS5（sshj 无现成实现）、JNA 依赖补 Windows 真实桥、亮色板 Warning 前景变体、自动打包分发。

## 3. 已知问题 / 技术债

| 项 | 现状 | 处置建议 |
|---|---|---|
| ~~ServersScreen.kt 596 行~~ | 已在 T-1 拆为 ServersScreen/ServersScreenParts/ServerEditDialog 三文件 | 已解决 |
| `.Trash-0/`（仓库根，未跟踪） | 沙箱 safe-delete 副作用 | 已在本地排除，可手动删除 |
| sshj 无动态转发 | UI 禁用标注「即将支持」 | 需自写 SOCKS5 accept 循环，成本较高 |
| 亮色板 Warning(0xFFFBBF24) 前景对比 1.53:1 | 仅在亮背景作前景时不可读 | 组件层定义深色变体 |
| Windows 凭据 isAvailable() 恒 false | 无 JNA 依赖 | 加 `net.java.dev.jna:jna` 后补 JnaAdvapiBridge |
| 测试 init 脚本在项目外 tmpfs | 丢失需重建 | 配方在 DEVELOPMENT.md 2.1 |
| `FileServerRepository` 缺 `coerceInputValues` | 三处同构持久化契约中唯一例外（仅 `ignoreUnknownKeys`）；旧 JSON 出现未知 `StoredAuth` 判别值时 `list()` 返空并隔离为 `.corrupt-*` | 补 `coerceInputValues = true` 对齐另两处；ROADMAP T-1 注意事项已点名，本轮未授权未做 |
| 钥匙串子进程无超时 | `CommandRunner` 同步读流无超时，Linux 下 `secret-tool` 弹解锁框可能长期阻塞 | 当前靠「只在 `LaunchedEffect`/协程里调用」规避；后续可加超时与取消 |
| 无 UI 级删除服务器入口 | `AppModel.removeServer` 已具备（含钥匙串清理）但 ServersScreen 无按钮 | 设计内为 M2 占位；需要时单独排期 |

## 4. 历史决策记录（不要重开讨论）

1. 语言混编（2026-10-02 用户拍板）：Java 管解析/JNA/库包装，Kotlin 管 UI/状态。
2. UI 基线：DBX 设计令牌，暗色优先，亮色板已启用（2026-10-02 完成）。
3. 通知图标：完整启动图标位图，不做单色剪影（用户明确否决过剪影方案）。
4. 动态转发、P6 AI：未决策前 UI 显式标注，不造功能。
