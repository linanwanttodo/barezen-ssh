# 下一阶段路线图：全功能补全（P2–P6）+ DBX 风格 UI 重设计

> 状态：** groundwork，待用户确认决策点后进入 spec → plan 流程**（沿用 superpowers 方法论）。
> 上游：P1「设置全域 + 持久化」已完成（HEAD `e9adf03`，28 suites / 149 tests / 0 failures）。

## 1. 用户本轮诉求（2026-10-02 凌晨）

1. 继续优化测试、完善代码（已做一轮：补 `AutoConnectTest` 3 例 + 更新源启用用例，`e9adf03`）。
2. 软件很多功能是空壳 → **所有功能要设计完成、全部可用**（对应原设计 P2–P6）。
3. 有一台可登录的 SSH VPS 可用于真实功能测试（**凭据待用户提供**）。
4. UI 重新设计：**以 DBX（github.com/t8y2/dbx）为基准**，用户本机已安装（`/usr/bin/dbx`）。

## 2. DBX 设计语言（已提取其权威 tokens.css，2026-10-02）

DBX = Tauri 2 + Vue 3 + Rust 的开源数据库工作台，AGPL-3.0。设计权威文件：
`apps/desktop/src/styles/tokens.css`（:root 浅色 + .dark 深色两套）。

**深色模式核心令牌**（对我们最有参考价值）：

| 令牌 | 值 | 语义 |
|---|---|---|
| background | `rgb(19 20 22)` | 近黑微冷主底 |
| card | `rgb(27 27 30)` | 卡片/面板 |
| sidebar | `rgb(25 25 28)` | 侧栏（比 content 略深一层的分层表面） |
| foreground / muted-foreground | `rgb(215 215 219)` / `rgb(151 152 157)` | 两级文字 |
| primary | `rgb(208 208 214)`（中性！） | **主按钮是浅灰不是彩色**，primary-foreground 为深底 |
| border / input | `rgb(110 110 114 / 0.28 / 0.34)` | 低透明度细边框 |
| destructive / success / warning / info | `rgb(243 98 95)` / `rgb(74 222 128)` / `rgb(251 191 36)` / `rgb(96 165 250)` | 语义色一律配 12–16% 透明底色（error-bg 等） |
| 圆角 | sm/md=4px，lg/xl=6px | 极克制的小圆角 |
| 字体 | Geist Variable + PingFang SC；mono: Fira Code / Cascadia / JetBrains Mono | |
| 分层表面 | chrome / content / editor-toolbar / gutter / sidebar-header | 窗口铬层与内容区分层 |

**结构特征**（从组件目录归纳）：左侧连接树 + 顶部 tab 栏（appTabBar）+ SQL 编辑器工作区
（sqlEditorWorkspace）+ 结果网格（虚拟滚动、行内编辑）+ AI 助手侧栏。支持可安装多主题
（theme-soft / theme-graphite 等）与 `data-corner-style` 圆角变体。

## 3. 功能补全范围（对应上游需求 P2–P6，全部待 spec）

| 子项目 | 空壳现状 | 依赖 | 备注 |
|---|---|---|---|
| P2 主机指标 | 仪表盘四指标「—」、两图占位、终端状态栏 | 扩展 SshSession（exec 通道跑 top/vmstat 等） | **可先做**，用 VPS 真测 |
| P3 端口转发 + 凭据 | 端口转发屏空、凭据分类占位 | sshj 隧道 API；OS 钥匙串 | 凭据库落地后 sudoAutofill 才能生效 |
| P4 SFTP 文件传输 | 文件传输屏空 | sshj SFTP；conflictPolicy 消费者在此 | 与 P2 共享 exec 通道扩展 |
| P5 多标签 + 分屏 | 终端 `+`、分屏无 | AppModel 单会话 → 会话注册表重构 | 架构改动最大 |
| P6 AI 助手 | AI 侧栏占位 | LLM 接入 + 审批流 + 安全模型 | **待用户决策是否做**（需 LLM key） |
| 浅色主题 | 「浅色（未实现）」禁用项 | 无 | DBX tokens 有现成浅色体系，UI 重设计时一并做 |

## 4. UI 重设计方向（草案，待确认）

以 DBX tokens.css 为**设计权威**映射到 Compose：
1. 新建 `ui/theme/DbxTokens.kt`：把上表逐项落成 Compose `Color`/`Dp` 常量，深浅两套；
2. 分层表面体系：chrome（标题栏/侧栏容器）→ content（内容区）→ 卡片，替换现有单层 surface；
3. 中性主色 + 语义色（success/warning/info/destructive 各配 12% 透明底）替换现有 accent 体系；
4. 小圆角统一 4/6dp；边框统一低透明度；
5. 浅色主题随双套 token 一并落地（消费 D2 遗留的 when 分支）；
6. 布局范式对齐 DBX：左树/列表 + tab 工作区 + 底部状态栏。

## 5. 待用户确认的决策点（下次会话第一件事）

1. **VPS 测试凭据**：host/port/user + 私钥或密码（用于 P2 指标、P4 SFTP 的真实联调；只在内网使用，不落盘）。
2. **UI 基准确认**：是否按 §4 以 DBX tokens 全面替换现有 graphite 主题（含浅色）？还是要渐进式？
3. **优先级排序**：建议 P2 → P3 → P4 → P5 → P6（P2 无协议依赖可立即开工，P5 架构改动最大放后）。
4. **P6 AI 助手**：做否？做的话 LLM 接入方式（本地 Ollama / API key）。
