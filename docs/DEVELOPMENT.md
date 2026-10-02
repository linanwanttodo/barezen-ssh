# 操作手册（DEVELOPMENT）

更新：2026-10-02。日常命令与排障。约束见 CONVENTIONS.md，架构见 ARCHITECTURE.md。

## 1. 环境

| 项 | 要求 |
|---|---|
| JDK | 21（Gradle 自动管理在 `~/.gradle/jdks/`） |
| 构建 | 仓库自带 wrapper 9.5.1，不要用系统 gradle |
| 运行 | `./gradlew :desktopApp:run`（需图形环境） |

## 2. 常用命令

```
./gradlew :desktopApp:run                                # 启动桌面应用
./gradlew :desktopApp:compileKotlin :shared:jvmTest      # 门禁（见 2.1）
./gradlew :shared:jvmTest --tests "com.barezen.ssh.ssh.metrics.*"   # 跑单个包
```

### 2.1 门禁（提交前必须全绿；当前基线 456 用例 / 0 失败）

受限环境（无 X、`/root` 不可写）的完整配方：

```
HOME=/home/lin JAVA_HOME=/home/lin/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2 \
GRADLE_USER_HOME=/home/lin/.gradle MALLOC_ARENA_MAX=1 \
timeout -k 15 900 ./gradlew --no-daemon --init-script /home/lin/tmp/barezen-ssh-init.gradle \
:desktopApp:compileKotlin :shared:jvmTest
```

**init 脚本重建**（`/home/lin/tmp/barezen-ssh-init.gradle` 随 tmpfs 丢失时，在项目外重建以下内容）：

```groovy
// 给 Test JVM 补 user.home（skiko 写字体缓存）与 headless（无 X 跑 Compose UI 测试）
gradle.projectsEvaluated {
    tasks.withType(Test).configureEach {
        systemProperty "user.home", "/home/lin"
        systemProperty "java.awt.headless", "true"
    }
}
```

注意：
- `--rerun` 是 per-task flag 必须紧跟任务名；多任务强制重跑用 `--rerun-tasks`。
- 不并发跑多个 Gradle 进程（文件锁冲突；子代理并行时的重试协议见 CONVENTIONS 第 6 节）。
- `assembleDebug` 类任务在受限沙箱不可用（`/root/.android` 不可写），门禁以上述两任务为准。

## 3. 目录速查

见 ARCHITECTURE.md 第 2 节。要点：Java 源放 `shared/src/jvmMain/java/com/barezen/ssh/<包>/`；测试镜像源码路径；Compose 资源从 `com.barezen.ssh.generated.resources` import。

## 4. 测试写法要点

1. 先红后绿；禁止 skip；网络能力构造注入（零网络测试）。
2. Compose 断言：多节点文本歧义用 `testTag` 或 `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()`；`assertIs<T>` 返回值需包 `runBlocking<Unit>`。
3. coroutines-test 1.11.0：`advanceUntilIdle` 不执行 backgroundScope 任务，用 `runCurrent` / `advanceTimeBy + runCurrent`。
4. fake 现货：`FakeSshSession`（exec 计数 + 预设返回 + sftpFactory 注入）、`FakeSshClient`、`FakeSftpFs`、`FakeCommandRunner`（凭据进程封装测试）——复用，不要重写。

## 5. 提交

`类型: 中文摘要`（feat/fix/refactor/test/docs/build）；零 emoji；逐文件 add（禁 `git add -A`）；一个逻辑任务一个提交。

## 6. sshj 0.40.0 API 速查（已 javap 核实，改调用前仍建议复核）

```
Session.Command 无 waitFor() -> 用 cmd.join(timeout, TimeUnit)
SSHClient.newLocalPortForwarder(Parameters(localHost, localPort, remoteHost, remotePort), ServerSocket)
  LocalPortForwarder.listen() 阻塞 -> 放守护线程
RemotePortForwarder.bind(Forward(bindPort), ConnectListener) / cancel(Forward)
SFTPClient: ls/mkdir/mkdirs/rename/rm/rmdir/lstat/open(path, Set<OpenMode>)
RemoteFile.read/write(long offset, byte[], int off, int len)；read 返回 <=0 视为 EOF
FileAttributes: getSize()/getMtime()(秒)/getType()==FileMode.Type.DIRECTORY
jar 位置：~/.gradle/caches/modules-2/files-2.1/com.hierynomus/sshj/0.40.0/
```

## 7. 排障速查

| 症状 | 处置 |
|---|---|
| Gradle 锁等待 / `/root/.gradle` 权限 | 带齐 `HOME=/home/lin GRADLE_USER_HOME=/home/lin/.gradle` |
| 测试 skiko `AccessDeniedException` | Test JVM 缺 `-Duser.home=/home/lin`（init 脚本丢失，按 2.1 重建） |
| 测试 `X11GraphicsEnvironment` 报错 | 缺 `-Djava.awt.headless=true` |
| Compose 断言多节点匹配失败 | `testTag` 或 `onAllNodesWithText` 非空断言 |
| 资源 `Res` 无法解析 | 核对 `packageOfResClass` 与导入包一致；必要时加 `--no-configuration-cache` 刷新资源生成 |
| 并行代理编译互相报错 | 只修自己授权文件；等 2 分钟重试（协议见 CONVENTIONS 第 6 节） |
| init 脚本丢失 | 按 2.1 节模板在项目外重建 |
