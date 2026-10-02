// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/TerminalScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.SessionSnapshot
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ssh.ShellChannel
import com.barezen.ssh.ssh.SshSession
import com.barezen.ssh.terminal.TerminalView
import com.barezen.ssh.ui.theme.BareZenMonoSmall

/**
 * 终端屏：标签条（36dp，已连接渲染会话标签，`+`/「助手」为禁用占位）+
 * 状态机内容区 + 状态栏（28dp/11sp，已连接带 `—` 指标位）。
 * 未连接 CTA / 连接中 spin / 失败 msgbox+重试 / 已连接 TerminalView。
 *
 * 整屏状态机读**活动会话**（[SessionRegistry.active]），不再读单值投影：多会话并存时
 * 投影只能反映其一，而本屏必须能表达「有会话但都不是前台」这类情形。
 */
@Composable
fun TerminalScreen(model: AppModel) {
    val active = model.registry.active
    Column(Modifier.fillMaxSize()) {
        TerminalTabStrip(active)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // 先解到快照再分派：Connected 分支需要整个 SessionSnapshot（id 用于失败回传、session 用于挂终端）
            when (val snapshot = active) {
                null -> Cta("在服务器列表选择「新建终端」以开始。")
                else -> when (val st = snapshot.state) {
                    // 无活动会话与显式断开是同一档呈现（都是「没有可用的终端会话」），文案不分叉
                    is ConnectionState.Disconnected -> Cta("在服务器列表选择「新建终端」以开始。")
                    is ConnectionState.Connecting -> ConnectingPane(st)
                    is ConnectionState.Failed -> FailedPane(st) { model.requestConnect(st.server) }
                    is ConnectionState.Connected -> ConnectedPane(model, snapshot)
                }
            }
        }
        ConnectionStatusBar(active?.state)
    }
}

/**
 * 标签条（设计包 §终端方案与组件表：36dp 高、panel 底、活动标签 elevated 6dp 圆角、无下划线）。
 * 已连接会话渲染标签（close 不渲染——显式断开是开放项）；
 * `+`（多标签 M4）与「助手」（AI 侧栏 M5）渲染为禁用占位——能力未实现，不给可点入口。
 *
 * 入参是 [SessionSnapshot] 而非 [ConnectionState]：标签的身份是**会话**（id/title），
 * 多标签渲染需要会话列表，本刀先只渲染活动会话那一个，签名先按终态定型以免刀 5 再改一次。
 */
@Composable
private fun TerminalTabStrip(active: SessionSnapshot?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(colors.surfaceContainer).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active != null && active.state is ConnectionState.Connected) {
            Surface(color = colors.surfaceContainerHigh, shape = RoundedCornerShape(6.dp)) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Text(
                        active.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // 右侧组：M4 多标签占位 + M5 AI 助手占位（禁用）
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "多标签（M4 占位）")
        }
        TextButton(onClick = {}, enabled = false) {
            Text("助手", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 居中提示文案（未连接 CTA 与「会话缺席」占位共用）。 */
@Composable
private fun Cta(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 连接中：水平居中 spin 圆环 + 「正在连接 {name}…」。 */
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
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 失败：msgbox 错误 + 「重试」（重试走 requestConnect 弹连接确认）。 */
@Composable
private fun FailedPane(state: ConnectionState.Failed, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "连接失败：${state.message}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 已连接：挂 [TerminalView]。会话缺席不以 `!!` 炸组合，startShell 抛错不炸应用。 */
@Composable
private fun ConnectedPane(model: AppModel, snapshot: SessionSnapshot) {
    val session = snapshot.session
    if (session == null) {
        // Connected 而会话缺席（测试直注状态 / 会话刚被收口）：给占位而非崩溃
        Cta("正在启动终端…")
        return
    }
    // startShell 在 TerminalView 的 DisposableEffect 体内执行（effect 阶段，组合期 try/catch 够不着）：
    // 在会话外包一层 runCatching——抛错即回传 Failed（并关会话），以 no-op 通道返回让 effect
    // 照常注册 onDispose（否则 widget 不关、connector 悬挂且炸组合）。
    //
    // 失败回传必须带**本条会话的 id**（不是活动会话）：启动 shell 与回传之间用户可能已切标签，
    // 按活动会话落失败会关掉用户正在用的那一条。remember 的 key 里带上 id 亦保证切换会话时
    // 包装器随之重建，回调闭包不会指向旧会话。
    val guarded = remember(session, snapshot.id) {
        ShellStartGuardedSession(session) { message ->
            model.reportShellStartFailed(snapshot.id, message)
        }
    }
    TerminalView(guarded, model.settings.settings, Modifier.fillMaxSize())
}

/**
 * 状态栏（组件表：28dp 高、panel 底、顶部 1dp 分隔线、11sp 文字）。
 * 已连接：`[圆点][状态词] | [服务器名] | [SSH 往返 X ms] …… [负载 —][内存 —][运行 —]`——
 * 指标位值一律 `—`（M4 占位，**不造数**；latency 是会话实测值，非造数）。
 * 其余状态只渲染圆点+状态词（照设计空态，不带指标位）。
 */
@Composable
private fun ConnectionStatusBar(connection: ConnectionState?) {
    val colors = MaterialTheme.colorScheme
    // null = 无活动会话：与 Disconnected 同档文案（「未连接」），不新造状态词
    val (label, dotColor) = when (connection) {
        null, is ConnectionState.Disconnected -> "未连接" to colors.onSurfaceVariant
        is ConnectionState.Connecting -> "连接中" to colors.primary
        is ConnectionState.Connected -> "已连接" to colors.primary
        is ConnectionState.Failed -> "连接失败" to colors.error
    }
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainer)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = dotColor)
            if (connection is ConnectionState.Connected) {
                StatusSeparator()
                Text(
                    connection.server.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                StatusSeparator()
                Text(
                    "SSH 往返 ${connection.latencyMs} ms",
                    style = BareZenMonoSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 指标位：M4 占位，值固定 `—`——未接入主机指标前绝不渲染数字（不造数红线）
                Text("负载 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("内存 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("运行 —", style = BareZenMonoSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** 状态栏分段竖线：1×12dp、border 色（照设计 `.status-bar .sep`）。 */
@Composable
private fun StatusSeparator() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.width(8.dp))
}

/**
 * 会话包装：只改写 startShell——失败回调 [onShellError]（上层置错误态）并返回 no-op 通道，
 * 其余（pingMs/close）委托原会话。TerminalView 的 effect 因此永不感知抛错。
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

/** startShell 失败后的占位通道：TerminalView 的 `lateinit channel` 需要非空引用，写/调窗/关均无效果。 */
private object NoopShellChannel : ShellChannel {
    override fun write(bytes: ByteArray) {}
    override fun resize(cols: Int, rows: Int) {}
    override fun close() {}
}
