# P5 D2 设计 · 终端分屏（交接版）

> 状态：**待用户批准**（批准前不动任何产品代码，同 T-4 先例）
> 目标读者：**执行本设计的下一个 AI**（不共享本设计的对话上下文，本文自包含）
> 上游需求：`docs/superpowers/specs/2026-09-25-ui-redesign-requirements.md` 域 3 第 6 条（D2：向右/向下拆分，每格独立终端）
> 前序设计：`docs/superpowers/specs/2026-10-02-session-registry-design.md`（D1 多标签，已实施；本文在其产出之上叠加）
> 任务书：`docs/ROADMAP.md` T-4 遗留项「分屏（P5 D2 形态）」
> 设计权威：`docs/ui-redesign/index.html` + `styles.css`（配色/间距/圆角/词表一律以它为准）

---

## 0. 执行须知（先读这一节）

### 0.1 你要交付什么

在多标签（D1，已上线）之上，给终端内容区加**分屏**：用户可以把焦点格**向右**（纵向分栏）或**向下**（横向分栏）拆成两格，每格是一条独立 SSH 会话的终端；拖动分隔条调比例；关闭格即关闭该格会话；只剩一格时回到现在的单格形态。

**核心语义决定（需用户确认的默认值）**：「拆分」在焦点格执行，新格**立即以同一台服务器新建一条会话**（复用该服务器已存认证），行为对齐 iTerm2/Terminator「拆分即新连接」的惯例。

- 理由：备选方案「新格先空着、内嵌服务器选择器」要新增一整块选择 UI 与「连接在途时关闭格」的取消语义，范围翻倍；「同机新会话」零新 UI、语义可预期（每格独立终端，正是需求原文）。
- 同机重复连接是 sshj 多 `SSHClient` 实例，D1 已验证资源与心跳各自独立（SessionRegistry 上限 8 仍然生效）。
- 若用户否决此默认值，备选实现是内嵌选择器，本文 §3.6 给了它的接缝，不影响其余设计。

非目标（本轮明确不做，别自行发挥）：

| 不做 | 原因 |
|---|---|
| 拖拽把会话在格间移动 / 格重排 | 需求未要求；树模型可后补 |
| 超过两层嵌套的复杂布局 | 二叉树天然支持任意深度，但 UI 只提供「拆分焦点格」一个动作，不造布局编辑器 |
| 分屏布局持久化 | 布局是会话期 UI 状态；设置里存布局无需求支撑 |
| 标签脱离窗口（tear-off） | 桌面多窗口不在本轮 |
| AI 侧栏（M5） | 未拍板（T-5） |

### 0.2 当前基线（开工前先自己确认）

```bash
cd /home/lin/All_projects/Javaproject/BareZen-SSH
git log --oneline -1     # 期望 HEAD = 2614c81 或其继任者
```

基线是 **76 suites / 567 用例 / 0 失败**（批次 B2 后）。低于此数或有失败，先停下来问用户，不要在红基线上开工。

### 0.3 风险声明

1. **多 TerminalView 同屏**：每格一个 JediTerm widget + 一条 shell 通道，同时活跃 2–4 格是常规用量；性能不是本轮目标，但 **DisposableEffect 释放路径必须一格一份**，不允许共享可释放状态（现状 `ConnectedPane` 的 `remember(session, snapshot.id)` 已是按会话隔离的，保持）。
2. **不变量**（D1 设计 §3 锁定，继续成立）：`connection` 与 `shellSession` 同进同退；关闭会话释放全部资源；`SessionRegistry` 是会话唯一真相源。**分屏布局不是真相源**——它只引用 `SessionId`，会话增删的真相永远在 registry（§3.2 的剪枝规则）。

---

## 1. 现状（D1 之后的终端屏）

`TerminalScreen.kt` 的结构：

```
Column
├─ TerminalTabStrip        // 36dp：每会话一标签，单击=activate，关闭按钮/中键=close，+ 跳服务器列表
├─ RestartNotice(可选)      // 活动会话切换过的一次性重挂提示（notifiedIds 记账）
├─ Box(weight 1f)          // ★ 本轮改造点：现在只渲染 registry.active 一个快照的状态机
│   └─ when (active) …     // null/Disconnected=EmptyHint；Connecting；Failed；Connected→ConnectedPane
└─ ConnectionStatusBar      // 28dp：只渲染活动会话状态
```

`ConnectedPane` → `ShellStartGuardedSession`（startShell 抛错转 Failed 回传**本会话 id**）→ `TerminalView(guarded, settings, fillMaxSize)`。会话与 widget 一对一，这份配对就是「格」的雏形——分屏只是让**多份配对同屏**。

