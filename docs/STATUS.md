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
2. **T-2 VPS 真机冒烟**：SFTP/指标/本地转发/远端转发数据通路已全部通过（见 1.11）；仅剩下述一项受网络拓扑阻塞。
   - **远端转发数据通路无法在本环境验证**：本机公网出口 IP 经实测**就是该 VPS 自身**（`curl api.ipify.org` = 158.101.11.31，与 `SSH_CLIENT` 一致），VPS 无法 TCP 回连本机 18080；已证 VPS→其自身 127.0.0.1 通路完好（对照试验通过），故属链路不可达而非产品缺陷。需另找一台与开发机互通的服务器复验。
3. **T-3 SFTP 删除/重命名 UI**：`SftpFs.delete/rename` 能力已备，无界面入口。
4. **T-4 P5 多标签/分屏**：会话注册表重构 + AppShell 改造。
5. **T-5 P6 AI 运维侧栏**：待用户决策。
6. 小项池：动态转发 SOCKS5（sshj 无现成实现）、JNA 依赖补 Windows 真实桥、亮色板 Warning 前景变体、自动打包分发。

## 1.11 T-2a 上传假死缺陷 + T-2 剩余冒烟（2026-10-02）

### 1.11.1 T-2a 根因与修复（提交 `445b5c0`）

**根因一句话**：`JvmSftpFs.upload` 对空块执行 `continue` 而不推进 offset，回调持续返回同一个空块 → 死循环空转 100% CPU，表现为「远端已落盘、进程再不返回、无异常无输出」。

| 项 | 内容 |
|---|---|
| 定位手段 | 用测试依赖已有 sshd-core + 新增 `sshd-sftp` 子系统写最小复现（先红）；`jstack` 抓到 Test worker 在 `JvmSshClient.kt:258` RUNNABLE，cpu 589s / elapsed 602s |
| 修复 | 空块是契约违例（约定 null 表示结束），改为 `check(chunk.isNotEmpty())` 快速失败并报出 offset |
| 反证 | 把修复回退成 `continue` 后新增用例转红（`uploaderRejectsEmptyChunkInsteadOfSpinning`） |
| 测试增量 | 10 例：10MiB 整块边界上传校验和、非整块对齐上传、空块快速失败、上传循环终止契约、LocalChunkReader 边界 |

**为何此前没暴露**：既有 `FakeSftpFs` 的 upload 以 `nextChunk(offset) ?: break` 终止，与生产实现不同构；`SftpModel` 是 fire-and-forget（`scope.launch`），UI 上"任务卡在进行中"不易与"线程忙等"区分。教训：**fake 必须与生产实现的终止语义同构**。

### 1.11.2 T-2 剩余项真机结果（VPS Ubuntu 24.04，OpenSSH 9.6p1）

| 分组 | 结果 |
|---|---|
| 连接/认证/心跳 | 公钥认证 2.8s、pingMs 591ms 全通过 |
| 交互式 shell | 独立通道回显 `SHELL_OK_42` 通过 |
| exec 与指标 | 5 条指标命令全部可读且格式可解析；非零退出码、超时置 null 均如实 |
| SFTP | 上传 10MiB / 200KB、下载 10MiB / 200KB、中文文件名、list、delete、取消进行中上传 全部通过（10MiB 上传修复后通过且校验和一致） |
| 本地转发 | `18780 -> VPS 127.0.0.1:18080` 取回 `HTTP/1.0 200 OK` 通过；关闭后本机与远端端口均释放 |
| 远端转发 | 监听建立通过（计数 2）；数据通路受上述拓扑阻塞未能验证 |

**结论**：29 项检查 28 通过，唯一失败项已定位为环境限制而非产品缺陷。

## 3. 已知问题 / 技术债

