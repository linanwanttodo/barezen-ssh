// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/AppShell.kt
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.AiChatModel
import com.barezen.ssh.app.AiConfig
import com.barezen.ssh.app.Destination
import com.barezen.ssh.app.JvmSessionResources
import com.barezen.ssh.app.JavaAiHttpTransport
import com.barezen.ssh.app.PlatformAiKeyStore
import com.barezen.ssh.servers.FileForwardRuleStore
import com.barezen.ssh.ssh.forward.ForwardManager
import com.barezen.ssh.terminal.LocalTerminalBridge
import com.barezen.ssh.terminal.TerminalBridge
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ssh.sftp.TransferStatus
import com.barezen.ssh.ui.screens.ConnectDialog
import com.barezen.ssh.ui.screens.DashboardHost
import com.barezen.ssh.ui.screens.FilesScreen
import com.barezen.ssh.ui.screens.PortsScreen
import com.barezen.ssh.ui.screens.PortsModel
import com.barezen.ssh.ui.screens.ServersScreen
import com.barezen.ssh.ui.screens.SettingsScreen
import com.barezen.ssh.ui.screens.TerminalScreen
import com.barezen.ssh.ui.theme.focusRing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用外壳：200px 可折叠侧边导航（设计包「外壳/侧边导航」）+ 右侧内容区路由。
 * 连接确认对话框与连接中/失败覆盖层为壳级挂载——Task 5 约定：重写壳时原样保留这两处挂载。
 */
@Composable
fun BareZenAppContent(model: AppModel) {
    var collapsed by remember { mutableStateOf(false) }
    // T-7：AI 运维侧栏。单实例 AiKeyStore 由壳持有（同时下传侧栏与设置屏），
    // 避免两处各自建内存降级存储导致互不可见。
    var aiExpanded by remember { mutableStateOf(false) }
    val aiKeys = remember { PlatformAiKeyStore.platformDefault() }
    val aiScope = rememberCoroutineScope()
    val aiChat = remember(aiScope) {
        AiChatModel(
            scope = aiScope,
            transport = JavaAiHttpTransport(),
            // endpoint/model 来自设置、key 来自钥匙串；任一缺失视为未配置。
            configProvider = {
                val s = model.settings.settings
                val key = aiKeys.loadKey()
                if (s.aiEndpoint.isBlank() || s.aiModel.isBlank() || key.isNullOrBlank()) {
                    null
                } else {
                    AiConfig(s.aiEndpoint, s.aiModel, key)
                }
            },
        )
    }
    // 终端桥：TerminalView 经 LocalTerminalBridge 挂载活动会话的只读缓冲与写入口
    val terminalBridge = remember { TerminalBridge() }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppSidebar(
            model = model,
            collapsed = collapsed,
            onToggle = { collapsed = !collapsed },
            modifier = Modifier.fillMaxHeight(),
        )
        CompositionLocalProvider(LocalTerminalBridge provides terminalBridge) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                when (model.current) {
                    Destination.DASHBOARD -> DashboardHost(model)
                    Destination.SERVERS -> ServersScreen(
                        model = model,
                        onNewTerminal = { model.requestConnect(it) },
                        onOpenFiles = { model.navigate(Destination.FILES) },
                    )
                    Destination.TERMINAL -> TerminalScreen(model)
                    Destination.FILES -> FilesHost(model)
                    Destination.PORTS -> PortsHost(model)
                    Destination.SETTINGS -> SettingsScreen(model, aiKeys)
                }
            }
        }
        AiPanel(
            chat = aiChat,
            keys = aiKeys,
            bridge = terminalBridge,
            expanded = aiExpanded,
            onToggle = { aiExpanded = !aiExpanded },
        )
    }

    // 钥匙串预填：每个待连接服务器解析一次。**必须放在 LaunchedEffect 里**——
    // 底层 secret-tool 是同步阻塞子进程且无超时（Linux 可能弹解锁框），放进重组路径会冻死 UI。
    // key 用 server.id：切换待连接目标时重新解析，避免沿用上一台的凭据。
    val pendingServerId = model.pendingConnect?.id
    LaunchedEffect(pendingServerId) { model.initializePendingCredential() }

    // 连接确认对话框：确认 → confirmConnect（内部委托 startConnect 主路径），取消 → 关闭。
    // 「记住凭据」勾选时先写入钥匙串再连接——未勾选则绝不落盘（安全红线）。
    model.pendingConnect?.let { server ->
        ConnectDialog(
            server = server,
            hideAddresses = model.settings.settings.hideAddresses,
            prefill = model.pendingPrefill,
            keychainAvailable = model.keychainAvailable,
            onResult = { auth, remember ->
                if (auth != null) {
                    if (remember) model.credentials.remember(server, auth)
                    model.confirmConnect(auth)
                } else {
                    model.dismissConnect()
                }
            },
        )
    }

    // 连接中/失败覆盖层：与 ConnectDialog 同为壳级挂载（Task 8 重写壳时原样迁移这两行）
    ConnectFlowOverlays(model)
}

