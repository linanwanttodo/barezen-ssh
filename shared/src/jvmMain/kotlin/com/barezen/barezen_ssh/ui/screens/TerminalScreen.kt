// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/TerminalScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.ssh.ConnectionState
import com.barezen.barezen_ssh.ssh.ShellChannel
import com.barezen.barezen_ssh.ssh.SshSession
import com.barezen.barezen_ssh.terminal.TerminalView

/**
 * 终端屏（Task 8）：标签条（40dp 单 chip「终端」）+ 状态机内容区 + 状态栏（30dp/11sp）。
 * 未连接 CTA / 连接中 spin / 失败 msgbox+重试 / 已连接 TerminalView。
 */
@Composable
fun TerminalScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        TerminalTabStrip()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val st = model.connection) {
                is ConnectionState.Disconnected -> Cta("在服务器列表选择「新建终端」以开始。")
                is ConnectionState.Connecting -> ConnectingPane(st)
                is ConnectionState.Failed -> FailedPane(st) { model.requestConnect(st.server) }
                is ConnectionState.Connected -> ConnectedPane(model, st)
            }
        }
        ConnectionStatusBar(model.connection)
    }
}

/** 标签条：scHigh 底、40dp 高，单 chip「终端」active，primary 下边线 2dp（多标签 M4）。 */
@Composable
private fun TerminalTabStrip() {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "终端",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

/** 居中提示文案（未连接 CTA 与「会话缺席」占位共用）。 */
@Composable
private fun Cta(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                fontSize = 13.sp,
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
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "连接失败：${state.message}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 已连接：挂 [TerminalView]。会话缺席不以 `!!` 炸组合，startShell 抛错不炸应用。 */
@Composable
private fun ConnectedPane(model: AppModel, state: ConnectionState.Connected) {
    val session = model.shellSession
    if (session == null) {
        // Connected 而会话缺席（测试直注状态 / 会话刚被收口）：给占位而非崩溃
        Cta("正在启动终端…")
        return
    }
    // startShell 在 TerminalView 的 DisposableEffect 体内执行（effect 阶段，组合期 try/catch 够不着）：
    // 在会话外包一层 runCatching——抛错即回传 Failed（并关会话），以 no-op 通道返回让 effect
    // 照常注册 onDispose（否则 widget 不关、connector 悬挂且炸组合）。
    val guarded = remember(session) {
        ShellStartGuardedSession(session) { message ->
            model.reportShellStartFailed(state.server, message)
        }
    }
    TerminalView(guarded, Modifier.fillMaxSize())
}

/** 状态栏：scHigh 底、30dp 高、11sp；点色 Disconnected 灰 / Connecting·Connected primary / Failed error。 */
@Composable
private fun ConnectionStatusBar(connection: ConnectionState) {
    val colors = MaterialTheme.colorScheme
    val (label, dotColor) = when (connection) {
        is ConnectionState.Disconnected -> "未连接" to colors.onSurfaceVariant
        is ConnectionState.Connecting -> "连接中" to colors.primary
        is ConnectionState.Connected -> "已连接" to colors.primary
        is ConnectionState.Failed -> "连接失败" to colors.error
    }
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = dotColor)
            if (connection is ConnectionState.Connected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    connection.server.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "SSH 往返 ${connection.latencyMs} ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
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
