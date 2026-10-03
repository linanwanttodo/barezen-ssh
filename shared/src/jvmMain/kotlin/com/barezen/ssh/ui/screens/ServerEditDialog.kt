// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ServerEditDialog.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.SegmentedControl
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * ServersScreen 的编辑表单（自 ServersScreen.kt 原样拆出，文案/令牌未改）：
 * 新增/编辑共用对话框。列表向部件在 ServersScreenParts.kt，屏本体与接线在 ServersScreen.kt。
 */

/**
 * 新增/编辑共用对话框。字段：名称 / 地址 / 端口（默认 22）/ 用户名 /
 * 认证方式（密码、私钥文件）/ 标签（逗号分隔）；标题「新建服务器」/「编辑服务器」。
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun ServerEditDialog(
    server: Server?,
    onDismiss: () -> Unit,
    onSave: (Server) -> Unit,
) {
    var name by remember(server) { mutableStateOf(server?.name ?: "") }
    var host by remember(server) { mutableStateOf(server?.host ?: "") }
    var port by remember(server) { mutableStateOf((server?.port ?: 22).toString()) }
    var user by remember(server) { mutableStateOf(server?.user ?: "") }
    var useKey by remember(server) { mutableStateOf(server?.auth is StoredAuth.Key) }
    var keyPath by remember(server) { mutableStateOf((server?.auth as? StoredAuth.Key)?.keyPath ?: "") }
    var tags by remember(server) { mutableStateOf(server?.tags?.joinToString(",") ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // 设计包组件表「对话框 8px 圆角」；原实现 16dp（简报笔误记作 12→8，按实际代码改）
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (server == null) "新建服务器" else "编辑服务器",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentedControl(
                    options = listOf("密码", "私钥文件"),
                    selectedIndex = if (useKey) 1 else 0,
                    onSelect = { useKey = it == 1 },
                )
                if (useKey) {
                    OutlinedTextField(
                        value = keyPath,
                        onValueChange = { keyPath = it },
                        label = { Text("私钥路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Btn("取消", onDismiss, kind = BtnKind.ghost)
                    Btn(
                        "保存",
                        {
                            onSave(
                                Server(
                                    id = server?.id ?: Uuid.random().toString(),
                                    name = name.trim(),
                                    host = host.trim(),
                                    port = port.trim().toIntOrNull() ?: 22,
                                    user = user.trim(),
                                    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    auth = if (useKey) StoredAuth.Key(keyPath.trim()) else StoredAuth.Password,
                                )
                            )
                        },
                        kind = BtnKind.primary,
                    )
                }
            }
        }
    }
}
