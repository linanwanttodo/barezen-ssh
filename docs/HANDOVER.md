# 交接总纲（HANDOVER）

更新：2026-10-02（v1.0 收官批次完成，HEAD 见 git log）。写给接手本项目的 AI 或开发者：读完本文即可安全开工。

## 1. 项目是什么

BareZen-SSH：跨平台（Linux/Windows/macOS）桌面 SSH 客户端，Kotlin Multiplatform（JVM-only）+ Compose Desktop。单用户自用工具，中文界面，暗色优先（DBX 设计令牌体系）。产品定位与设计原则见仓库根 `PRODUCT.md`。

技术栈：Kotlin 2.4.20 / Java 21 / Compose Multiplatform 1.12.1 / sshj 0.40.0 / JediTerm 3.73 / Gradle wrapper 9.5.1。

## 2. 现状快照（2026-10-02，v1.0 收官批次完成后）

- **可运行**：`./gradlew :desktopApp:run` 可启动；连接、终端、仪表盘真数据、本地/远程端口转发、SFTP 文件管理、设置六分类、AI 运维侧栏（BYOK）全部真实可用。
- **安装包**：`./gradlew :desktopApp:packageDistributionForCurrentOS` 产出 `barezen-ssh_1.0.0_amd64.deb`（Linux 实测；macOS/Windows 需各自平台）。
- **测试门禁**：556 用例 / 0 失败（`shared` 模块 jvmTest，--rerun-tasks 全量）。
- **版本**：1.0.0，单一真相源 `gradle.properties` `barezen.version`（BuildInfoTest 守卫一致性）。
- **未完成（诚实积压）**：钥匙串原生路径真机验证、远端转发真实拓扑复验、JNA + Windows 桥、亮色主题与分屏打磨。完整清单见 STATUS.md。

## 3. 开工前必须做的事

1. **通读 CONVENTIONS.md**——硬约束在那里，违反即返工。最重要的三条：
   - 代码与提交信息**零 emoji**、仓库内**零 AI 工具痕迹**；
   - **禁止 `git add -A`/`git add .`**（曾因此把 AI 工作目录提交进历史）；
   - 语言边界：UI/协程状态用 Kotlin，解析/JNA/库包装用 Java（`shared/src/jvmMain/java/`）。
2. **确认构建门禁可用**（命令与排障见 DEVELOPMENT.md 第 2/7 节；受限沙箱必须带 init 脚本与环境变量）。
3. **从 STATUS.md 核对基线**，跑一次门禁确认全绿再动手。

## 4. 工作流（沿用既定方法论）

1. 从 ROADMAP.md 领取任务（按优先级顺序，除非用户改派）。
2. 任务开工前：明确授权文件清单（只改清单内文件）；写测试先行（红），实现转绿。
3. 完成标准：门禁全绿 + 更新 STATUS.md + 提交（`类型: 中文摘要`，见 CONVENTIONS 第 5 节）。
4. 使用子代理并行时：不相交文件集 + 共享文件由主代理预先改好 + Gradle 门禁只串行跑（锁冲突重试协议见 CONVENTIONS 第 6 节）。

## 5. 领域要点（少踩坑）

- **sshj 0.40.0**：`Session.Command` 无 `waitFor()`（用 `join(timeout)`）；`SSHClient` 非线程安全（`JvmSshSession` 用 `execLock` 串行化 exec/pingMs）；无动态（SOCKS5）转发器——转发 UI 上该选项已禁用，不是遗漏。
- **sshj API 改动前先 javap 核实**（jar 在 `~/.gradle/caches/modules-2/files-2.1/com.hierynomus/sshj/0.40.0/`）。
- **Compose 资源生成包**固定为 `com.barezen.ssh.generated.resources`（shared/build.gradle.kts `packageOfResClass`），新增字体/图片后从这里 import。
- **主题令牌**：只改 `ui/theme/Color.kt` 的值，val 名称不得变；组件一律消费 `MaterialTheme.colorScheme`，禁止硬编码颜色。
- **测试铁律**：不造数（未连接/无数据显示「—」或禁用态）；多节点文本断言用 `testTag` 或 `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()`；网络能力一律构造注入，测试零网络。
- **`.workbuddy/`、`.superpowers/`、`.vscode/`、`.Trash-0/`** 均为本地排除，永不入库。

## 6. 接下来的任务（按优先级）

v1.0 收官四批次（T-6 UI 对齐 / T-7 AI 侧栏 / T-8 打包 / 终审）已全部完成。剩余为需要用户桌面机或外部资源的事项：

1. **用户侧验证清单**（见 STATUS.md §2.1）：钥匙串原生路径、deb 安装冒烟、AI 侧栏 BYOK 实连。
2. **远端转发真实拓扑复验**（需一台与开发机互通的服务器）。
3. 其余小项见 ROADMAP.md「小项池」（JNA/Windows 桥、亮色 Warning 变体、SOCKS5 动态转发）。

## 7. 向用户提问的既定待决点

- VPS 测试凭据（host/port/user/认证方式）——T-2 需要。
- P6 AI 侧栏做不做；若做，命令注入的审批边界怎么定。
- UI 基线已定：DBX 令牌（2026-10-02 完成），不要再提议换设计体系。
