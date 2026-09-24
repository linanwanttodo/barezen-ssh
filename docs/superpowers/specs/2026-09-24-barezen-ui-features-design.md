# BareZen-SSH 界面与功能设计（UI + Features Design）

- 日期：2026-09-24
- 状态：已批准（原型与功能规划清单均已逐节确认）
- 原型：`docs/ui/prototype.html`（六屏 + 终端 AI 分栏，hash 路由可点击）
- 字体/图标：`docs/ui/fonts/`（JetBrains Mono 三字重 + Material Symbols Outlined，均已本地化）

## 1. 背景与目标

BareZen-SSH 是自研跨平台桌面 SSH 客户端（Kotlin / Compose Multiplatform Desktop），单用户自用。成功标准：日常运维全程无需再打开任何其他 SSH 工具，并提供与终端同会话的 AI 运维侧栏。

## 2. 参考与授权（AGPL 注意事项）

- 视觉与布局 1:1 参考 MaidKit（Flutter + Material 3，AGPL-3.0，位于 `/home/lin/桌面/MaidKit`）。
- **只参考视觉与中文文案，不搬运任何 Dart 源码**；本项目 HTML 原型与后续 Kotlin 代码均为自写。
- 中文文案以 MaidKit `assets/translations/zh-CN.json` 为权威词表照抄；产品名处替换为 BareZen。
- MaidKit `AGENTS.md` 约束同步生效于视觉决策：quiet / functional，无渐变、发光、玻璃拟态、表演式动效；间距走 4/8/12/16/24/32；层次靠边框与对比，不靠阴影特效。

## 3. 视觉规范

- 主题：Material 3 暗色，`ColorScheme.fromSeed(0xFF0F766E)`（tonalSpot）。
- 关键色值（已双路计算交叉验证，与原型 `:root` 一致）：
  `primary=#81d5cb`、`on-primary=#003733`、`primary-container=#00504a`、
  `secondary-container=#324b48`、`surface=#101413`、`on-surface=#e0e3e1`、
  `surface-container-low=#191c1c`、`surface-container=#1d2020`、
  `surface-container-high=#272b2a`、`surface-container-highest=#323535`、
  `outline=#899391`、`outline-variant=#3f4947`、`tertiary=#aec9e6`、`error=#ffb4ab`。
- 字体：JetBrains Mono（界面 + 终端，400/500/700），中文回退 Noto Sans CJK / WenQuanYi；字体随包分发，Linux 下 fontconfig 兜底。
- 图标：Material Symbols Outlined，woff2 本地化，`FILL` 随选中态切换。
- 终端配色：复刻 MaidKit `terminal_color_scheme.dart`（bg `#1e1e1e`、fg `#cccccc`、绿 `#0dbc79`、蓝 `#2472c8`、红 `#cd3131`、暗 `#8a8a8a`）。

## 4. 信息架构（六屏 + AI 分栏）

hash 路由，NavigationRail 外壳（目的地：服务器 / 终端 / 文件 / 仪表盘；trailing：端口转发 badge / 设置），内容区 `surface` + topLeft 12px 圆角。

| 路由 | 屏 | 要点 |
|---|---|---|
| `#home` | 服务器 | 横幅 + 搜索 + 标签过滤 + 320px 卡片网格（状态/延迟/微瓦片/操作） |
| `#terminal` | 终端 | 标签条 + 终端 + 状态栏；**默认含右侧 360px AI 分栏**，`smart_toy` 可收起 |
| `#sftp` | 文件 | 本地/远程双栏 + 路径栏 + 底部传输进度条 |
| `#dash` | 仪表盘 | 四指标瓦片 + CPU/内存/网络折线图 + 磁盘条形（确定性波形数据） |
| `#ports` | 端口转发 | M3 表单式新建向导 + 转发列表 |
| `#settings` | 设置 | 分类导航（8 项）+ `maxWidth 720` 内容列 |

设置分类顺序：外观 / 终端 / 连接 / **智能助手** / 凭据 / 存储 / 更新 / 关于。

## 5. 功能规划（A–E，已批准）

### A · SSH 与终端（P0）
- A1 服务器卡片：名称/地址/标签/在线状态；增删改查、搜索、标签过滤。
- A2 认证：密码、私钥（ed25519 / RSA）；凭据引用一律走系统钥匙串。
- A3 终端：Jediterm 渲染；选中即复制、Shift+Insert 粘贴、可选 sudo 密码自动填入。
- A4 生命周期：连接中/已连接/断开语义状态、SSH 往返延迟、断线自动重连。
- A5 底部状态栏：连接 / 延迟 / 负载 / 内存 / 运行时间。

### B · SFTP（P0）
- B1 双栏本地/远程浏览：路径栏、后退/前进/刷新/搜索。
- B2 上传/下载、传输队列 + 进度 + 取消。
- B3 同名冲突策略：每次询问 / 覆盖 / 重命名（设置可配）。
- B4 新建文件夹、重命名、删除；传输完成通知。

### C · 端口转发与凭据（P0）
- C1 本地 / 远程 / 动态（SOCKS5）三类转发；启停开关；rail badge 计数。
- C2 转发表单：名称 / 类型 / 绑定地址 / 端口。
- C3 凭据库：密码与密钥对；Linux Secret Service、Windows Credential Manager、macOS Keychain 薄适配；UI 明示「已存入系统钥匙串」。
- C4 使用计数（N 台服务器使用）；导出默认脱敏。

