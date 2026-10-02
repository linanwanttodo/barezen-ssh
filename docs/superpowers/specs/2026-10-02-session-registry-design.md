# P5 设计 · 会话注册表与多标签（交接版）

> 状态：**待用户批准**（批准前不动任何产品代码）
> 目标读者：**执行本设计的下一个 AI**（不共享本设计的对话上下文，本文自包含）
> 上游需求：`docs/superpowers/specs/2026-09-25-ui-redesign-requirements.md` 域 3「终端」第 5/6 条（多标签 / 分屏，D1/D2）、域 2「SSH 连接与会话」
> 设计权威：`docs/ui-redesign/index.html` + `styles.css`（配色/间距/圆角/词表一律以它为准）
> 任务书：`docs/ROADMAP.md` T-4
> 定位：M3（v0.3.0）唯一子项目，本轮**只出 spec**，实施等批准

---

## 0. 执行须知（先读这一节）

### 0.1 你要交付什么

把目前「一个进程一条 SSH 会话」的模型，换成**会话注册表 + 多标签**：用户在服务器列表点
「新建终端」连上 A 机后，可以再连 B 机；两条会话各自独立地跑终端、指标轮询、SFTP、端口转发；
顶部标签条可在其间切换、关闭单条、全部关闭。

非目标（本轮明确不做，别自行发挥）：

| 不做 | 原因 |
|---|---|
| 分屏（向右/向下拆分，D2） | 需求标 📋M4，与多标签同域但**独立**；标签树结构先按「扁平列表 + 可选 split 字段预留」设计，不实现渲染 |
| AI 侧栏（M5） | 未拍板（ROADMAP T-5） |
| 会话后台保活 | 见 §3.4 的决策与理由：切走的会话**保留连接**，但**关闭标签即释放全部资源** |
| 标签拖拽排序 / 重命名 | 需求未要求；v0.3.0 不做 |
| 分屏内的 pane 焦点模型 | 随 D2 |