`SessionRegistry`（commonMain）：扁平 `sessions` 列表（插入序=标签序）、`activeId`、`connect()` 建连即激活、`close(id)` 收口全部资源、上限 8。**本轮不改 registry 的会话语义**，只在其上叠一层布局状态。

---

## 2. 数据模型：布局是引用，不是真相

### 2.1 PaneNode（commonMain，新文件 `app/PaneLayout.kt`）

```kotlin
/** 终端内容区的分屏布局；只引用 SessionId，会话真相在 SessionRegistry。 */
sealed interface PaneNode {
    data class Leaf(val sessionId: SessionId) : PaneNode
    /** horizontal=true 表示向右拆分（左右两栏），false 表示向下拆分（上下两栏）。 */
    data class Split(
        val horizontal: Boolean,
        val first: PaneNode,
        val second: PaneNode,
        val ratio: Float = 0.5f,   // first 占比，钳制在 [MIN_RATIO, 1-MIN_RATIO]
    ) : PaneNode
}
```

`PaneLayoutState`（AppModel 持有，`mutableStateOf` 承载根节点）：

```kotlin
class PaneLayoutState {
    var root: PaneNode?              // null = 经典单格（=现状渲染路径）
    val focusedId: SessionId?        // 焦点格会话 = registry.activeId（单一事实，见 §3.4）
    fun splitFocused(horizontal: Boolean, newSessionId: SessionId)
    fun closeLeaf(id: SessionId)     // 剪枝：Leaf 移除后 Split 收缩；根收缩到 Leaf 即回到 null
    fun replaceDetached(id: SessionId, with: SessionId)  // 见 §3.6 备选；默认实现可先留空
}
```

### 2.2 与 SessionRegistry 的两条铁律

1. **布局引用必须可满足**：任何时刻 `root` 里出现的 `SessionId` 都必须能在 `registry.sessions` 里找到。实现方式：`AppModel.registryClose(id)`（现有 `close` 的唯一 UI 入口）先 `paneLayout.closeLeaf(id)` 再 `registry.close(id)`，两条写同帧完成，中间态不可观测。
2. **registry 是会话增删唯一入口**：拆分动作本身**不创建**会话——它先经 registry 正常建连（`connect(server, auth)` 返回新 id），再 `splitFocused(horizontal, newId)`。建连失败新格显示 `FailedPane`（复用现组件，重试也只作用于该格会话）。

---

## 3. 交互设计

### 3.1 拆分入口

标签条 `+` 旁新增两个 IconButton（32dp，同现有尺寸语言）：「向右拆分」「向下拆分」（`contentDescription` 同文案）。仅当存在焦点格且其会话处于可拆状态（非 null）时可用；窗口内容区小于 **560×360dp**（=两倍最小格）时禁用并给 tooltip 说明，不留一个拆出来就挤死的入口。

### 3.2 拆分动作

1. 取焦点格会话的 `server` 与已存认证，`registry.connect(server, auth)` 得 `newId`（此时 registry 会把 activeId 切到新会话——保持，见 §3.4）。
2. `paneLayout.splitFocused(horizontal, newId)`：把焦点格所在的 Leaf 替换为 `Split(horizontal, 原Leaf, Leaf(newId))`。
3. 新格渲染新会话自己的状态机（Connecting→Connected/Failed），**shell 重挂提示在该格首次挂载时出现一次**（§3.5）。

### 3.3 分隔条

- 宽 6dp 命中区、可见线 1dp `outlineVariant`；hover/拖拽时光标 `Cursor.Move`（横向分栏用 resize 左右向）。
- 拖拽改 `ratio`，松手落盘到状态；`ratio` 钳制保证每格不小于 **280×180dp**（约 24 列×9 行的等宽字号下限；低于此限拖不动，不是缩字）。
- 分隔条双击 = 恢复 0.5。不造吸附、不造记忆。

### 3.4 焦点模型：焦点格 = activeId，单一事实

- **单击任一格**（终端区任意处）= `registry.activate(该格会话)`；标签条选中态、状态栏、焦点描边全部跟着 activeId 走——**不新增第二套焦点状态**。
- 焦点格描边：复用 `focusRing` 的 2dp `primary`，常显在非活动格边界内侧 1dp？**不**——非焦点格不加任何描边（本项目「不做悬浮/发光」约定），只用**标签条选中态 + 状态栏**指示焦点。若实测不可辨，允许给非焦点格终端底色叠 `AccentSubtle`（灰阶 token），不加彩色。
- 键盘：Tab 焦点环行为不变（`focusRing` 已覆盖可点行）；格间焦点移动本轮不绑快捷键（无全局快捷键基建，不为此新建）。

