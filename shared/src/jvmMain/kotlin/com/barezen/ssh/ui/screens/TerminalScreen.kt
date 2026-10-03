// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/TerminalScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
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
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.Destination
import com.barezen.ssh.app.NoopAiKeyStore
import com.barezen.ssh.app.SessionId
import com.barezen.ssh.app.SessionSnapshot
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.terminal.TerminalView
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.StatusDot
import com.barezen.ssh.ui.shell.AiSidebar
import com.barezen.ssh.ui.theme.BareZenMonoSmall
import com.barezen.ssh.ui.theme.BareZenSize
import com.barezen.ssh.ui.theme.LocalBareZenColors

/**
 * 终端屏（apple.html `.screen.terminal`）：标签条（38dp）| 终端区 + 可折叠 AI 侧栏（320dp）| 状态栏（28dp）。
 *
 * AI 侧栏从壳级右列**移入本屏**（2026-10-03 用户拍板，对齐 apple.html）：
 * 它与终端共享同一会话（要读滚动缓冲、要注入命令），离开终端屏就失去这两项能力，
 * 常年挂在壳级会占走 320dp 却只对终端有用。标签条右侧「助手」按钮即开关。
 *
 * 整屏状态机读**活动会话**（[SessionRegistry.active]），不再读单值投影：多会话并存时
 * 投影只能反映其一，而本屏必须能表达「有会话但都不是前台」这类情形。
 */