| 项 | 现状 | 处置建议 |
|---|---|---|
| **~~SFTP 大文件上传崩溃（T-2a）~~** | 已解决（`445b5c0`）：根因为 `JvmSftpFs.upload` 空块 `continue` 不推进 offset 造成 100% CPU 忙等（jstack 实证 589s CPU/602s 挂钟）；修复为 `check` 快速失败，嵌入式 sshd + sshd-sftp 本地复现先红后绿，含反证 | 已关闭；fake 语义同构教训见下行 |
| **远端转发数据通路未验证** | 2026-10-02 真机冒烟：监听建立通过；但开发机公网出口即该 VPS（经它出网），VPS 无法 TCP 回连开发机，最后一跳链路不可达——环境限制非产品缺陷（对照试验：VPS 本机 curl 自建 sentinel 成功，证明 SSH 通道与转发前半段是好的） | 待有一台与开发机互通的服务器时补验；ROADMAP T-2 遗留项 |
| ~~ServersScreen.kt 596 行~~ | 已在 T-1 拆为 ServersScreen/ServersScreenParts/ServerEditDialog 三文件 | 已解决 |
| `.Trash-0/`（仓库根，未跟踪） | 沙箱 safe-delete 副作用 | 已在本地排除，可手动删除 |
| sshj 无动态转发 | UI 禁用标注「即将支持」 | 需自写 SOCKS5 accept 循环，成本较高 |
| 亮色板 Warning(0xFFFBBF24) 前景对比 1.53:1 | 仅在亮背景作前景时不可读 | 组件层定义深色变体 |
| Windows 凭据 isAvailable() 恒 false | 无 JNA 依赖 | 加 `net.java.dev.jna:jna` 后补 JnaAdvapiBridge |
| 测试 init 脚本在项目外 tmpfs | 丢失需重建 | 配方在 DEVELOPMENT.md 2.1 |
| `FileServerRepository` 缺 `coerceInputValues` | 三处同构持久化契约中唯一例外（仅 `ignoreUnknownKeys`）；旧 JSON 出现未知 `StoredAuth` 判别值时 `list()` 返空并隔离为 `.corrupt-*` | 补 `coerceInputValues = true` 对齐另两处；ROADMAP T-1 注意事项已点名，本轮未授权未做 |
| 钥匙串子进程无超时 | `CommandRunner` 同步读流无超时，Linux 下 `secret-tool` 弹解锁框可能长期阻塞 | 当前靠「只在 `LaunchedEffect`/协程里调用」规避；后续可加超时与取消 |
| 无 UI 级删除服务器入口 | `AppModel.removeServer` 已具备（含钥匙串清理）但 ServersScreen 无按钮 | 设计内为 M2 占位；需要时单独排期 |
| `FakeSftpFs.upload` 与生产实现的终止语义不同构 | fake 用 `nextChunk(offset) ?: break`，生产曾是空块 `continue`，导致缺陷逃过单测 | 已由 T-2a 修复生产侧；fake 侧建议后续补充"空块"负样例 |
| `SftpModel` 传输为 fire-and-forget | 无完成句柄，调用方无法 await；排查假死时「卡住」与「忙等」难以区分 | 已决策（2026-10-02）：随 T-3 同批补完成句柄（`awaitCompletion`/Deferred 语义，接口改进走 Ssh.kt 外的 sftp 包内） |

## 3.1 决策追加（2026-10-02 晚，T-2a/T-2 收口）

1. **KeychainCredentialResolver 立即接线**：替换 AppShell 的 `NoopCredentialResolver` 注入（当前凭据不落盘，T-1 功能等于死代码）；接受在无钥匙串会话的环境不可真机验证，真机验证项挂到用户桌面机首次运行。
2. **SftpModel 完成句柄**：随 T-3 同批实现。
3. **远端转发数据通路**：环境受限（开发机经 VPS 出网），待互通服务器补验，非产品缺陷。
4. **T-3 授权**：sftp 包（kotlin+java+fake）与 FilesScreen.kt，Ssh.kt/JvmSshClient.kt 禁改（T-2a 已关闭该范围）。

## 4. 历史决策记录（不要重开讨论）

1. 语言混编（2026-10-02 用户拍板）：Java 管解析/JNA/库包装，Kotlin 管 UI/状态。
2. UI 基线：DBX 设计令牌，暗色优先，亮色板已启用（2026-10-02 完成）。
3. 通知图标：完整启动图标位图，不做单色剪影（用户明确否决过剪影方案）。
4. 动态转发、P6 AI：未决策前 UI 显式标注，不造功能。
