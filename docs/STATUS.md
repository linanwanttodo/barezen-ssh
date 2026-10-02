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

0. **钥匙串真机验证**：接线已在 T-3 完成（`KeychainCredentialResolver.platformDefault()`），但本环境无 `secret-tool`/D-Bus 会话，原生路径未真机跑过。待用户桌面机首次运行时验证：勾选「记住凭据」-> 重启 -> 连接免输密码 -> 钥匙串出现 `BareZen-SSH`/`barezen-id=server/<id>/password` 条目 -> 删除服务器后条目消失。
2. **T-2 VPS 真机冒烟**：SFTP/指标/本地转发/远端转发数据通路已全部通过（见 1.11）；仅剩下述一项受网络拓扑阻塞。
   - **远端转发数据通路无法在本环境验证**：本机公网出口 IP 经实测**就是该 VPS 自身**（`curl api.ipify.org` = 158.101.11.31，与 `SSH_CLIENT` 一致），VPS 无法 TCP 回连本机 18080；已证 VPS→其自身 127.0.0.1 通路完好（对照试验通过），故属链路不可达而非产品缺陷。需另找一台与开发机互通的服务器复验。
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

## 1.12 T-3 SFTP 删除/重命名 + 完成句柄 + 钥匙串接线（2026-10-02 晚，见本批提交）

| 内容 | 说明 |
|---|---|
| 钥匙串接线（T-1 收口） | `AppModel.real()` 注入 `KeychainCredentialResolver.platformDefault()`，替换原 Noop——T-1 自此不是死代码。**不可用时行为与接线前逐字一致**（`isAvailable()` 为 false -> ConnectDialog 走「系统钥匙串不可用，凭据不会保存」分支，勾选框不渲染、凭据不落盘）；原生可用时按 T-1 设计的勾选/预填路径生效 |
| 完成句柄 | `TransferTask.completion: Deferred<TransferTask>` + `SftpModel.awaitCompletion(id)` / `awaitAll()`；任务进入终态（Done/Failed/取消）时完成，未知 id 返回 null 不抛异常。6 例单测覆盖：已完成立即返回、进行中阻塞至终态、失败返回 Failed、未知 id 为 null、失败也必完成、awaitAll 按 id 有序 |
| 删除（T-3） | 行尾「删除」-> 确认框（testTag `file-delete-dialog`）；文件走 delete，空目录走 rmdir；**非空目录由服务端拒绝并把原因显示在错误条，不做一键递归**；完成后刷新列表 |
| 重命名（T-3） | 行尾「重命名」-> 行内输入框（预填原名）；非法名（空/含 `/`/含 `\\`/`.`/`..`）前端拦截、确认按钮禁用并显示原因；名称未变时亦禁用 |
| 新增纯函数 | Java `RemoteNameValidator`（jvmMain/java，与既有解析器同风格，8 例穷举单测：合法/空/空白/分隔符/导航名/超长） |

- 测试增量：**35 例**（421 -> 456，0 失败）。
- 关键实现选择：完成任务用 `CompletableDeferred` 且**只在首次进入终态时完成**，重复置状态不会覆盖（取消后再置状态也安全）。
- 排查价值：本次假死排查中「卡住与忙等难区分」正因缺少完成句柄——现在冒烟脚手架与未来 UI 都可 `await` 传输真正结束。

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
| ~~`SftpModel` 传输为 fire-and-forget~~ | 无完成句柄，无法 await；排查假死时「卡住」与「忙等」难以区分 | **已在 T-3 解决**：`TransferTask.completion` + `awaitCompletion/awaitAll` |

## 3.1 决策追加（2026-10-02 晚，T-2a/T-2 收口）

1. **KeychainCredentialResolver 立即接线**：替换 AppShell 的 `NoopCredentialResolver` 注入（当前凭据不落盘，T-1 功能等于死代码）；接受在无钥匙串会话的环境不可真机验证，真机验证项挂到用户桌面机首次运行。
2. **SftpModel 完成句柄**：随 T-3 同批实现。
3. **远端转发数据通路**：环境受限（开发机经 VPS 出网），待互通服务器补验，非产品缺陷。
4. **T-3 授权**：sftp 包（kotlin+java+fake）与 FilesScreen.kt，Ssh.kt/JvmSshClient.kt 禁改（T-2a 已关闭该范围）。