### 3.5 重挂提示的去处

现状：活动会话**切换**过才提示一次。分屏后「挂载」多了新来由（拆分、剪枝收缩），语义统一为：**一条会话的终端 widget 在某格首次挂载时，该格顶部显示一次提示**；同会话同格生命周期内不重复（notifiedIds 按 sessionId 记账，逻辑从屏级下沉到格级组件）。措辞不变（如实：服务端新 shell，滚动缓冲不可保留）。

### 3.6 备选接缝（若用户否决「同机新会话」默认值）

新格渲染内嵌服务器下拉（数据源 = ServersScreen 同一 repository）→ 选择后 connect；`PaneLayoutState` 增加「未绑定格」（Leaf 无 sessionId）与「连接在途取消」路径（关格时若会话已建需一并 close）。`replaceDetached` 即为此预留。此路径+1 个组件、+2 条测试，范围扩大约 40%。

### 3.7 关闭语义

| 动作 | 效果 |
|---|---|
| 格内会话断开/失败 | 该格显示对应状态（EmptyHint/Failed），**不自动收格**——给用户重试入口 |
| 关格（格角 ×，hover 显示） | `registryClose(该格会话)` → 剪枝收缩 |
| 标签条关标签 | 同上：该会话若在某格，格被剪掉；标签与格同源同灭 |
| 收缩到单格 | `root=null`，回到现状渲染路径（代码上单格就是 null 布局的渲染） |

---

## 4. 渲染层改造（`TerminalScreen.kt`）

```
Box(weight 1f)
├─ root == null  → 现状 when(active) 单格路径（一字不动）
└─ root != null  → PaneTreeRenderer(root)
     ├─ Leaf     → PaneCell(snapshot)   // 复用现有 when(st) 四态 + ConnectedPane
     └─ Split    → Row/Column( weight(first)·Divider·weight(second) )
```

- `PaneCell` = 现内容区 `when` 块原样提取成组件 + 格级点击激活 + 关格按钮 + 格级 RestartNotice。
- `ConnectionStatusBar` 不动（继续只渲染 activeId，即焦点格）。
- 标签条不动（仍是全会话扁平列表；分屏不改变标签数）。
- `key()` 锚定：Split 递归层与 Leaf 一律 `key(sessionId)`，剪枝/收缩时槽位复用不错位。

---

## 5. 测试计划（先红后绿，门禁命令不变）

| # | 层 | 用例 |
|---|---|---|
| 1 | commonTest `PaneLayoutTest` | splitFocused 在焦点 Leaf 处生成正确方向 Split；ratio 默认 0.5 |
| 2 | commonTest | closeLeaf 剪枝：兄弟 Leaf 顶替、Split 收缩、根收缩到 Leaf → root=null |
| 3 | commonTest | ratio 钳制：拖拽值被钳在 [MIN, 1-MIN] |
| 4 | commonTest | 不变量：closeLeaf 后 root 中无该 id（registry close 前置剪枝的契约测试） |
| 5 | jvmTest UI | 拆分后两格各挂一个 TerminalView（testTag 计数）；焦点切格 = 标签选中态同步 |
| 6 | jvmTest UI | 关标签 → 对应格消失、单格收缩回经典路径 |
| 7 | jvmTest | 建连失败的格：FailedPane 重试只作用于该格会话（连带 D1 的 reportShellStartFailed(id) 语义） |

UI 测试沿用既有教训：`waitUntil` 显式 timeout、`performTextClearance()`、测试零外部网络（Fake ssh client 注入）。

## 6. 实施切片（每片门禁全绿才进下一片）

1. `PaneLayout.kt` + 用例 1–4（纯逻辑，零 UI 风险）。
2. `PaneCell` 提取 + root==null 路径回归（现有 UI 测试不许红）。
3. 渲染器 + 拆分/关格动作 + 用例 5–7。
4. 分隔条拖拽 + 最小尺寸钳制 + 用例 3 的 UI 侧验证。

## 7. 验收清单（给用户的走查单）

- [ ] 向右/向下拆分各一次，两格独立登录输出互不串扰
- [ ] 拖分隔条：比例可调、拖不过最小格限、双击回半
- [ ] 点非焦点格：标签选中态与状态栏跟随
- [ ] 关标签/关格：格消失、资源收口（服务器侧 who/uptime 验证会话关闭）
- [ ] 收缩到单格后行为与升级前一致（回归）
- [ ] 亮色板下分隔线、焦点指示可辨
