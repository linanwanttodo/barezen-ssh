// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/AppShell.kt
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.AiChatModel
import com.barezen.ssh.app.AiConfig
import com.barezen.ssh.app.Destination
import com.barezen.ssh.app.JavaAiHttpTransport
import com.barezen.ssh.app.PlatformAiKeyStore
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.terminal.LocalTerminalBridge
import com.barezen.ssh.terminal.TerminalBridge
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ssh.sftp.TransferStatus
import com.barezen.ssh.ui.components.ScreenTitle
import com.barezen.ssh.ui.components.StatusDot
import com.barezen.ssh.ui.screens.ConnectDialog
import com.barezen.ssh.ui.screens.DashboardHost
import com.barezen.ssh.ui.screens.FilesScreen
import com.barezen.ssh.ui.screens.PortsHost
import com.barezen.ssh.ui.screens.ServersScreen
import com.barezen.ssh.ui.screens.SettingsScreen
import com.barezen.ssh.ui.screens.TerminalScreen
import com.barezen.ssh.ui.theme.BareZenSize
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.LocalBareZenColors

/**
 * 应用外壳（apple.html `app-shell` / `app-body`）：
 * 224dp 侧栏（品牌块 + 导航 + 底部连接计数）| 48dp 标题栏（屏名 + 右侧快捷键按钮）| 内容容器。
 *
 * 内容区按 apple.css `.screen`：bg 底、四角 12dp 圆角、上/右/下各留 12px 边距，
 * 终端屏与设置屏为「无边距满铺」形态（`.screen.terminal/.settings { padding: 0 }`）。
 */
@Composable
fun BareZenAppContent(model: AppModel) {
    var collapsed by remember { mutableStateOf(false) }
    // T-7：AI 运维侧栏。单实例 AiKeyStore 由壳持有（同时下传终端屏与设置屏），
    // 避免两处各自建内存降级存储导致互不可见。
    val aiKeys = remember { PlatformAiKeyStore.platformDefault() }
    val aiScope = rememberCoroutineScope()
    val aiChat = remember(aiScope) {
        AiChatModel(
            scope = aiScope,
            transport = JavaAiHttpTransport(),
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
    val terminalBridge = remember { TerminalBridge() }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppSidebar(
            model = model,
            collapsed = collapsed,
            onToggle = { collapsed = !collapsed },
            modifier = Modifier.fillMaxHeight(),
        )
        CompositionLocalProvider(LocalTerminalBridge provides terminalBridge) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AppTitlebar(screenName = model.current.label)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // 内容容器：bg 底 + 12dp 圆角 + 1dp 描边。终端/设置满铺，其余屏内缩。
                    val inset =
                        model.current == Destination.TERMINAL || model.current == Destination.SETTINGS
                    Surface(
                        modifier = if (inset) Modifier.fillMaxSize()
                        else Modifier
                            .fillMaxSize()
                            .padding(
                                start = BareZenSize.contentInset,
                                top = BareZenSize.contentInset,
                                end = BareZenSize.contentInset,
                                bottom = BareZenSize.contentInset,
                            ),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(BareZenSize.contentTopRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        when (model.current) {
                            Destination.DASHBOARD -> DashboardHost(model)
                            Destination.SERVERS -> ServersScreen(
                                model = model,
                                onNewTerminal = { model.requestConnect(it) },
                                onOpenFiles = { model.navigate(Destination.FILES) },
                            )
                            Destination.TERMINAL -> TerminalScreen(model, aiChat, aiKeys)
                            Destination.FILES -> FilesHost(model)
                            Destination.PORTS -> PortsHost(model)
                            Destination.SETTINGS -> SettingsScreen(model, aiKeys)
                        }
                    }
                }
            }
        }
    }

    // 钥匙串预填：每个待连接服务器解析一次。**必须放在 LaunchedEffect 里**——
    // 底层 secret-tool 是同步阻塞子进程且无超时（Linux 可能弹解锁框），放进重组路径会冻死 UI。
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

    // 连接中/失败覆盖层：与 ConnectDialog 同为壳级挂载
    ConnectFlowOverlays(model)
}

