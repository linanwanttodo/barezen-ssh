# 开发契约（所有子代理与主代理共同遵守）

日期：2026-10-02。本文档是派发子代理时的**唯一约定来源**：任何子代理在开始前必须通读，任何与本文冲突的指令以本文为准。

## 1. 硬约束（违反即返工）

1. **代码与提交信息零 emoji**；内容文案需要连接语义时用「至」或 `->`，不用箭头装饰字符。
2. **仓库内零 AI 工具痕迹**：不创建 `.workbuddy/`、`.superpowers/` 等目录，不写会话日志进仓库，不在 `.gitignore` 加 AI 条目。
3. **禁止 `git add -A` / `git add .`**：子代理**一律不做任何 git 操作**（不 add、不 commit、不 push），改动只落在工作区，由主代理审计后统一提交。
4. 禁止渐变、禁止装饰性动效；界面文案中文，术语保留英文惯用（SSH/SFTP/Docker）。
5. 只改任务书授权的文件；发现相邻问题**不修**，在结果报告中列出。
6. 包名一律 `com.barezen.ssh`（2026-10-02 已重构，不得出现 `barezen_ssh`）。

## 2. 语言边界（混编规则，PRODUCT.md 已收录）

- UI（Compose）、协程状态、路由 → Kotlin。
- 文本/协议解析、JNA 系统绑定、第三方 Java 库（sshj 等）包装 → Java。
- Java 源目录：`shared/src/jvmMain/java/com/barezen/ssh/<包路径>/`（KMP 会自动纳入编译）。
- 跨平台接口与测试 fake 留在 commonMain Kotlin；解析器产出不可变 data class，UI 只消费不解析。
- Java 类不 import Compose；Kotlin UI 不直接解析协议文本。
- 现有 Kotlin 代码不迁移语言；不为混编而混编。

## 3. TDD 流程

1. 先写测试（红），再写实现（绿），每个功能点保持红绿循环。
2. 测试位置：跨平台逻辑入 `shared/src/commonTest`，JVM 实现/Compose UI 入 `shared/src/jvmTest`。
3. Compose UI 测试注意：多节点文本匹配用 `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()` 或 `testTag`；`assertIs<T>` 返回值，JUnit4 中用 `runBlocking<Unit>` 包裹。
4. 纯解析器必须有穷举单测：合法 / 截断 / 非法 / 空输入。

## 4. 构建门禁（唯一验收命令）

```
HOME=/home/lin JAVA_HOME=/home/lin/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2 \
GRADLE_USER_HOME=/home/lin/.gradle MALLOC_ARENA_MAX=1 \
timeout -k 15 900 ./gradlew --no-daemon --init-script /home/lin/tmp/barezen-ssh-init.gradle \
:desktopApp:compileKotlin :shared:jvmTest
```

- 门禁必须全绿才算完成；`/home/lin/tmp/barezen-ssh-init.gradle` 丢失时重建（Test 任务加 `-Duser.home=/home/lin` 与 `-Djava.awt.headless=true`）。
- 不要并发跑多个 Gradle 进程（文件锁冲突）。

## 5. 当前基线

- 28 测试套件 / 149 用例 / 0 失败（包名重构后，commit `3f7059d`）。
- 依赖：Kotlin 2.4.20、Compose Multiplatform 1.12.1、sshj 0.40.0、jediterm 3.73。
- `SshSession`（commonMain `ssh/Ssh.kt`）现有 `pingMs/startShell/close`。

## 6. 结果报告格式

子代理结束时报告：改动文件清单（路径 + 意图）、新增测试数、门禁输出摘要（任务数/失败数）、未解决的偏离与原因。