@Composable
fun TerminalScreen(
    model: AppModel,
    chat: AiChatModel? = null,
    keys: AiKeyStore = NoopAiKeyStore,
) {
    val active = model.registry.active
    var aiOpen by remember { mutableStateOf(false) }

    // §4.4 重挂提示：活动会话切换 = 终端区对新前台会话重挂（服务端新起 shell、滚动缓冲丢失）。
    // UI 必须如实告知这个代价；同一条会话只提示一次。
    var lastActiveId by remember { mutableStateOf<SessionId?>(null) }
    var restartNoticeFor by remember { mutableStateOf<SessionId?>(null) }
    val notifiedIds = remember { mutableStateListOf<SessionId>() }
    LaunchedEffect(active?.id) {
        val id = active?.id
        when {
            id == null -> {
                restartNoticeFor = null
                lastActiveId = null
            }
            lastActiveId == null -> lastActiveId = id
            id != lastActiveId -> {
                restartNoticeFor = if (id in notifiedIds) null else id
                if (id !in notifiedIds) notifiedIds += id
                lastActiveId = id
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TerminalTabStrip(
            sessions = model.registry.sessions,
            activeId = model.registry.activeId,
            aiOpen = aiOpen,
            aiAvailable = chat != null,
            onToggleAi = { aiOpen = !aiOpen },
            onActivate = { model.registry.activate(it) },
            onClose = { model.registry.close(it) },
            onNewSession = { model.navigate(Destination.SERVERS) },
        )
        if (restartNoticeFor != null && restartNoticeFor == active?.id) {
            RestartNotice()
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (val snapshot = active) {
                    null -> EmptyHint(
                        Icons.Outlined.Terminal,
                        "在服务器列表选择「新建终端」以开始。",
                        Modifier.fillMaxSize(),
                    )
                    else -> when (val st = snapshot.state) {
                        is ConnectionState.Disconnected -> EmptyHint(
                            Icons.Outlined.Terminal,
                            "在服务器列表选择「新建终端」以开始。",
                            Modifier.fillMaxSize(),
                        )
                        is ConnectionState.Connecting -> ConnectingPane(st)
                        is ConnectionState.Failed -> FailedPane(st) { model.requestConnect(st.server) }
                        is ConnectionState.Connected -> ConnectedPane(model, snapshot)
                    }
                }
            }
            if (aiOpen && chat != null) {
                AiSidebar(
                    chat = chat,
                    keys = keys,
                    sessionName = active?.let {
                        (it.state as? ConnectionState.Connected)?.server?.name
                    },
                    onClose = { aiOpen = false },
                )
            }
        }
        ConnectionStatusBar(active?.state)
    }
}

/**
 * 标签条（apple.css `.tab-strip`）：38dp、terminal 底；活动标签取 bg 底 + 描边。
 * 每条会话一个标签：单击切换，关闭按钮/中键关闭；`+` 跳服务器列表。
 * 右端「助手」按钮是 AI 侧栏开关（未接入时禁用而非隐藏）。
 */
@Composable
private fun TerminalTabStrip(
    sessions: List<SessionSnapshot>,
    activeId: SessionId?,
    aiOpen: Boolean,
    aiAvailable: Boolean,
    onToggleAi: () -> Unit,
    onActivate: (SessionId) -> Unit,
    onClose: (SessionId) -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.tabHeight)
            .background(extras.terminalBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sessions.forEach { snapshot ->
                key(snapshot.id) {
                    SessionTab(
                        snapshot = snapshot,
                        isSelected = snapshot.id == activeId,
                        onActivate = onActivate,
                        onClose = onClose,
                    )
                }
            }
        }
        // 新建标签
        val addShape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .size(28.dp)
                .clip(addShape)
                .clickable(onClick = onNewSession)
                .semantics { contentDescription = "新建终端" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        // AI 侧栏开关（apple.css `.tab.ai-toggle`）
        val toggleShape = RoundedCornerShape(8.dp)
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Row(
            Modifier
                .clip(toggleShape)
                .background(if (aiOpen) extras.accentOnSubtle else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (aiOpen) extras.accentOnSubtle else colors.outline,
                    shape = toggleShape,
                )
                .then(
                    if (focused) Modifier.border(2.dp, extras.accentOnSubtle, toggleShape) else Modifier
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = aiAvailable,
                ) { onToggleAi() }
                .testTag("ai-panel-toggle")
                .semantics { contentDescription = "智能助手" }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (aiAvailable) extras.accentOnSubtle else colors.onSurfaceVariant),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "助手",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    !aiAvailable -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                    aiOpen -> extras.accentOnSubtle
                    else -> colors.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 单个标签：单击切换前台；关闭按钮或中键（tertiary）点击关闭。
 * 选中态走语义 selected——无障碍读屏与测试断言共用同一事实源。
 */
@Composable
private fun SessionTab(
    snapshot: SessionSnapshot,
    isSelected: Boolean,
    onActivate: (SessionId) -> Unit,
    onClose: (SessionId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Surface(
        color = if (isSelected) colors.surfaceContainerLow else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, colors.outline) else null,
        modifier = Modifier
            .testTag("session-tab-" + snapshot.id.raw)
            .semantics { this.selected = isSelected }
            .clickable { onActivate(snapshot.id) }
            .pointerInput(snapshot.id) {
                // 中键按下即关（与浏览器标签页一致）。不消费事件：外层 clickable 若随后
                // 收到释放，也只会对已移除的 id 做一次无害激活。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed) {
                            onClose(snapshot.id)
                        }
                    }
                }
            },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(
                on = snapshot.state is ConnectionState.Connected,
                hollow = snapshot.state !is ConnectionState.Connected,
                tone = when (snapshot.state) {
                    is ConnectionState.Failed -> com.barezen.ssh.ui.components.BadgeTone.error
                    is ConnectionState.Connecting -> com.barezen.ssh.ui.components.BadgeTone.warning
                    else -> com.barezen.ssh.ui.components.BadgeTone.success
                },
            )
            Text(
                snapshot.title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClose(snapshot.id) }
                    .testTag("session-tab-close-" + snapshot.id.raw)
                    .semantics { contentDescription = "关闭标签页" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** §4.4 重挂提示：一次性、贴内容区顶部；措辞如实——重挂 = 服务端新 shell，历史缓冲不可保留。 */
@Composable
private fun RestartNotice() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh)
            .testTag("terminal-restart-notice")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "会话已重新打开：服务端会新起一个 shell，历史滚动缓冲不可保留。",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 连接中：居中 spin + 「正在连接 {name}…」。 */
@Composable
private fun ConnectingPane(state: ConnectionState.Connecting) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Text(
                "正在连接 ${state.server.name}…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 失败：错误提示块 + 「重试」（重试走 requestConnect 弹连接确认）。 */
@Composable
private fun FailedPane(state: ConnectionState.Failed, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = colors.errorContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "连接失败：${state.message}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    color = extras.onErrorContainer,
                )
            }
            Btn("重试", onRetry, kind = BtnKind.primary)
        }
    }
}