/**
 * 文件页接线（参照 DashboardHost 范式）：跟随**活动会话**创建 [SftpModel]（fsFactory 惰性建 SFTP），
 * 会话切走或断开时取消未完成传输并释放。无活动会话传 null，FilesScreen 呈现整屏禁用态。
 *
 * key 用会话 id 而非布尔 connected：两条会话**同时 Connected** 时布尔恒为 true，
 * 只以它为 key 的 LaunchedEffect 不会重跑，切换活动会话后 SFTP 面板仍绑在旧会话的句柄上。
 */
@Composable
private fun FilesHost(model: AppModel) {
    val active = model.registry.active
    val scope = rememberCoroutineScope()
    var sftpModel by remember { mutableStateOf<SftpModel?>(null) }

    LaunchedEffect(active?.id) {
        val session = active?.session
        if (active != null && session != null) {
            sftpModel = SftpModel(scope, fsFactory = { session.newSftp() })
        } else {
            sftpModel?.transfers?.value
                ?.filter { it.status == TransferStatus.Running }
                ?.forEach { sftpModel?.cancel(it.id) }
            sftpModel = null
        }
    }
    FilesScreen(model = sftpModel)
}

/**
 * 端口转发页接线：只**订阅**活动会话的 [ForwardManager] 并驱动自动启用，不创建也不释放它。
 *
 * 隧道所有权在**会话**（[JvmSessionResources]，设计 §3.4 方案 A）而不是 Host：Host 随标签切换
 * 重新组合，若由 Host 持有 manager，用户切走标签就会把正在使用的隧道一起关掉。因此这里
 * **没有** DisposableEffect + closeAll——Host 销毁不等于隧道销毁，释放统一由 [SessionRegistry]
 * 在关闭标签/断开时执行（「关闭即释放、不保活」）。
 *
 * managerFlow 仍是 StateFlow：会话尚未连上（Connecting/Failed/无会话）时为 null，
 * PortsScreen 据此呈现禁用态；这与「非活动会话不参与渲染」的既有语义一致。
 */
@Composable
private fun PortsHost(model: AppModel) {
    val active = model.registry.active
    val scope = rememberCoroutineScope()

    // 资源没变就不重建 flow：切走再切回拿到的是同一个 JvmSessionResources 实例，
    // 故 PortsModel.liveEntries（flatMapLatest）不会因切换而重新订阅、活动转发列表不丢。
    val resources = active?.resources as? JvmSessionResources
    val managerFlow = remember(resources) {
        MutableStateFlow<ForwardManager?>(resources?.forwardManager)
    }

    // 自动启用只在**会话首次**进入活动态时执行一次（JvmSessionResources 自己记账）：
    // 该会话已被用户手动关掉的转发，不得因切回标签而被重新打开（安全敏感，设计 §3.4）。
    LaunchedEffect(resources) {
        resources?.ensureAutoStartApplied { FileForwardRuleStore().list().filter { it.autoStart } }
    }
    PortsScreen(model = PortsModel(scope = scope, managerFlow = managerFlow))
}