### D · 工作区增强（P1）
- D1 多标签：新建 / 关闭 / 中键关闭 / 切换；标签含图标与服务器名。
- D2 分屏：向右 / 向下拆分，每格独立终端。
- D3 仪表盘：CPU / 内存 / 负载 / 磁盘 / 网络实时图表；SSH 采集 `/proc`、`df`、`uptime`；10s 刷新。
- D4 自动更新：启动检查、稳定/预览频道、下载后提示重启；数据源为 GitHub Releases。

### E · AI 运维（P1，新增范围）
- E1 侧栏：终端右 360px 常驻（非悬浮），bg = `surface-container-low`，1px 分隔线；标签条 `smart_toy` 一键收起/展开。
- E2 会话管理：居中标题下拉（新建 / 切换会话，当前项 ✓）；⋯ 菜单（**压缩对话** / 删除对话）。
- E3 对话流：用户气泡右对齐 `secondary-container`；助手左对齐 `surface-container`；**工具执行卡**带「自动批准」徽章、可展开输出；**审批卡**含标题、mono 命令、`目标：<服务器>`、「批准并运行」/「拒绝」。
- E4 输入区（Codex 式 composer）：
  - 上行提示文本（词表 `agentPromptHint`）；
  - 下行工具行：`+` 添加上下文 ｜ **策略胶囊**（shield 循环：始终询问 → 自动审查 → 自动批准）｜ 模型内嵌选择器（含「管理模型…」直达设置）｜ 圆形发送钮；
  - 待审批期间输入进入**排队态**（提示「助手正在执行…」，按键排队，完成后送入）——IDEA 式终端行为。
- E5 提供方配置（设置 · 智能助手）：OpenAI 兼容 Base URL + API Key（存系统钥匙串）、获取模型列表、模型增删、默认模型。
- E6 命令执行策略（三选一，设置与胶囊同一状态）：
  1. **始终询问** — 每次运行前都展示提议的操作供审查。
  2. **自动审查** — 只读或助手标记为安全的操作直接运行，其余需要审查。
  3. **自动批准** — 直接运行每个提议的操作，无需询问（YOLO）。
- E7 工具集（本期）：**在当前会话执行命令**（一个工具覆盖运行/读取，如 `cat`）。写入文件、执行脚本类工具 P2；MCP 与 Agent 技能不做。
- E8 与终端同会话（IDEA 式，核心设计）：
  - AI **不新建 SSH 连接**：批准后的命令直接注入当前终端 shell 的 stdin，输出落在真实滚动缓冲区，可滚动审计；
  - AI 上下文 = 直接读 Jediterm 屏幕 + 滚动缓冲区（含用户输入与全部输出）；
  - AI 空闲时终端完全归用户；执行期间输入排队并显示细进度指示（复用 spin，不弹遮罩）；
  - 输出边界用 prompt / sentinel 标记判定，拿不到 PS1 时回退超时截取；
  - 遇到 sudo 等交互式提示不代答，标记「需要你在终端处理」交还控制权。

## 6. 技术方案（方案 1，已批准）

- UI：Kotlin + **Compose Multiplatform Desktop**。
- SSH/SFTP/转发：**sshj**。
- 终端组件：**Jediterm**（自有 Pty 代理，支持 stdin 注入与缓冲区读取，服务 E8）。
- 钥匙串：薄适配层 —— libsecret（Linux）/ Windows Credential Manager（Win）/ Security framework（macOS）。
- 打包：`jpackage`（deb + AppImage / nsis / dmg）。
- 自动更新：GitHub Releases 自托管通道。

### 6.1 CI / 构建（已批准）
- GitHub Actions **三平台矩阵**（ubuntu / windows / macOS），tag push 触发 `jpackage` 构建，产物附到 GitHub Release。
- 自动更新（D4）读取同一 Release。
- macOS 产物自用**不签名/公证**（首次打开右键打开）；签名与公证 P2。

## 7. 非功能需求

- 无障碍：WCAG AA（正文 ≥ 4.5:1，大字 ≥ 3:1）、可见焦点环、Tab 序符合视觉序、色彩不作唯一状态指示。
- 动效：仅状态传达（150–250ms），尊重 `prefers-reduced-motion`。
- 主题：v1 仅暗色（「跟随系统」= 暗色）；浅色 P2。
- 安全：私钥 / 密码 / API Key 均不落明文数据库，全部进系统钥匙串；导出脱敏。
- 性能：冷启动 < 2s；终端滚动 60fps 目标。

## 8. 里程碑

| 里程碑 | 内容 |
|---|---|
| M0 | 项目骨架、外壳导航、色板与字体、原型对照 |
| M1 | SSH 连接 + 终端（A） |
| M2 | SFTP（B） |
| M3 | 端口转发 + 凭据（C） |
| M4 | 多标签/分屏 + 仪表盘 + 自动更新（D） |
| M5 | AI 侧栏（E，含同会话执行与审批流） |
| M6 | GitHub Actions 三平台打包 + 无障碍走查 + 文档 |

## 9. 范围外（本期不做）

MCP 服务器、Agent 技能、写文件/执行脚本类 AI 工具、会话云同步、多用户、浅色主题、macOS 签名公证、应用商店上架；MaidKit Dart 源码不搬运。

## 10. 遗留待办（非阻塞）

- `desktopApp/build.gradle.kts`：`nativeDistributions.packageName` → `BareZen-SSH`。
- README：补 fontconfig 字体排查条目。
- 仓库尚无首次提交（提交信息需先给用户过目）。
