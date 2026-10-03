# 约束规范（CONVENTIONS）

更新：2026-10-02。**本项目唯一约束源**，取代并吸收原 `docs/superpowers/2026-10-02-dev-contract.md`（已归档）。任何子代理或开发者在写代码前必须通读；与其他文档冲突时以本文为准。违反硬约束（第 1 节）即返工。

## 1. 硬约束（零容忍）

1. **代码与提交信息零 emoji**。需要连接语义时用「至」或 `->`，不用箭头装饰字符。
2. **仓库内零 AI 工具痕迹**：不创建 `.workbuddy/`、`.superpowers/`、`.vscode/` 等目录；不写会话日志进仓库；不在 `.gitignore` 加 AI 条目（这些目录已在 `.git/info/exclude` 本地排除，保持现状即可）。
3. **禁止 `git add -A` / `git add .`**。先 `git status` 核对，逐个 add 授权文件。历史教训：曾把 AI 工作目录卷进提交。
4. 禁止渐变、禁止装饰性动效；界面文案中文，术语保留英文惯用（SSH/SFTP/Docker）。
5. **只改任务授权的文件**。发现相邻问题不擅自修，记录上报。
6. 包名一律 `com.barezen.ssh`（2026-10-02 已从 `barezen_ssh` 重构完毕，禁止出现旧包名）。
7. 功能缺失宁可显式禁用并标注原因，**不造数、不做假功能**（空态显示「—」或「连接后可…」提示）。

## 2. 混编语言边界（PRODUCT.md 已收录）

| 归 Kotlin | 归 Java |
|---|---|
| UI（Compose）、协程状态、路由 | 文本/协议解析（/proc、df 等） |
| 跨平台接口与测试 fake（commonMain） | JNA/系统调用绑定、第三方 Java 库包装 |

- Java 源目录：`shared/src/jvmMain/java/com/barezen/ssh/<包路径>/`，与 Kotlin 同包互调，Gradle 自动编译。
- **语言边界即模块边界**：Java 类不 import Compose；Kotlin UI 不直接解析协议文本——解析器产出不可变 data class，UI 只消费。
- 解析器/纯函数必须有穷举单测：合法 / 空串 / 截断 / 非法数字 / 超长输入。
- 现有 Kotlin 代码不迁移语言；不为混编而混编。
- sshj API 调用前用 javap 核实签名（jar：`~/.gradle/caches/modules-2/files-2.1/com.hierynomus/sshj/0.40.0/`）。

## 3. 目录与命名

```
com.barezen.ssh                     唯一根包
  app/        AppModel（连接状态机/路由）、Destination、SettingsModel
  ui/theme/   Color.kt 令牌（val 名称稳定）、Theme.kt（含 LocalBareZenColors /
              BareZenSpace / BareZenSize）、Type.kt
  ui/components/ Components.kt 通用控件库（Btn/Chip/Badge/Banner/Card/
              StatusDot/SegmentedControl/Switch/Slider）——**各屏只从这里取控件**
  ui/screens/ 一屏一文件；设置页按分类拆 Settings*Section.kt
  ui/shell/   AppShell 布局骨架 + *Host 接线层（模型生命周期）+
              WindowControl.kt（自建窗口 chrome 的能力接口与拖动区）
  ssh/        Ssh.kt 接口（commonMain）；jvmMain：JvmSshClient、ssh/metrics、ssh/sftp、ssh/forward
  servers/    Server 模型与仓库、连接导入导出
  settings/   AppSettings 与仓库、更新检查
  credentials/(jvmMain) 凭据存取接口与实现
```

- 测试镜像源码路径：`commonTest` / `jvmTest`。
- Compose 资源生成包固定 `com.barezen.ssh.generated.resources`（shared/build.gradle.kts `packageOfResClass`）。
- 主题令牌：只改 `Color.kt` 的值，**val 名称不得变**；组件只消费 `MaterialTheme.colorScheme`
  与 `LocalBareZenColors.current`，禁止硬编码颜色。