/** 侧边导航：展开 200dp / 折叠 56dp，surface 底、右缘 1dp borderSubtle 竖线；不渲染 badge（非目标）。 */
@Composable
private fun AppSidebar(
    model: AppModel,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(if (collapsed) 56.dp else 200.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 设计包 .collapse-btn { margin: auto var(--space-2) var(--space-2) }：顶部 auto 由上面的
        // 弹性间隔承担，底部 8px 这里用列底 padding 表达（原实现是第二个弹性间隔，已删）
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            SidebarHeader(collapsed)
            SidebarItem(Destination.DASHBOARD, model, collapsed)
            SidebarItem(Destination.SERVERS, model, collapsed)
            SidebarItem(Destination.TERMINAL, model, collapsed)
            SidebarItem(Destination.FILES, model, collapsed)
            // 设计包 mock-nav-spacer：文件与端口转发之间的弹性间隔（仅此一个弹性间隔——
            // 设计包 .mock-nav-spacer{flex:1} 只有一个，端口转发/设置因此钉在列底部）
            Spacer(Modifier.weight(1f))
            SidebarItem(Destination.PORTS, model, collapsed)
            SidebarItem(Destination.SETTINGS, model, collapsed)
            SidebarToggle(collapsed, onToggle)
        }
        // 右缘 1dp borderSubtle 竖线（Box 后置）
        Box(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** 顶部标题行：高 56dp、padding 16；折叠时只留图标。 */
@Composable
private fun SidebarHeader(collapsed: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = if (collapsed) 18.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Terminal,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        if (!collapsed) {
            Text("BareZen", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun navIcon(to: Destination): ImageVector = when (to) {
    // Monitoring 在 CMP 1.7.3 material-icons-extended 不存在 → 简报授权回退 QueryStats
    Destination.DASHBOARD -> Icons.Outlined.QueryStats
    Destination.SERVERS -> Icons.Outlined.Dns
    Destination.TERMINAL -> Icons.Outlined.Terminal
    Destination.FILES -> Icons.Outlined.Folder
    Destination.PORTS -> Icons.Outlined.SyncAlt
    Destination.SETTINGS -> Icons.Outlined.Settings
}

/** 导航项：40dp 行、clip 6dp、选中 panel 底；折叠时仅图标（水平居中）。 */
@Composable
private fun SidebarItem(dest: Destination, model: AppModel, collapsed: Boolean) {
    val isSelected = model.current == dest
    val navRowShape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = if (collapsed) 18.dp else 12.dp)
            .clip(navRowShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            )
            // 键盘焦点环（需求 §2 硬约束 3）。**必须放在 clickable 之前**：
            // onFocusChanged 只能观察到链上位于其后的 focusable/focusTarget 的焦点状态。
            .focusRing(navRowShape)
            .clickable { model.navigate(dest) }
            .semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = navIcon(dest),
            contentDescription = dest.label,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!collapsed) {
            Text(
                dest.label,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 底部收起/展开按钮：展开态 ChevronLeft + 「收起」，折叠态仅 ChevronRight。 */
@Composable
private fun SidebarToggle(collapsed: Boolean, onToggle: () -> Unit) {
    val toggleShape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = if (collapsed) 18.dp else 12.dp)
            .clip(toggleShape)
            // 键盘焦点环（需求 §2 硬约束 3）：须在 clickable 之前，见 SidebarItem 注释
            .focusRing(toggleShape)
            .clickable { onToggle() }
            .testTag("sidebar-toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = if (collapsed) "展开侧边栏" else "收起侧边栏",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!collapsed) {
            Text(
                "收起",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}