## 3.2 决策追加（2026-10-02 深夜，T-3 收口后）

T-3 已交付并独立复核（`42ec347`，456 用例 / 0 失败）。

1. **SftpFs.upload 空块契约维持运行期守卫，不改接口**：`check` 快速失败 + KDoc 契约 + 回归测试已闭环；编译期方案（NonEmptyChunk value class / sealed 结果类型）收益边际、牵动 fake/测试/脚手架三处，不值得 Ssh.kt 接口churn。此决策为终局，不再重议。
2. **T-4 多标签批准开工，但强制 spec-first**：先产出 `docs/superpowers/specs/2026-10-02-session-registry-design.md`（SessionRegistry 接口、AppModel 单会话字段迁移路径、*Host 接线层改造、断开/关闭语义、兼容策略），**spec 获批准后**再领实施授权；未批不动代码。
3. 小项池（JNA+Windows 桥、亮色 Warning 变体、打包分发）不阻塞 T-4，由主代理择机批量派发。

## 3.3 T-4 spec 批准与实施决策（2026-10-02 深夜）

spec `f0c6b1c`（session-registry-design.md，543 行）**批准**，评审结论：现状盘点完整、语义矩阵清晰、四刀可独立回滚。四项拍板：

1. **上限 8 批准**：每会话一个 SSHClient + keepalive 线程，桌面单用户可承受；达限拒绝不淘汰（不静默杀会话）。提高上限只改常量。
2. **隧道采纳方案 A**：`ForwardManager` 所有权上移 `SessionResources`——切走保持、关闭释放、不保活；作为独立一刀提交（用户切标签不得断隧道，体验红线）。
3. **终端 widget 先方案 B**：切标签重挂（新 shell、滚动缓冲丢失），UI 必须明示该语义不假装保留；SwingPanel 多实例 interop（方案 A）列入小项池 spike，不阻塞 T-4。
4. **Ssh.kt 零改动判断确认**：`SessionId` 归 app 层，registry 侧包装即可；保留例外条款——实施中确需动接口必须先报批。
5. **上游文档 emoji 清理**：批准单独 docs 提交清理 `2026-09-25-ui-redesign-requirements.md` 的 `✅/📋` 标记，连同本 spec 第 24 行残留的 `📋M4`（引用记号改为「M4」）。纯格式，不动语义。

## 3.4 T-4 会话注册表与多标签实施（2026-10-02 深夜，分批提交）

spec `f0c6b1c` 批准后按 §6 分刀推进，每刀独立提交、门禁全绿。

| 刀 | 提交 | 内容 | 门禁 |
|---|---|---|---|
| 1 | `a601fc8` | `SessionRegistry`（commonMain：`SessionId`/`SessionSnapshot`/`SessionLimitException`，上限 8 达限拒绝不淘汰）；`AppModel.connection`/`shellSession` 改**只读派生投影**；四个生命周期方法委托 registry。**UI 零改动** | 463/0 |
| 2 | `03fd0ce` | `DashboardHost` 改按 `registry.active?.id` 订阅（key 由布尔改会话 id） | 464/0 |
| 3 | `8029970` | FilesHost/PortsHost/TerminalScreen/ServersScreen/ConnectFlowOverlays 全部迁移；`reportShellStartFailed` 改携 `SessionId`；`connectedId` -> `connectedIds`。**全局防重入守卫按 spec §2.3 废除**（允许并发连不同服务器） | 482/0 |
| 4 | `eff498b` | 隧道所有权上移到会话：commonMain `SessionScoped` 接口 + jvmMain `JvmSessionResources`，registry 经 `resourcesFactory` 注入点持有，6 处释放点。**切标签不再断隧道**（用户红线） | 495/0 |
| 5 | `2219855` | 多标签条（渲染全部会话、`selected` 语义、点击切换、关闭按钮、中键关闭、`+` 跳服务器列表）+ 重挂一次性提示（§4.4 红线：不保活，切标签重挂 = 新 shell，滚动缓冲丢失必须明示） | 508/0 |

### 3.4.1 实施中触及的既有测试变更（均经裁决，非静默）