### 0.2 当前基线（开工前先自己确认）

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
git log --oneline -1     # 期望 HEAD = 72bcd53 或其继任者
```

基线是 **456 用例 / 0 失败**（CONVENTIONS §7）。低于此数或有失败，先停下来问用户，不要在红基线上开工。

### 0.3 本次重构的风险声明（必读）

这是 M1 以来**面最大**的一次改动：`AppModel.connection` 与 `AppModel.shellSession` 是全应用
连接语义的**唯一真相源**，被 6 个文件、20 余处调用点消费（清单见 §2.2）。改造原则：

1. **不删旧字段，先并存**：`connection`/`shellSession` 在第一阶段保留为「活动会话的投影」，
   语义等价于现状；消费方逐个迁到 registry。**任何一步都不允许出现"两个真相源都能写"的中间态**——
   投影是**只读派生**，不是可写副本。
2. **每步门禁全绿**才进下一步（§6 给了三刀切法）。
3. 迁移期**禁止**顺手改业务语义。发现相邻问题记录下来上报，不擅自修（CONVENTIONS §1.5）。

---

## 1. 现状（改造的出发点）

### 1.1 单会话模型的实际形状

`AppModel`（commonMain，178 行）持有两个字段表达"当前连接"：

```kotlin
var connection: ConnectionState by mutableStateOf(ConnectionState.Disconnected)  // private set
var shellSession: SshSession? = null                                             // private set
```

`ConnectionState`（`shared/.../ssh/Ssh.kt`）是四态 sealed interface：
`Disconnected` / `Connecting(server)` / `Connected(server, latencyMs)` / `Failed(server, message)`。

关键约束（**现状注释里已经写明的既有契约，不要打破**）：

- `startConnect` **防重入**：`if (connection is ConnectionState.Connecting) return`。注释给了理由——
  并发协程会互相丢弃会话（孤儿 SSH 连接 + keep-alive 线程），且两次赋值可能被末写者覆盖成不一致。
- `startConnect` 内**先关旧会话再建新**（`shellSession?.close(); shellSession = null`），
  因为 sshj 单连接多通道已验证，但多连接 = 多 `SSHClient` 实例，资源与心跳各自独立。
- `connection` 与 `shellSession` **必须同进同退**。既有测试 `AppModelTest.kt:87` 显式断言
  「连接未完成不预存会话：状态与会话不脱节」——这是**被测试锁定的不变量**，registry 必须继续满足。

### 1.2 消费面清单（迁移的完整靶子）

`connection` 的读取点（`grep -rn 'model.connection\|ConnectionState' shared/src desktopApp/src`）：

| 文件 | 用处 | 迁移后读什么 |
|---|---|---|
| `ui/shell/ConnectFlowOverlays.kt:35,38,55` | 连接中/失败全局覆盖层 | **全局连接尝试态**（可能同时多于一条？见 §5 决策 Q3），不是活动会话 |
| `ui/shell/AppShell.kt:142` | FilesHost 判连接 | 活动会话 |
| `ui/shell/AppShell.kt:166` | PortsHost 判连接 | 活动会话 |
| `ui/screens/DashboardScreen.kt:135` | DashboardHost 判连接 | 活动会话 |
| `ui/screens/TerminalScreen.kt:51,53,60,70,190,207` | 终端屏整屏状态机 + 标签条 + 状态栏 | 活动会话 |
| `ui/screens/ServersScreen.kt:77` → `ServersScreenParts.kt:70,226,227` | 卡片"已连接 · N ms"徽标、未连接计数、失败横幅 | **该服务器是否已在某条会话中** |
| `commonTest/.../AppModelTest.kt`, `jvmTest/.../ConnectFlowTest.kt:101` | 断言会话字段 | 迁移需同步更新（见 §7.4） |

`shellSession` 的读取点：`AppShell.kt:147,173`、`DashboardScreen.kt:140`、`TerminalScreen.kt:166`，
以及两个测试。

### 1.3 各 Host 的模型生命周期（迁移必须保留的语义）

三个 Host 是同一个范式（ARCHITECTURE §2 已把它写成规范）：

```kotlin
val connected = model.connection is ConnectionState.Connected
LaunchedEffect(connected) {
    val session = model.shellSession
    if (connected && session != null) { /* 建模型，start 轮询 / 建 SFTP */ }
    else { /* 停轮询 / 取消传输 / 关隧道 */ }
}
```

- **DashboardHost**：建 `MetricsCollector(session)` 并 `start(scope)`；断时 `stop()`。另有 `DisposableEffect(Unit)` 兜底 `stop()`。
- **FilesHost**：建 `SftpModel(scope, fsFactory = { session.newSftp() })`；断时取消所有 `Running` 传输再置 null。
- **PortsHost**：建 `ForwardManager { spec -> session.startForward(spec) }`，包成 `StateFlow` 交给 `PortsModel`；
  断时 `closeAll()`。`DisposableEffect(Unit)` 兜底 `closeAll()`。
  另有一条**旁路**：`autoStartRules` 读 `FileForwardRuleStore`（文件持久化），把 `autoStart` 规则批量 `applyAll`。

注意一个**现存的可疑点**（不在本设计修复范围，但要记为迁移风险）：三个 Host 的
`LaunchedEffect(connected)` 只以布尔 `connected` 为 key。当前从 A 机直接连到 B 机必然经过
`Connecting`（`connected` 假→真），所以会重建；但**如果未来出现两条会话同时 Connected**，
只看布尔就无法区分是哪条——这正是要改成以 `sessionId` 为 key 的原因（§3.2）。

### 1.4 终端组件的会话耦合

`TerminalView(session, settings, modifier)`（`terminal/TerminalView.kt`）以 `session` 为 key：
`remember(session)` 建 widget、`DisposableEffect(session)` 起 shell、`key(session)` 包 `SwingPanel`。
**SwingPanel 的 interop holder 是无 key 的 `remember`，只认首次 factory 结果**——原注释明确：
会话切换时换 key 重建整个组，才能让新 widget 真正挂到面板上。

这是多标签**最大的 UI 技术风险**：若把多个 `TerminalView` 同时保留在组合树里（例如用
`Box` 叠放 + 只显示活动者），SwingPanel 的 interop 行为与焦点/尺寸归属都需要实测。
**本设计的对策**：同一时刻**只组合活动会话的 TerminalView**，非活动会话的终端 widget 销毁重挂
（见 §4.4 的取舍与代价分析）。

---

## 2. SessionRegistry 设计（commonMain）

### 2.1 核心类型

新增 `shared/src/commonMain/kotlin/com/barezen/ssh/app/SessionRegistry.kt`。

```kotlin
/** 会话唯一标识。单调递增，进程内唯一；不复用（关闭后 id 不再分配给新会话）。 */
@JvmInline
value class SessionId(val raw: Long)

