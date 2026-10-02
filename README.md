# BareZen-SSH

跨平台（Linux / Windows / macOS）桌面 SSH 客户端：以终端会话为中心，集成 SFTP 文件管理、端口转发、钥匙串凭据、服务器监控与 AI 运维侧栏。单用户自用工具，效率与信息密度优先，界面安静不添乱。

技术栈：Kotlin Multiplatform（JVM 目标）+ Compose Desktop，sshj（SSH/SFTP）、JediTerm（终端模拟）、Java 21。UI 与解析层按 Kotlin/Java 混编约定分层（见 docs/CONVENTIONS.md）。

## 功能一览

- **终端会话**：JediTerm 终端，TOFU 主机密钥校验，心跳保活，多标签会话
- **服务器管理**：增删改查、口令掩码显示、OpenSSH config 导入
- **凭据**：系统钥匙串（GNOME Keyring / macOS Keychain / Windows 凭据管理器），无钥匙串环境明示降级；连接时可选「记住凭据」
- **SFTP 文件管理**：浏览、上传、下载（流式分块）、重命名、删除，传输进度与取消
- **端口转发**：本地 / 远程 / 动态，规则持久化，随会话自动启用
- **服务器监控**：CPU / 内存 / 磁盘 / 负载实时指标（exec 采样，失败如实置空，不造数）
- **AI 运维侧栏**（BYOK）：OpenAI 兼容 endpoint + 自带 API key（key 只存系统钥匙串）；可附带终端滚动缓冲区最后 200 行作为上下文；AI 回复中的命令须经审批对话框确认后才会注入终端；未配置 / 无钥匙串 / 网络失败均有明示，不静默
- **界面**：Material 3，暗色优先；仅黑/白/灰，红色 0xFFF3625F 仅用于警告与错误；终端 ANSI 配色保持数据原貌

## 从源码运行

需要 JDK 21+。

```bash
./gradlew :desktopApp:run
```

## 打包安装程序

版本单一真相源是 `gradle.properties` 的 `barezen.version`（当前 1.0.0），`BuildInfo.VERSION` 由测试守卫与其保持一致。

```bash
# 构建当前平台的安装包（Linux -> deb；macOS -> dmg；Windows -> msi）
./gradlew :desktopApp:packageDistributionForCurrentOS

# 一次构建全部三种格式（需要对应平台的 jpackage 环境）
./gradlew :desktopApp:packageDistributionForCurrentOS  # 在各目标平台分别执行
```

产物位置：`desktopApp/build/compose/binaries/main/<deb|dmg|msi>/`。

- Linux：依赖宿主 `jpackage`（JDK 21 自带）与 `dpkg-deb`；已验证产出 `barezen-ssh_1.0.0_amd64.deb`
- macOS / Windows：需要各自平台的 JDK 与打包工具链，本项目未在非宿主平台交叉验证

## 测试

```bash
./gradlew :shared:jvmTest
```

当前 556 例（0 失败）。测试零外部网络依赖：SSH/SFTP 行为用 Apache MINA sshd 内嵌服务器模拟，AI 面板用注入式 fake transport。

## 文档

`docs/README.md` 是文档索引：`HANDOVER.md`（交接总纲）、`CONVENTIONS.md`（约束规范）、`STATUS.md`（进度台账）、`ROADMAP.md`（待办任务书）、`ARCHITECTURE.md`（架构）、`DEVELOPMENT.md`（操作手册）。产品设计基准见 `PRODUCT.md`。

## 已知限制

- 钥匙串真实读写需在桌面机验证（开发环境无 D-Bus/secret-tool，覆盖的是注入 fake 与内存降级路径）
- 远端转发的数据通路因开发环境网络拓扑（出口 IP 即 VPS 自身）未实测，协议栈已通过对照试验
- AI 侧栏要求用户自备 OpenAI 兼容 endpoint 与 key；无 Markdown 渲染，回复为纯文本