1. **`AppModelTest.secondConnectIgnoredWhileConnecting` 改写**（刀3）：该用例第二次连接的目标是**不同服务器**，
   断言的正是 spec §2.3 明文废除的「全局互斥」；与多标签（Q3 已拍板）互斥且无中间态。
   经裁决按选项 A 处理：**保留原三项意图**（Connecting 态不被覆盖 / 未完成不预存会话 / 放行后成对一致），
   并把「不同服务器可并发建连」**单独新增一例** `concurrentConnectToDifferentServersIsAllowed` 显式锁定新契约。
   spec §7.4 已预先登记该更新属第 3/4 刀范围。
2. **`TerminalScreenTest` 两例删除**（刀1）：`connectedShowsStatusBarWithLatency` 与
   `statusBarShowsMetricSlotsWithoutFakeValues` 断言的是「Connected 且 `shellSession == null`」下的渲染，
   该状态在新不变量（Connected 必有 session）下**不可表达**；一旦携带真会话就会挂真 `TerminalView`（SwingPanel interop），
   headless 下必然抛 `LocalInteropContainer not provided`。且 `ConnectionStatusBar` 为 private 且内容全由
   `is Connected` 包住，无法脱离整屏单独组合——**两条不变量在本环境确实不可验证**。
   按"宁可诚实红，不要削弱绿"处理：**删除而非改成恒真断言**，原位置留注释，**待有显示环境补验**。
3. 既有 `ConnectFlowTest`/`AppModelTest` 其余断言、`ServersScreenTest`/`SessionRegistryTest` 等**一字未改**。
4. **`TerminalScreenTest.tabStripHoldsDisabledPlaceholderButtons` 改名 + 断言替换**（刀5）：原用例锁定的是
   M4 占位（" + " 禁用 + contentDescription「多标签（M4 占位）」）；刀5 落地后 "+" **启用**且描述改「新建终端」，
   占位断言与新实现互斥。按选项 A 处理：改名 `tabStripPlusEnabledAssistantStillPlaceholder`，
   保留原意图中仍成立的部分（「助手」按钮仍为占位禁用），禁用断言替换为 `assertIsEnabled`。
5. **`MultiSessionTabTest.middleClickClosesTab` 注入方式修正**（刀5，新文件内部）：CMP 1.12.1 ui-test 的
   `press` 从光标当前位置注入、`performMouseInput` 不自动移到节点中心（实测事件落在窗口原点，探针验证），
   press 前补 `moveTo(center)`。断言与被锁行为未动，纯注入机制修正。

### 3.4.2 刀4/刀5 的反向验证（防"恒真断言"）

刀4：`PortsHostTunnelLifetimeTest` 两例（切标签隧道不断 / 关闭标签释放隧道）在**把 PortsHost 逐字还原成刀3 形态**后
**双双转红**（`ComposeTimeoutException`），还原为刀4 实现后转绿——证明该用例确实在锁红线，不是恒真断言。

刀5：`MultiSessionTabTest` 13 例在**把 TerminalScreen 逐字还原成刀4 形态（`eff498b`）后 12 例转红**
（唯一不红的 `singleSessionNeverShowsRestartNotice` 属边界钉子：旧 UI 无重挂提示机制，单会话自然无提示；
其作用是锁刀5 之后「单会话不出提示」的边界），还原为刀5 实现后全绿。

### 3.4.3 已知未完成项（不假装已做）

| 项 | 说明 |
|---|---|
| 关闭标签的"SftpModel 进行中传输"确认框（spec §4.5） | **未实现**：`SftpModel` 归 FilesHost 私有持有，要检测需把它也上移到 `SessionScoped`，属新的所有权变更，不宜塞进 UI 刀 |
| `FilesHost` 仍由 Host 持 `SftpModel` | **切标签会取消进行中的传输**，语义与刀3 前一致；是否上移待单独裁决 |
| 非活动会话远端断线探测（spec §9 R1） | 本轮明确不做，仍为技术债 |
| 后台会话的指标/终端 widget | 按 spec §2.2「单活动前台 + 多后台连接保留」：后台只保持 SSH 连接，不轮询、不渲染 |

## 4. 历史决策记录（不要重开讨论）

1. 语言混编（2026-10-02 用户拍板）：Java 管解析/JNA/库包装，Kotlin 管 UI/状态。
2. UI 基线：DBX 设计令牌，暗色优先，亮色板已启用（2026-10-02 完成）。
3. 通知图标：完整启动图标位图，不做单色剪影（用户明确否决过剪影方案）。
4. 动态转发、P6 AI：未决策前 UI 显式标注，不造功能。