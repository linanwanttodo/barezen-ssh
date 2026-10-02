# 待办任务书（ROADMAP）

更新：2026-10-02。每个任务包含：目标 / 授权文件 / 验收标准 / 注意事项。开工前先读 CONVENTIONS.md；完成后更新 STATUS.md。默认按编号顺序执行，用户改派除外。

## T-1 钥匙串凭据接入连接流程（优先级最高）

**目标**：把已建好的 `CredentialStore`（jvmMain `credentials/` 包）接入服务器连接链路：保存凭据的服务器在连接时自动从钥匙串取密码/密钥口令；连接成功后可选「记住凭据」存入钥匙串；UI 上明示「已存入系统钥匙串」（PRODUCT.md 设计原则 4：安全可见）。

**授权文件**：`ui/screens/ServersScreen.kt`（顺势拆分）、`ui/screens/ConnectDialog.kt`、`servers/Server.kt`（如需加 useKeychain 标记字段，注意旧 JSON 兼容：`coerceInputValues` + 默认值）、新测试文件。**禁改** `credentials/` 包内容（接口已定：`save/load/delete/isAvailable`，id 约定 `server/<serverId>/password|keypass`）。

**验收**：
1. 未存凭据的连接流程与现状完全一致（回归零破坏）；
2. 「记住凭据」后，下次连接无需再输密码；钥匙串不可用时回退现状并提示；
3. 服务器删除时同步删除钥匙串条目；
4. UI 测试覆盖：勾选/未勾选、钥匙串不可用提示；门禁全绿。

**注意**：`Server` 是 commonMain 数据类，钥匙串访问只能在 jvmMain 接线层发生——通过 jvmMain 的 Host 层把 `CredentialStore` 注入连接流程，不要让 commonMain 出现平台依赖。ServersScreen 拆分：列表 / 卡片 / 编辑表单至少分三文件。

## T-2 VPS 真机冒烟（需用户提供凭据）

**目标**：在一台真实 Linux VPS 上全链路验证。凭据只进用户本机 `~/.ssh/config` 或环境变量，**不入仓库不入文档**。

**冒烟清单**：
1. 连接：密码与密钥两种认证；TOFU 首次信任 + 主机密钥变更拒绝。
2. 终端：交互、resize、长时间会话心跳（15 分钟不断线）。
3. 仪表盘：四指标与真实 `top`/`free` 数值同量级；磁盘条与 `df -h` 一致；轮询 5 分钟无内存泄漏迹象。
4. 转发：**不硬编码目标端口**——按目标机实际存在的服务选替代对端（无 Web 服务时可用 `127.0.0.1:<临时高位端口>` 起一次性对端）；本地转发与远程转发各验一条；断开连接后隧道关闭、端口释放。**若目标机是生产机：只动临时高位端口与临时目录，禁止触碰既有服务与端口。**
5. SFTP：浏览根目录/家目录；上传 10MB 与 200KB 文件（进度/完成态）；下载同；取消进行中任务；新建文件夹；中文文件名。
6. 亮/暗两主题 + 缩放 80%/120% 各过一遍主流程。

### T-2a SFTP 大文件上传崩溃（2026-10-02 冒烟发现，优先于 T-2 剩余项）

**现象**：10MB 上传，远端文件完整落盘（10485760 字节与本地一致），传输结束后 JVM **静默退出**（exit 0、无堆栈、无 OOM），两次完全复现；后续冒烟项全部未执行到。
**定性方向**：不能排除冒烟脚手架自身（lambda 内一次性喂大数组、`copyOfRange` 反复拷贝）触发，也必须排除产品代码（`SftpFs.upload` 生命周期 / `JvmSshClient` / `SftpModel` 任务收尾）。
**方法**：用测试依赖里已有的嵌入式 sshd（sshd-core 2.14.0，`JvmSshClientTest` 在用）在本地写最小复现回归测试（先红）；定位根因后修复转绿；**若根因在脚手架，只许改脚手架并如实报告，不许为过测试改产品代码**。

## T-3 SFTP 删除/重命名 UI

**目标**：文件列表条目加删除与重命名操作（长按或行尾菜单），底层直接调 `SftpFs.delete/rename`；删除目录须为空（递归删除：前端逐层确认，不做一键递归）；完成后 `SftpModel.refresh()`。

**授权文件**：`ui/screens/FilesScreen.kt`、`ssh/sftp/SftpModel.kt`、`ssh/sftp/FakeSftpFs`（测试）等 sftp 包新文件。**验收**：UI 测试（删除确认框、重命名输入、空目录删除成功、非空目录提示）；门禁全绿。

## T-4 P5 多标签/分屏

**目标**：多会话并行：侧栏或顶部标签页管理多个已连接会话；终端/仪表盘/SFTP/转发按会话隔离。

**关键设计（开工前先出 spec）**：
- 会话注册表：`SessionRegistry`（commonMain）替代 AppModel 单 `shellSession` 字段——所有 `*Host` 接线层改为订阅 registry 的活动会话；这是本次最大的重构面。
- AppShell：内容区改标签容器；连接生命周期（断开/关闭单会话 vs 全部）。
- 风险：sshj 单连接多通道已验证；多连接 = 多 `SSHClient` 实例，资源占用与心跳各自独立。
- **建议在 T-1/T-3 之后做**（避免与凭据接线撞 ServersScreen）。

## T-5 P6 AI 运维侧栏（待用户决策）

用户未拍板。若做，先出 spec 明确：读取滚动缓冲区范围、命令注入的审批交互（建议：AI 生成命令 -> 用户确认 -> 注入 shell）、API key 存放（钥匙串）、失败降级。**不造功能**，决策前不动。

## 小项池（随时可插队）

| 项 | 说明 |
|---|---|
| JNA 依赖 + Windows 真实桥 | 加 `net.java.dev.jna:jna:5.x`（shared jvmMain deps），实现 P3 预留的 JnaAdvapiBridge，WindowsCredStore isAvailable() 变真 |
| 亮色 Warning 变体 | 组件层为亮色板提供深色 warning 前景（当前 1.53:1 不可读） |
| 自动打包分发 | `packageDistributionForCurrentOS`，注意沙箱 `/root/.android` 限制——在用户桌面机跑 |
| 动态转发 SOCKS5 | 自写 SOCKS5 CONNECT accept 循环 + direct-tcpip 通道；成本高，评估后再排期 |
| `.Trash-0/` 清理 | 仓库根的沙箱垃圾目录，可直接删除（未跟踪） |

## 版本里程碑

- M1（当前）：单会话全功能闭环，367 测试全绿。
- M2：T-1 + T-2 完成后，凭据安全可见 + 真机验证过，可打 v0.2.0。
- M3：P5 多标签，v0.3.0。
- M4：P6 AI（若做），v0.4.0 / v1.0。
