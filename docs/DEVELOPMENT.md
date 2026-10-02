# 开发指南（DEVELOPMENT）

更新：2026-10-02。面向在本仓库写代码的人（含子代理）。硬约束的完整清单见 `docs/superpowers/2026-10-02-dev-contract.md`，本文是其可执行版。

## 1. 环境

| 项 | 要求 |
|---|---|
| JDK | 21（Gradle 自动管理在 `~/.gradle/jdks/`） |
| 构建 | 仓库自带 Gradle wrapper 9.5.1，不要用系统 gradle |
| 运行 | `./gradlew :desktopApp:run`（桌面环境） |

## 2. 常用命令

```
./gradlew :desktopApp:run          # 启动桌面应用
./gradlew :desktopApp:compileKotlin :shared:jvmTest   # 门禁（见下）
./gradlew :shared:jvmTest --tests "com.barezen.ssh.settings.*"   # 跑单个包
```

### 2.1 门禁（提交前必须全绿）

受限环境（无 X、HOME 不可写、`/root` 只读）下的完整配方：

```
HOME=/home/lin JAVA_HOME=/home/lin/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2 \
GRADLE_USER_HOME=/home/lin/.gradle MALLOC_ARENA_MAX=1 \
timeout -k 15 900 ./gradlew --no-daemon --init-script /home/lin/tmp/barezen-ssh-init.gradle \
:desktopApp:compileKotlin :shared:jvmTest
```

- init 脚本在项目外 `/home/lin/tmp/barezen-ssh-init.gradle`：给 Test JVM 加 `-Duser.home=/home/lin`（skiko 写字体缓存）与 `-Djava.awt.headless=true`（无 X 环境跑 Compose UI 测试）。脚本随 tmpfs 丢失时按此重建。
- `--rerun` 是 per-task flag 必须紧跟任务名；多任务强制重跑用 `--rerun-tasks`。
- 不要并发跑多个 Gradle 进程（文件锁冲突）。
- `assembleDebug` 类任务在此环境不可用（`/root/.android` 不可写），门禁以上述两任务为准。

## 3. 目录与命名约定

```
com.barezen.ssh                     # 唯一根包（禁止 barezen_ssh）
  app/        AppModel、Destination、SettingsModel
  ui/theme/   Color.kt 令牌、Theme.kt 装配、Type.kt 字体
  ui/screens/ 一屏一文件；设置页按分类拆 Settings*Section.kt
  ui/shell/   AppShell 布局骨架与悬浮层
  ssh/        Ssh.kt 接口（commonMain）；jvmMain 放 sshj 实现
  servers/    Server 模型与仓库
  settings/   AppSettings 与仓库、导入导出、更新检查
```

- Java 源放 `shared/src/jvmMain/java/com/barezen/ssh/<包>/`，与 Kotlin 同包互调。
- 测试镜像源码路径：commonTest / jvmTest。
- Compose 资源生成包固定为 `com.barezen.ssh.generated.resources`（shared/build.gradle.kts `packageOfResClass`），新增字体/图片后导入写这个包。

## 4. TDD 规则

1. 每个功能点先红后绿：先写会失败的测试，再写最小实现。
2. 纯解析器（Java 侧）必须穷举单测：合法 / 截断 / 非法 / 空输入。
3. Compose UI 测试：
   - 多节点文本歧义用 `onAllNodesWithText(x).fetchSemanticsNodes().isNotEmpty()` 断言，或给交互件加 `Modifier.testTag` 后 `onNodeWithTag`。
   - `assertIs<T>` 返回值，JUnit4 里包 `runBlocking<Unit>`。
   - 零网络：网络能力一律以构造参数注入（参考 `UpdateChecker` 的 `getJson` 注入模式）。
4. 门禁全绿才算完成；不允许 skip/ignore 测试。

## 5. 混编规则（摘要）

- Kotlin：UI（Compose）、协程状态、路由。
- Java：文本/协议解析、JNA 绑定、第三方 Java 库包装。
- 边界即模块边界：Java 不 import Compose；UI 不解析协议文本，只消费解析器产出的不可变 data class。
- 现有 Kotlin 代码不迁移语言。

## 6. Git 与提交

- 提交信息：`类型: 中文摘要`，类型取 feat/fix/refactor/test/docs/build；**禁止 emoji**，禁止 AI 协作署名。
- **禁止 `git add -A` / `git add .`**：先 `git status` 核对，逐个 add 授权文件；`.workbuddy/`、`.superpowers/` 等目录已在 `.git/info/exclude` 本地排除，不得入库。
- 提交粒度：一个逻辑任务一个提交；提交前门禁全绿。

## 7. 排障速查

| 症状 | 原因与处置 |
|---|---|
| Gradle 锁等待 / `/root/.gradle` 权限 | 未带 `GRADLE_USER_HOME=/home/lin/.gradle` 与 `HOME=/home/lin` |
| 测试报 skiko `AccessDeniedException` | Test JVM 缺 `-Duser.home=/home/lin`（init 脚本丢失，重建） |
| 测试报 `X11GraphicsEnvironment` | 缺 `-Djava.awt.headless=true` |
| Compose 断言多节点匹配失败 | 改 `testTag` 或 `onAllNodesWithText` 非空断言 |
| 资源 `Res` 无法解析 | 确认 `packageOfResClass` 配置与导入包一致；必要时 `--no-configuration-cache` 刷新资源生成 |