/**
 * 侧边导航（apple.css `.app-sidebar`）：展开 224dp / 折叠 56dp，surface 底；
 * 品牌块 64dp；导航项 34dp、圆角 8、选中 panel 底 + accent 图标；底部连接计数。
 */
@Composable
private fun AppSidebar(
    model: AppModel,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val connectedCount = model.registry.sessions.count { it.state is ConnectionState.Connected }
    Box(
        modifier
            .fillMaxHeight()
            .width(if (collapsed) BareZenSize.sidebarCollapsedWidth else BareZenSize.sidebarWidth)
            .background(colors.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            SidebarBrand(collapsed)
            Column(
                Modifier.weight(1f).padding(BareZenSpace.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Destination.entries.forEach { dest ->
                    SidebarItem(dest, model, collapsed)
                }
            }
            // 底部连接计数（apple.css `.app-sidebar-footer`）
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (collapsed) 40.dp else 34.dp)
                    .padding(horizontal = if (collapsed) 0.dp else 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
            ) {
                StatusDot(on = connectedCount > 0, hollow = connectedCount == 0)
                if (!collapsed) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$connectedCount 台已连接",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("sidebar-connected-count"),
                    )
                }
            }
            HorizontalRule()
            SidebarToggle(collapsed, onToggle)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.outline),
        )
    }
}

/** 品牌块（apple.css `.app-brand`）：64dp 高，32dp 方形标记 + 名称/副名。 */
@Composable
private fun SidebarBrand(collapsed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.brandHeight)
            .padding(horizontal = if (collapsed) 0.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "BZ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.surface,
            )
        }
        if (!collapsed) {
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "BareZen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text("SSH 客户端", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
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

/** 导航项（apple.css `.nav-item`）：34dp、圆角 8、选中 panel 底 + accent 图标 + 600 字重。 */
@Composable
private fun SidebarItem(dest: Destination, model: AppModel, collapsed: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val isSelected = model.current == dest
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.navItemHeight)
            .clip(shape)
            .background(if (isSelected) colors.surfaceContainer else Color.Transparent)
            // 焦点环须在 clickable 之前（见 FocusRing.kt 约束）
            .then(if (focused) Modifier.border(2.dp, extras.accentOnSubtle, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { model.navigate(dest) }
            // 标题栏同步渲染同名屏名（apple.html 设计），纯文本定位会二义 -> 用 tag 定位
            .testTag("sidebar-nav-" + dest.name)
            .semantics { selected = isSelected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(
            imageVector = navIcon(dest),
            // contentDescription 置 null：同一行已有可读文字标签，重复会让读屏念两遍，
            // 也会让按文本定位的测试命中两个节点。
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) extras.accentOnSubtle else colors.onSurfaceVariant,
        )
        if (!collapsed) {
            Spacer(Modifier.width(12.dp))
            Text(
                dest.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 底部收起/展开按钮。 */
@Composable
private fun SidebarToggle(collapsed: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, extras.accentOnSubtle, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
            .testTag("sidebar-toggle")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = if (collapsed) "展开侧边栏" else "收起侧边栏",
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant,
        )
        if (!collapsed) {
            Spacer(Modifier.width(12.dp))
            Text("收起", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * 标题栏（apple.css `.app-titlebar`）：48dp、surface 底；左为屏名（13sp/600），
 * 右侧两个等宽字形按钮（搜索 / 命令面板）。命令面板尚未实现，按 disabled 呈现
 * （不造功能：按钮可见但禁用，而不是给一个点了没反应的东西）。
 */
@Composable
private fun AppTitlebar(screenName: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.titlebarHeight)
            .background(colors.surface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenTitle(screenName)
        Spacer(Modifier.weight(1f))
        TitlebarKey("Q", "搜索")
        Spacer(Modifier.width(8.dp))
        TitlebarKey("Ctrl K", "命令面板", enabled = false)
    }
}

/** 标题栏键帽按钮（apple.css `.titlebar-btn`）：等宽字形 + 描边。 */
@Composable
private fun TitlebarKey(glyph: String, label: String, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) {}
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 11.sp,
            color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun HorizontalRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * 文件页接线：跟随**活动会话**创建 [SftpModel]，会话切走或断开时取消未完成传输并释放。
 * key 用会话 id 而非布尔 connected（两条会话同时 Connected 时布尔恒为 true）。
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
