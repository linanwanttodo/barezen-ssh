// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/ConnectFlowOverlays.kt
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.Destination
import com.barezen.ssh.app.SessionId
import com.barezen.ssh.ssh.ConnectionState

/**
 * 连接中 / 失败全局覆盖层（设计包「连接流程」三态）。
 *
 * 终端屏自理 Connecting/Failed 状态机，故在终端屏不叠加，避免双弹。
 *
 * 覆盖层只反映**活动会话**：多会话下可能同时有多条会话在连接，同时弹多个进度框没有意义，
 * 且「取消」按钮不知道该掐哪一条。非活动会话的连接进度属于标签条的事（第 4 刀）。
 */
@Composable
fun ConnectFlowOverlays(model: AppModel) {
    if (model.current == Destination.TERMINAL) return
    val active = model.registry.active
    val activeId = active?.id
    val connection = active?.state
    // 失败框的「已关闭」按**会话 id** 记账，不按状态对象：两条会话可能产生内容相同的
    // Failed 值，按值记会被另一条会话的失败误判成「已经关过了」而静默不弹。
    var dismissed by remember { mutableStateOf<SessionId?>(null) }

    if (connection is ConnectionState.Connecting) {
        AlertDialog(
            onDismissRequest = { model.cancelConnect() },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在连接 ${connection.server.name}…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { model.cancelConnect() }) { Text("取消") } },
        )
    }
    if (connection is ConnectionState.Failed && dismissed != activeId) {
        AlertDialog(
            onDismissRequest = { dismissed = activeId },
            text = {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "连接失败：${connection.message}",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dismissed = activeId
                        model.requestConnect(connection.server)
                    },
                ) { Text("重试") }
            },
            dismissButton = { TextButton(onClick = { dismissed = activeId }) { Text("取消") } },
        )
    }
}