/** 单条会话的快照（不可变；UI 只消费本类型，不持有 SshSession）。 */
data class SessionSnapshot(
    val id: SessionId,
    val server: Server,
    val state: ConnectionState,      // 复用既有四态，语义不变
    val session: SshSession?,        // 仅 Connected 时非空
    val title: String,               // 默认 server.name；重复连接同名服务器时为 "name (2)"
)
```

```kotlin
/**
 * 会话注册表：多会话的唯一真相源。
 *
 * 线程模型：全部公开方法必须在主线程调用（Compose 重组线程）；
 * 内部以 mutableStateListOf 承载，保证 Compose 可观察。
 */
class SessionRegistry(
    private val ssh: SshClient,
    private val scope: CoroutineScope,
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
) {
    val sessions: List<SessionSnapshot>            // 只读视图，顺序 = 标签显示顺序（插入序）
    var activeId: SessionId?                       // 活动会话；写完即重组
    val active: SessionSnapshot?                   // activeId 对应的快照，无则 null

    /** 建连。返回新会话 id（立即返回，状态为 Connecting；完成/失败经 flow 推进）。 */
    fun connect(server: Server, auth: AuthMethod): SessionId
    fun cancel(id: SessionId)                      // 掐掉 Connecting 的协程 -> 移除该会话
    fun disconnect(id: SessionId)                  // 关会话 -> 置 Disconnected（会话仍留在列表）
    fun close(id: SessionId)                       // 关闭标签：关会话 + 从列表移除
    fun closeAll()                                 // 关闭全部标签
    fun activate(id: SessionId)
    fun retry(id: SessionId)                       // Failed 会话重连（原地复用 id 与 title）

    /** 会话终态事件；供无 Compose 的调用方（脚手架/测试）等待。 */
    val events: Flow<SessionEvent>
}
```

### 2.2 活动会话语义（**决策 Q1**）

**决策：单活动前台 + 多会话并存（后台连接保留），不做真并行活跃渲染。**

- **单活动**：任何时刻只有一个 `activeId`；只有活动会话的屏幕被组合、其 MetricsCollector 在轮询。
- **多后台并存**：非活动会话的 `SshSession` **保持连接**（keep-alive 心跳照跑），终端 widget **销毁**。
- 理由：
  1. 需求域 3 第 5 条只要求「新建 / 关闭 / 中键关闭 / 切换」，**没有**要求后台会话继续渲染输出。
  2. 同时组合多个 JediTerm widget 会同时起多个模拟器线程 + SwingPanel interop 实例，收益不明确、
     风险明确（§1.4）。切换时重挂 widget 的代价是**滚动缓冲丢失**——这是**已知且要在 UI 上诚实呈现**
     的行为（见 §4.5），不是隐藏缺陷。
  3. 指标轮询、SFTP、隧道都是**活动会话的资源**；后台会话不占用这些，多连 5 台也不会 5 倍 CPU。

**容量上限与淘汰（决策 Q1 续）**：`DEFAULT_MAX_SESSIONS = 8`。达上限时 `connect` **不做隐式淘汰**，
返回前**先拒绝并报错**（`SessionLimitException`，UI 显示「最多同时打开 8 个会话，请先关闭一个」）。
理由：隐式关闭用户会话是**破坏性行为**，且"淘汰最久未用"会让用户正在等待的输出静默消失——
与不造数/不越权的一贯立场一致。上限本身防止 SSH 连接线程无界增长。

> **待用户确认点**：8 是否合适？（每个会话 = 1 个 sshj SSHClient + 1 keep-alive 线程 + 若干通道）

### 2.3 与既有不变量的一致性

`startConnect` 今日的**防重入**语义在新模型下必须**重新定义**（§5 决策 Q3）：
单会话时代「连接中忽略一切新连接请求」；多会话时代每个会话 id 有**自己的**连接协程，
防重入退化为「同一 `SessionId` 不重复建连」，而**同时连接两台不同服务器是被允许的**——
这正是多标签的意义。但由此引入一个新的不一致窗口：**两条会话并发建连**，
若两者都经 `AppModel.connection` 投影，投影只能反映其一。§6 的迁移第二步消除了这个窗口。

---

## 3. AppModel 迁移路径

### 3.1 迁移的分步（与 §6 的提交切分一一对应）

**第一步（并存）**：`AppModel` 增 `val registry: SessionRegistry`；`connection`/`shellSession`
改为**只读派生投影**：

```kotlin
// 迁移期投影：语义与改造前逐字等价（单一会话时）
val connection: ConnectionState get() = registry.active?.state ?: ConnectionState.Disconnected
val shellSession: SshSession? get() = registry.active?.session
```

同时 `startConnect`/`disconnect`/`cancelConnect`/**`reportShellStartFailed`** 改为**委托** registry。
`applyConnectionForTest` 保留但在测试内改为经 registry 建一条假会话来达成（见 §7.4）。

**这一步结束时**：所有既有消费点**一行不改**仍然工作，456 用例必须全绿——这是本设计
最重要的**安全垫**：任何回归都能在"UI 未动"的条件下定位到 registry 本体。

**第二步（试点 Host）**：只把一个 Host（建议 **DashboardHost**，因为它最独立、不碰隧道/传输）
改为以 `registry.activeId` 为 key。门禁全绿。

**第三步（其余迁移）**：FilesHost / PortsHost / TerminalScreen / ServersScreen / ConnectFlowOverlays。

### 3.2 Host 接线层改造（**决策 Q2**）

统一范式（替换现有 `LaunchedEffect(connected)`）：

```kotlin
@Composable
private fun DashboardHost(model: AppModel) {
    val active = model.registry.active          // SessionSnapshot?，Compose 可观察
    val scope = rememberCoroutineScope()
    var collector by remember { mutableStateOf<MetricsCollector?>(null) }

    // key = 会话 id（不是布尔）：切换会话必重建，同一会话的状态推进不重建
    LaunchedEffect(active?.id) {
        val session = active?.session
        if (active != null && session != null) {
            val c = MetricsCollector(session); collector = c; c.start(scope)
        } else { collector?.stop(); collector = null }
    }
    DisposableEffect(active?.id) { onDispose { collector?.stop() } }
    DashboardScreen(snapshot = collector?.snapshot?.collectAsState()?.value, connected = active != null) { collector?.refresh() }
}
```

要点：

- **key 用 `SessionId`，不用 `Boolean`。** 这修掉 §1.3 末尾指出的隐患。
- **切换会话 = 销毁旧模型 + 建新模型**（与今日"重连"行为同构）。不做模型池/暂存：
  暂存会让 5 个后台会话各留一个轮询协程，与 §2.2「后台不占资源」的决策矛盾。
- **失败态会话**：`active` 非空但 `state is Failed` → `session` 为 null → 走 else 分支释放资源，
  UI 现失败态 + 重试（语义与今日 `FailedPane` 一致）。

### 3.3 TerminalScreen 改造

现状 `TerminalScreen(model)` 直接 `when(model.connection)` 渲染整屏。改造后：

- 入参改为 `TerminalScreen(model)`，内部读 `model.registry.active`。
- 标签条数据源从「`connection.server.name` 单标签」换成 `registry.sessions` 全列表。
- `ConnectedPane` 的 `ShellStartGuardedSession`（把 `startShell` 抛错转成 `reportShellStartFailed`）
  **语义要重新指向具体会话**：`reportShellStartFailed(server, message)` →
  `reportShellStartFailed(sessionId, message)`（否则会关错会话）。这是**必须**的签名变更。
- 无活动会话但**有**会话（全部 Failed/Disconnected）→ 显示会话列表空态还是最后一个会话的失败态？
  **决策**：`activeId` 永不置 null（除非列表为空）；关闭活动标签时自动激活**左邻**标签
  （主流客户端惯例），全部关闭才回到「在服务器列表选择『新建终端』以开始。」空态。

### 3.4 PortsHost 与隧道生命周期（**决策 Q4，用户已给倾向**）

**决策：隧道随会话关闭；不做后台保活。**

- 会话**切走**（非活动）时：**隧道保持**（会话仍在连接，隧道是其资源，关掉等于静默断用户的服务）。
  但 `ForwardManager` 实例**随 Host 销毁**——这会造成隧道句柄丢失。
  **这是本设计唯一需要产品决策的硬点**，方案见下方 A/B 与推荐。
- 会话**关闭**（关标签）时：`closeAll()` + 关 SSH 会话 → 隧道随之消亡。**不做保活**——
  理由：隧道绑定 SSH 会话，会话没了隧道必然不通；"保活"只能是重连后自动重建，
  而**无人值守地自动重建用户的端口转发**是安全敏感行为（本机突然多出一个监听端口），
  与 PRODUCT 一贯的"显式授权"立场冲突。

**方案 A（推荐）**：`ForwardManager` 的**所有权上移到会话本身**——每个 `SessionSnapshot`
持有一个 `SessionResources`（含 manager、collector、sftpModel 的**惰性工厂**），
Host 只负责**订阅**与**UI 呈现**，不再负责创建/销毁。

- 优点：切走再切回，隧道**不断**、活动转发列表**不丢**（符合上面"切走保持"的决策）；
  会话关闭时统一释放，不会漏。彻底解决"Host 销毁 = 句柄丢失"。
- 代价：`SessionResources` 需要跟随会话生命周期，涉及 `SessionRegistry` 与三个 Host 的**共同**改动 →
  与 §3.2「切换即重建」的简化范式冲突。

**方案 B**：维持 Host 持有模型；切走时 `closeAll()` 关隧道，切回时按 `autoStart` 规则重建。

- 优点：改动小，Host 范式不变。
- 代价：**切走标签就断掉用户正在用的隧道**（例如用户挂着 DB 隧道去另一个标签看日志，回来发现断了），
  且非 autoStart 的规则不会自动恢复 → 用户需手动重开。**体验上是明确的倒退**。

**推荐 A**，但把它**独立成一个提交**（§6 第四刀），因为它引入了新的抽象；
若用户认为 v0.3.0 不值得，**降级为 B 并明说代价**。

### 3.5 ServersScreen 的"已连接"语义

`ServerCard` 今日用 `model.connection` 判「本卡是否已连接」。多会话下变为：
**该 server 是否存在于 `registry.sessions` 中且 state 为 Connected**（可能多条！）。

- `connectedId: String?` → `connectedIds: Set<String>`；未连接计数 = `servers.count { it.id !in connectedIds }`。
- 卡片徽标：一条时「已连接 · N ms」（现状）；**多条时不撒谎**——显示「已连接 · 2 个会话」，
  延迟取活动会话那条（若卡片的服务器不是活动会话，则不显示延迟，只显示「已连接」）。
- 「新建终端」按钮在多会话下语义不变（再开一条）。
- 「全部重连」占位按钮（当前禁用）**不在本轮启用**。

---

## 4. UI 设计

### 4.1 标签条形态（照主流 SSH 客户端惯例，不自创）

复用需求域 3 已给的形态：**终端区顶部 36dp 标签条**（`TerminalScreen.TerminalTabStrip` 已是骨架）。

- **位置**：终端屏内容区顶部（现状即如此），非壳级全局——因为标签是**终端域**概念，
  文件/端口/仪表盘以活动会话为上下文，不各自带标签。
- **标签内容**：`Terminal` 图标（14dp）+ 标题（`server.name`，重名时 `name (2)`）+ 关闭按钮
  （16dp，hover 才显示，当前活动标签恒显示）。
- **活动态视觉**：照设计包既有约定——`surfaceContainerHigh` 底 + 6dp 圆角 + 12sp Medium（现实现已如此）。
- **交互（全部照惯例）**：
  - 单击 = 切换（`registry.activate(id)`）；
  - **中键点击 = 关闭**（需求域 3 第 5 条明确要求）；关闭按钮 = 关闭；
  - 关闭是**立即关闭**还是**确认**？**决策**：立即关闭，**不弹确认**——与主流客户端一致；
    断开是**可恢复**操作（重连即可），而 SFTP 进行中的传输会被取消这一点必须在
    §4.5 的"未保存状态"提示里覆盖，而不是靠每次弹框。
  - 标签溢出：横向滚动（`horizontalScroll`），不做下拉菜单（会话上限 8，溢出概率低）。
- **标签条右侧**（现状已有，语义调整）：
  - `+`：**从禁用占位改为可用**，点击 → 回到 `Destination.SERVERS` 让用户选机（**决策 Q5**）。
    理由：`+` 若直接弹一个服务器选择器，等于在终端域里复制一份服务器列表；
    而"切到服务器列表再点新建终端"是现有且已测的路径，复用零风险。
  - 「助手」：保持禁用占位（M5 未决策）。

### 4.2 新增会话入口与既有「新建终端」的关系

**不新增入口。** 现有路径已足够：服务器卡片「新建终端」/「连接」→ `requestConnect` → `ConnectDialog`
→ `confirmConnect`。改造点是 `confirmConnect` 从"替换当前会话"变为"**新建一条会话并激活**"。

从 `Destination.FILES` 的「打开文件管理」（`onOpenFiles` → `navigate(FILES)`）保持不变：
文件页以活动会话为上下文。

### 4.3 壳层路由

`Destination` 六个枚举**不变**。内容区路由不变。多标签是**终端屏内部**的结构，
不引入新 Destination——这与需求域 3 的信息架构一致，也把改动面控制在 `TerminalScreen.kt` 内。

### 4.4 终端 widget 的重挂代价（诚实说明）

切换标签会销毁并重建 `JediTermWidget`，**代价是滚动缓冲与屏幕内容丢失**（重新 startShell 也在
服务端新起一个 shell 进程——注意：**不是恢复原 shell**，是**新建** shell）。

**这是一个必须让用户看见的语义。** 两个可选处置：

- **A（推荐）**：切换标签时**不重挂**，而是把每个会话的 widget 缓存在 `key(session)` 下**同时保留**，
  用 `Box` + `zIndex`/`alpha` 控制可见性。**但**：需实测 SwingPanel interop 在多个实例下的
  焦点与尺寸行为——**这是本设计最大的未知数**。
- **B（保守）**：接受重挂，在标签切换时**明确**这是"重新打开会话"的语义，
  UI 上不承诺保留滚动缓冲。

**建议**：实施第一步先做 **B**（风险低、语义诚实），把 **A** 作为**独立 spike 任务**在
v0.3.0 之后评估。若用户要求"切回来内容还在"，则 A 升为 v0.3.0 必做项，且需要额外的
Swing interop 原型验证时间。

### 4.5 关闭会话的未保存状态提示

关闭标签会**静默取消进行中的 SFTP 传输**（`SftpModel` 的 Running 任务）与**关闭隧道**。
现状 `FilesHost` 在断开时正是这么做的（取消所有 Running 传输）——即今日"断开连接"已有同样副作用。

**决策**：关闭标签时，若该会话存在 `Running` 的传输，弹一次确认框
（「该会话有 N 个传输进行中，关闭将取消它们。[取消] [关闭]」）；无进行中传输则**不弹框**。
理由：这是**不可逆**的数据损失（半个文件），与"关闭标签不弹框"的惯例不冲突——
惯例的前提是无损。

---

## 5. 语义矩阵与决策汇总

### 5.1 断开/失败/关闭的语义矩阵

| 事件 | 会话列表 | 该会话 `state` | `activeId` | 隧道 | 进行中传输 | 指标轮询 |
|---|---|---|---|---|---|---|
| 单条**断开**（`disconnect(id)`） | 保留（标签变灰） | `Disconnected` | 不变 | `closeAll()` | 取消 | 停止 |
| 单条**关闭**（`close(id)`） | 移除 | — | 左邻（或末尾/ null） | `closeAll()` | 取消（有则先确认，§4.5） | 停止 |
| 单条**失败**（建连失败） | 保留 | `Failed` | 若为活动则保持活动，显示失败态 + 重试 | 无 | 无 | 无 |
| **取消连接中**（`cancel(id)`） | 移除 | — | 左邻 | 无 | 无 | 无 |
| **关闭全部**（`closeAll()`） | 空 | — | `null` | 全部关闭 | 全部取消 | 全部停止 |
| 应用**退出**（关窗） | — | — | — | 全部关闭 | 全部取消 | 全部停止 |
| **非活动**会话的后台断线 | 保留 | 由 SSH 层心跳决定（**本轮不做主动探测**） | 不变 | 保持 | 保持 | 无 |

最后一行是**已知开放项**：需求域 3 第 172 行把「远端 shell 退出/断线的呈现」列为开放项，
现状未做检测。本设计**不新增**断线探测（无 keep-alive 失败回调的现成钩子），
但在 STATUS 中明确登记为技术债，不假装已处理。

### 5.2 决策汇总（评审时逐条确认）

| # | 决策 | 备选与代价 |
|---|---|---|
| Q1 | **单活动前台 + 多后台并存**；上限 8，达限拒绝不淘汰 | 真并行活跃（渲染风险大）；LRU 淘汰（破坏性） |
| Q2 | Host 以 `activeId` 为 key，**切换即重建**模型 | 模型池/暂存（后台 5 条各留轮询协程） |
| Q3 | **允许并发连接不同服务器**；防重入退化为 per-SessionId | 保留全局互斥（等于没有多标签） |
| Q4 | 隧道**随会话关闭**，不做后台保活；切走时**保持**（方案 A 上移所有权，或降级 B 明说断隧道） | 见 §3.4 |
| Q5 | `+` 按钮 → 跳服务器列表 | 终端域内嵌选择器（复制服务器列表） |
| Q6 | 终端 widget 重挂（4.4 方案 B），保留实例列为后续 spike | 同时保留多 widget（interop 未验证） |
| Q7 | 关闭标签无进行中传输时**不弹确认**；有时弹 | 恒弹（噪音）；恒不弹（静默丢数据） |

### 5.3 待用户拍板的三点（会阻塞实施）

1. **Q1 上限 8** 是否合适。
2. **Q4 取 A 还是 B**（A 需多一个提交与 `SessionResources` 抽象；B 有明确体验倒退）。
3. **Q6 取 A 还是 B**（若用户要求"切回来滚动缓冲还在"，A 升为必做，需 spike）。

---

## 6. 提交切分与回滚策略

四刀，每刀**独立可验证、门禁全绿**：

| # | 提交 | 内容 | 验证 |
|---|---|---|---|
| 1 | `refactor: 会话注册表（SessionRegistry）与 AppModel 委托` | 新增 registry；`connection`/`shellSession` 改只读投影；`startConnect`/`disconnect`/`cancelConnect`/`reportShellStartFailed` 委托。**UI 零改动** | 456 用例**一行不改**全绿 + 新增 registry 单测 |
| 2 | `refactor: DashboardHost 改为订阅活动会话` | 试点一个 Host，`LaunchedEffect(active?.id)` | 全绿；DashboardScreenTest 更新 |
| 3 | `refactor: 其余 Host 与终端屏迁移到会话注册表` | FilesHost / PortsHost / TerminalScreen / ServersScreen / ConnectFlowOverlays | 全绿；UI 测试更新 |
| 4 | `feat: 多标签条与关闭语义` | 标签条、`+` 入口、关闭/切换、§4.5 确认框 | 新增 UI 测试；全绿 |

**分屏（D2）不在四刀内**，作为 v0.3.0 之后的独立任务。

**回滚策略**：每刀一个提交，任何一刀出问题 `git revert <sha>` 即回到上一刀的全绿状态。
第 1 刀是**纯新增 + 委托**，回滚只需还原 `AppModel`（registry 文件可留可删，不影响编译）。

---

## 7. 测试策略

### 7.1 registry 状态机单测（`commonTest`）

新增 `shared/src/commonTest/kotlin/com/barezen/ssh/app/SessionRegistryTest.kt`，用
`FakeSshClient`/`FakeSshSession`（既有，见 `jvmTest/.../ssh/FakeSshSession.kt`——**注意在 jvmTest**，
若 registry 测试入 commonTest 需先把 fake 上移或另写一个 commonTest 版本的 fake，
**推荐**：registry 测试放 `jvmTest`，与 `ConnectFlowTest` 同处，复用现成 fake，零新增基础设施）。

覆盖（先红后绿）：

1. `connect` 返回唯一 id；列表含该会话且状态推进到 Connected；
2. 两条会话并存；`activate` 切换 `active`；
3. `close` 移除并激活左邻；关最后一个 → `activeId == null`；
4. `closeAll` 关闭所有 `SshSession`（fake 的 `closed` 标志全为 true）；
5. 达上限第 9 条被拒绝且**不改动**既有 8 条；
6. **不变量**：`Connected` 必有非空 session；非 `Connected` 必无 session（延续 `AppModelTest:87` 的锁定）；
7. 建连失败 → `Failed` 且 session 为 null；`retry` 原地复用 id；
8. `cancel` 移除 Connecting 会话且不残留协程（`job.isActive == false`）。

### 7.2 迁移等价性测试（第 1 刀的核心保险）

**新增一个显式的等价性断言组**：把 `AppModelTest` 与 `ConnectFlowTest` 现有的
`m.connection`/`m.shellSession` 断言**原样保留**——它们就是"投影与旧语义等价"的回归网。
第 1 刀**不允许修改这些断言**，只允许它们继续通过。若必须改，说明投影不等价，**停下重新设计**。

### 7.3 会话切换 UI 测试（`jvmTest`）

新增 `jvmTest/.../ui/MultiSessionTabTest.kt`：

- 两条会话 → 标签条渲染两个标题（用 testTag `session-tab-{id}` 定位，避免重名歧义）；
- 点击非活动标签 → 活动指示转移（`semantics { selected }`，沿用 `SidebarItem` 的做法）；
- 关闭按钮 → 标签消失；关闭最后一个 → 空态文案「在服务器列表选择『新建终端』以开始。」；
- 有 Running 传输时关标签 → 弹确认框（testTag `session-close-confirm`）；
- 无进行中传输时关标签 → **不**弹框（`onAllNodesWithTag(...).fetchSemanticsNodes().isEmpty()`）。

多节点歧义一律 testTag（CONVENTIONS §4.3）。

### 7.4 既有 456 用例的回归保证

- 第 1 刀：**零改动**通过（§7.2）。
- 第 3/4 刀：需要更新的既有测试（已按 CONVENTIONS §6.5 允许范围登记）：
  - `DashboardScreenTest`、`ConnectFlowTest`（断言 `shellSession` 的两处）；
  - `AppModelTest`（若第 1 刀后仍绿则不动）。
- `applyConnectionForTest` 的处置：**保留**，实现改为在 registry 中造一条假会话并激活，
  使既有 UI 测试（`ConnectFlowTest` 等直接注状态）无需重写。
- 门禁命令与基线核对按 CONVENTIONS §7（唯一验收命令），提交前 `--rerun-tasks` 强制重跑。

### 7.5 非目标测试（明确不做，避免过度工程）

- 不做多会话并发建连的**竞态**压测（Q3 允许并发，但 code path 由同一 `connectJob` per id 保证）；
- 不测 Swing interop 的多实例行为（Q6 取 B 则无此需求）；
- 不做真实 SSH 多连接的真机测试（属 T-2 范畴，已有冒烟脚手架可扩展，但不阻塞本轮）。

---

## 8. 授权文件清单（实施时以用户下发的清单为准）

**新增**：`commonMain/.../app/SessionRegistry.kt`、`jvmTest/.../app/SessionRegistryTest.kt`、
`jvmTest/.../ui/MultiSessionTabTest.kt`

**修改**：`commonMain/.../app/AppModel.kt`、`jvmMain/.../ui/shell/AppShell.kt`、
`jvmMain/.../ui/shell/ConnectFlowOverlays.kt`、`jvmMain/.../ui/screens/TerminalScreen.kt`、
`jvmMain/.../ui/screens/DashboardScreen.kt`、`jvmMain/.../ui/screens/ServersScreen.kt`、
`jvmMain/.../ui/screens/ServersScreenParts.kt`

**评估项（我认为需要，但等用户批准）**：`commonMain/.../ssh/Ssh.kt`——
**仅当** `reportShellStartFailed` 需要携带 `SessionId` 时，其实**不需要**改 `Ssh.kt`
（`SessionId` 定义在 `app/`，`Ssh.kt` 不必知道它；签名变更发生在 `AppModel`/registry 侧）。
**故本设计主张 `Ssh.kt` 零改动**——请用户在授权清单中确认这一评估。

**禁改**：`ssh/JvmSshClient.kt`、`terminal/TerminalView.kt`（除 §4.4 若选 A）、
`credentials/`、`servers/`、`settings/`、`ui/theme/`。

---

## 9. 开放项与风险登记（不假装已解决）

| # | 项 | 处置 |
|---|---|---|
| R1 | 后台会话的**远端断线探测**（需求域 3 已列开放项） | 本轮不做，登记 STATUS 技术债 |
| R2 | SwingPanel 多实例 interop（Q6 选 A 才涉及） | 需 spike；选 B 则规避 |
| R3 | 切换标签**丢失滚动缓冲**（4.4） | 必须在 UI 上诚实呈现，不承诺保留 |
| R4 | 上限 8 的选择 | 待用户确认 |
| R5 | `Connecting` 期间关标签 | `cancel(id)` 移除并掐协程；需测试覆盖（§7.1-8） |
| R6 | 多会话同时 `Connecting` 时覆盖层呈现 | **决策**：覆盖层改为**逐会话**提示（列表式），或仅显示最近一次尝试；倾向"仅显示活动会话的 Connecting"，非活动会话的连接进度在标签上以动画点表示 |

R6 需要在实施第 3 刀时定稿；本设计建议**先做最简单的**（覆盖层只反映活动会话），
并在标签上给非活动 Connecting 会话一个视觉标记。

---

## 10. 实施前的检查清单（给下一个 AI）

1. 用户已批准本 spec（尤其是 §5.3 三个待拍板点）；
2. 已拿到**书面授权文件清单**；
3. `git log --oneline -1` 与基线一致，门禁 456/0 全绿；
4. 已通读 `CONVENTIONS.md`（硬约束 / TDD / 门禁 / 子代理协议）；
5. 第 1 刀**先写 registry 测试（红）**再实现；
6. 每刀结束：门禁 `--rerun-tasks` 全绿 + `git status` 核对 + 逐个 `git add`（**禁止 `git add -A`**）。
