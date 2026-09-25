// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/ConnectDialog.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.StoredAuth
import com.barezen.barezen_ssh.ssh.AuthMethod
import javax.swing.JFileChooser

/**
 * 连接确认对话框：展示连接目标（用户名/端口只读），认证分段选密码或私钥，
 * 返回 [AuthMethod]（null = 取消）。
 *
 * 安全：密码与私钥口令只存在于本组合的 remember 状态里 —— 不落盘、不进模型、不打日志，
 * 对话框离组合即消失（对话框的唯一输出是 [AuthMethod]）。
 */
@Composable
fun ConnectDialog(server: Server, onResult: (AuthMethod?) -> Unit) {
    var useKey by remember(server) { mutableStateOf(server.auth is StoredAuth.Key) }
    var password by remember(server) { mutableStateOf("") }
    var keyPath by remember(server) { mutableStateOf((server.auth as? StoredAuth.Key)?.keyPath ?: "") }
    var passphrase by remember(server) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text("连接到 ${server.name}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 连接目标只读展示：本对话框只回传 AuthMethod，编辑值无法进入 ConnectRequest，
                // 故不渲染成可改字段以免误导（改用户/端口走「编辑服务器」）。
                OutlinedTextField(
                    value = server.user,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = server.port.toString(),
                    onValueChange = {},
                    enabled = false,
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useKey,
                        onClick = { useKey = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("密码", fontSize = 13.sp) },
                    )
                    SegmentedButton(
                        selected = useKey,
                        onClick = { useKey = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("私钥", fontSize = 13.sp) },
                    )
                }
                if (!useKey) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = keyPath,
                            onValueChange = { keyPath = it },
                            label = { Text("私钥路径") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { choosePrivateKeyFile()?.let { keyPath = it } }) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = "选择私钥文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("私钥口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult(
                        if (useKey) AuthMethod.PrivateKey(keyPath.trim(), passphrase.ifBlank { null })
                        else AuthMethod.Password(password),
                    )
                }
            ) { Text("连接") }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("取消") } },
    )
}

/** 桌面文件选择（EDT 内同步弹出）：选中返回绝对路径，取消返回 null。 */
private fun choosePrivateKeyFile(): String? {
    val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