/** 已连接：挂 [TerminalView]。会话缺席不以 `!!` 炸组合，startShell 抛错不炸应用。 */
@Composable
private fun ConnectedPane(model: AppModel, snapshot: SessionSnapshot) {
    val session = snapshot.session
    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "正在启动终端…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // startShell 在 TerminalView 的 DisposableEffect 体内执行；在会话外包一层 runCatching——
    // 抛错即回传 Failed（并关会话），以 no-op 通道返回让 effect 照常注册 onDispose。
    // 失败回传必须带**本条会话的 id**：启动 shell 与回传之间用户可能已切标签。
    val guarded = remember(session, snapshot.id) {
        ShellStartGuardedSession(session) { message ->
            model.reportShellStartFailed(snapshot.id, message)
        }
    }
    TerminalView(guarded, model.settings.settings, Modifier.fillMaxSize())
}

/**
 * 状态栏（apple.css `.status-bar`）：28dp、terminal 底、顶部 1dp 分隔线。
 * 已连接：`[点][状态词] | [服务器名] | [SSH 往返 X ms] …… [负载 —][内存 —][运行 —]`
 * 指标位值一律 `—`（不造数；latency 是会话实测值）。
 */
@Composable
private fun ConnectionStatusBar(connection: ConnectionState?) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val (label, tone) = when (connection) {
        null, is ConnectionState.Disconnected -> "未连接" to com.barezen.ssh.ui.components.BadgeTone.neutral
        is ConnectionState.Connecting -> "连接中" to com.barezen.ssh.ui.components.BadgeTone.warning
        is ConnectionState.Connected -> "已连接" to com.barezen.ssh.ui.components.BadgeTone.success
        is ConnectionState.Failed -> "连接失败" to com.barezen.ssh.ui.components.BadgeTone.error
    }
    val dotColor = when (tone) {
        com.barezen.ssh.ui.components.BadgeTone.success -> extras.success
        com.barezen.ssh.ui.components.BadgeTone.warning -> extras.warning
        com.barezen.ssh.ui.components.BadgeTone.error -> colors.error
        else -> colors.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().background(extras.terminalBg)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outline)
        Row(
            Modifier
                .fillMaxWidth()
                .height(BareZenSize.statusBarHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(on = connection is ConnectionState.Connected, tone = tone, hollow = connection !is ConnectionState.Connected)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = dotColor)
            if (connection is ConnectionState.Connected) {
                StatusSeparator()
                Text(
                    connection.server.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                StatusSeparator()
                Text(
                    "SSH 往返 ${connection.latencyMs} ms",
                    style = BareZenMonoSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 指标位：未接入主机指标前绝不渲染数字（不造数红线）
                Text("负载 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("内存 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("运行 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** 状态栏分段竖线：1x12dp、border 色。 */
@Composable
private fun StatusSeparator() {
    Spacer(Modifier.width(8.dp))
    Box(
        Modifier
            .width(1.dp)
            .height(12.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
    Spacer(Modifier.width(8.dp))
}

/**
 * 会话包装：只改写 startShell——失败回调 [onShellError]（上层置错误态）并返回 no-op 通道，
 * 其余（pingMs/close）委托原会话。
 */
private class ShellStartGuardedSession(
    private val delegate: SshSession,
    private val onShellError: (String) -> Unit,
) : SshSession by delegate {
    override fun startShell(onData: (ByteArray) -> Unit, onClosed: (Throwable?) -> Unit): ShellChannel =
        try {
            delegate.startShell(onData, onClosed)
        } catch (e: Exception) {
            onShellError(e.message ?: e.toString())
            NoopShellChannel
        }
}

/** startShell 失败后的占位通道：TerminalView 的 `lateinit channel` 需要非空引用。 */
private object NoopShellChannel : ShellChannel {
    override fun write(bytes: ByteArray) {}
    override fun resize(cols: Int, rows: Int) {}
    override fun close() {}
}