- **色彩角色纪律**（STATUS §3.5.2 确立，因 apple 调色板而有必要，违反即对比度不达标）：
  - `BareZenAccent`（`#0A84FF`）只做**填充/图标**；当文字用请取 `BareZenAccentOnSubtle`（`#44A0FC`）。
    原因：能让白字达 AA 的深蓝做暗底文字必然不足 4.5:1。
  - 错误文字一律取 `LocalBareZenColors.current.onErrorContainer`，不取裸 `colorScheme.error`
    （后者在 `elevated` 底与 14% tint 底上均不足 4.5:1）。
  - 任何新增文字/背景组合，必须在 `ThemeColorsTest` 的 AA 用例里补上对应断言。

## 4. TDD 与测试规则

1. 每个功能点先红后绿；禁止 skip/ignore 测试；门禁全绿才算完成。
2. 测试位置：跨平台逻辑入 `commonTest`；JVM 实现 / Compose UI 入 `jvmTest`。
3. Compose UI 测试：
   - 多节点文本歧义用 `Modifier.testTag` + `onNodeWithTag`，或 `onAllNodesWithText(x).fetchSemanticsNodes().isNotEmpty()`；
   - `assertIs<T>` 返回值，JUnit4 中包 `runBlocking<Unit>`；
   - UI 测试跑 skiko 软渲染（headless 可用），不需要 X。
4. 网络能力一律构造参数注入（参考 `UpdateChecker.getJson`、`ForwardManager.tunnelFactory`、`SftpModel.fsFactory`），测试零网络。
5. kotlinx-coroutines-test 1.11.0：`advanceUntilIdle` 不执行 backgroundScope 任务，用 `runCurrent` / `advanceTimeBy + runCurrent`。

## 5. Git 与提交

- 提交信息：`类型: 中文摘要`，类型取 feat/fix/refactor/test/docs/build；禁止 emoji、禁止 AI 协作署名。
- 一个逻辑任务一个提交；提交前门禁全绿。
- `.workbuddy/`、`.superpowers/`、`.vscode/`、`.Trash-0/` 在 `.git/info/exclude` 本地排除，永不入库。

## 6. 子代理并行协议（多次验证可行）

1. **文件集必须不相交**：每个代理一份明确授权清单（可改/禁改），越界即返工。
2. **共享文件由主代理预先改好并提交**（接口扩展、构建脚本、路由接线），代理不碰。
3. **Gradle 门禁只能串行**：代理跑门禁遇 `Timeout waiting to lock` 等 60 秒重试（最多 10 次）；遇他人文件编译错误等 2 分钟重试。
4. 代理**不做任何 git 操作**，改动留工作区，主代理审计（范围核对 + 令牌/emoji 扫描 + 独立门禁复核）后统一提交。
5. 范围外但必须改的旧测试（占位断言失效类）：先向主代理申请授权，记录在终报。

## 7. 门禁（唯一验收命令）

普通环境：`./gradlew :desktopApp:compileKotlin :shared:jvmTest`

受限沙箱（无 X、`/root` 不可写）必须完整带环境与 init 脚本：

```
HOME=/home/lin JAVA_HOME=/home/lin/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2 \
GRADLE_USER_HOME=/home/lin/.gradle MALLOC_ARENA_MAX=1 \
timeout -k 15 900 ./gradlew --no-daemon --init-script /home/lin/tmp/barezen-ssh-init.gradle \
:desktopApp:compileKotlin :shared:jvmTest
```

- init 脚本在项目外 `/home/lin/tmp/barezen-ssh-init.gradle`（tmpfs 会丢，丢后按 DEVELOPMENT.md 第 2.1 节重建）。
- `--rerun` 是 per-task flag 必须紧跟任务名；多任务强制重跑用 `--rerun-tasks`。
- 当前基线：**608 例 / 0 失败**（2026-10-03 界面重设计与三轮补齐后；重设计前为 567）。
  低于此数或出现失败即门禁不过。
  重设计触及 33 例（词表/布局/令牌变更），变更台账见 `docs/STATUS.md` §3.5.4。
