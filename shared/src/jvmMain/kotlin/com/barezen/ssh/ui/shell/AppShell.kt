// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/AppShell.kt
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.HorizontalRule
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.awt.LocalAwtWindow
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
 *
 * ```
 * ┌──────────────── 标题栏（自建窗口 chrome，拖拽区 + 窗口按钮）──────────┐
 * │ 侧栏 224dp        │ 屏名                                        × ⤢ − │
 * │ 品牌块 64dp       ├───────────────────────────────────────────────────┤
 * │ 导航项            │ 内容区：--bg 底，仅上方两角 12dp 圆角                │
 * │ ...              │ （上/右/下 12px 外边距；与标题栏之间无分割线）      │
 * │ 连接计数          │                                                   │
 * │ 收起              │                                                   │
 * └──────────────────┴───────────────────────────────────────────────────┘
 * ```
 *
 * **粘连**：侧栏与标题栏同为 `--surface`(#141416)，内容区为 `--bg`(#1C1C1E)。
 * 侧栏右缘**没有描边**（apple.css 的 `.app-sidebar` 无 border-right），
 * 边界只靠这两级底色的色差；圆角只在内容区上方两角，形成侧栏「凹」、内容区「凸」。
 */
@Composable
fun BareZenAppContent(model: AppModel, modifier: Modifier = Modifier) {
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

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 标题栏横跨整个窗口宽度（含侧栏上方），这是「自建窗口 chrome」的前提：
        // 窗口按钮必须贴在窗口右上角，屏名在内容区上方居中于右半区。
        AppTitlebar(screenName = model.current.label)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            AppSidebar(
                model = model,
                collapsed = collapsed,
                onToggle = { collapsed = !collapsed },
                modifier = Modifier.fillMaxHeight(),
            )
            CompositionLocalProvider(LocalTerminalBridge provides terminalBridge) {
                // app-main：surface 底（= #141416，与侧栏同色，故粘连处无任何描边；
                // 边界完全靠内容区 --bg(#1C1C1E) 与它的色差体现）
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // .screen：bg 底 + **只切上方两个角** 12px + 上/右/下各 12px 外边距。
                        // **左外边距为 0**：内容区左缘必须紧贴侧栏，两者靠底色对撞「粘连」，
                        // 一旦留 12px 就会退化成「侧栏和内容区之间有条缝」。
                        //
                        // 注意 `.screen.terminal/.settings { padding: 0 }` 只清**内边距**，
                        // 外边距与圆角都还在——终端/设置屏同样要留这 12px 与圆角。
                        //
                        // 内层 32px（--space-8）留白只在**有内容的面板屏**给：
                        // 仪表盘/服务器/文件/端口转发是滚动内容页，需要这层呼吸；
                        // 终端与设置自己管理内部边距（终端要满铺画 shell，设置要满高滚动），
                        // 叠加会变成 32+32 双重留白、空态被推到中间偏上。
                        val paddedScreen =
                            model.current != Destination.TERMINAL &&
                                model.current != Destination.SETTINGS
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 0.dp,
                                    top = BareZenSize.contentInset,
                                    end = BareZenSize.contentInset,
                                    bottom = BareZenSize.contentInset,
                                )
                                .clip(
                                    RoundedCornerShape(
                                        topStart = BareZenSize.contentTopRadius,
                                        topEnd = BareZenSize.contentTopRadius,
                                        bottomEnd = 0.dp,
                                        bottomStart = 0.dp,
                                    ),
                                )
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .then(
                                    if (paddedScreen) Modifier.padding(BareZenSpace.xxxl)
                                    else Modifier
                                ),
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
            SidebarToggle(collapsed, onToggle)
        }
        // **刻意不画右缘描边**：apple.css 的 `.app-sidebar` 没有 border-right，
        // 侧栏与内容区的边界靠 surface(#141416) 与 bg(#1C1C1E) 的色差自然体现。
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

/** 读取当前 AWT 窗口（供标题栏拖动用）。实验 API 的标注只落在这一个读取点。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun currentAwtWindow(): java.awt.Window? = LocalAwtWindow.current

/**
 * 标题栏（自建窗口 chrome，apple.css `.app-titlebar`）：48dp、surface 底、横跨整窗宽。
 *
 * 三段：左侧留出侧栏宽度的空白（保持与侧栏同色粘连）| 中部屏名（13sp/600）|
 * 右侧窗口按钮（最小化 / 最大化 / 关闭）。
 *
 * 整条同时是**窗口拖拽区**（[WindowDraggableArea]）——`WindowDecoration.None` 之后
 * 系统不再提供拖拽，手工拖动只能靠它。
 */
@Composable
private fun AppTitlebar(screenName: String) {
    val colors = MaterialTheme.colorScheme
    val win = LocalWindowController.current
    // LocalAwtWindow 是实验 API：无边框窗口下拖动必须拿到 AWT Window 才能改屏幕坐标，
    // 而 WindowState 并不暴露它。实验标注收敛在这一个读取点。
    val awtWindow = currentAwtWindow()
    Row(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.titlebarHeight)
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题栏左段与侧栏同色，视觉上「延长」侧栏，形成 L 形粘连
        Spacer(Modifier.width(BareZenSize.sidebarWidth))
        ScreenTitle(screenName, Modifier.padding(start = 20.dp))
        Spacer(Modifier.weight(1f))
        // 拖拽区：占满屏名与窗口按钮之间的空白。WindowDecoration.None 之后系统不提供
        // 拖拽，必须自己接（见 windowDragHandle）；双击 = 最大化/还原。
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowDragHandle(awtWindow, onClick = { win.toggleMaximize() })
                .testTag("titlebar-drag-area"),
        )
        WindowButton(Icons.Outlined.HorizontalRule, "最小化") { win.minimize() }
        WindowButton(
            icon = if (win.isMaximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
            label = if (win.isMaximized) "还原" else "最大化",
        ) { win.toggleMaximize() }
        WindowButton(Icons.Filled.Close, "关闭", danger = true) { win.close() }
    }
}

/**
 * 窗口按钮：24x24 无底色、hover 才出 `surfaceContainer` 底；关闭按钮 hover 转红。
 * 不给常驻描边——与「粘连处无描边」同一套语言。
 */
@Composable
private fun WindowButton(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fg = when {
        danger && hovered -> colors.onError
        else -> colors.onSurfaceVariant
    }
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (hovered) colors.surfaceContainer else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("window-button-$label"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = fg)
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
