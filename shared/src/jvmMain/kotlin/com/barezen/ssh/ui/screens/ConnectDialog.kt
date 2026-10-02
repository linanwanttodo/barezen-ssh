// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ConnectDialog.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.servers.Server
import com.barezen.ssh.servers.StoredAuth
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ui.ADDRESS_MASK
import javax.swing.JFileChooser

/** 勾选框 testTag（文案多节点歧义时用标签定位）。 */
const val CONNECT_REMEMBER_TAG = "connect-remember"

/**
 * 连接确认对话框：展示连接目标（用户名/端口只读），认证分段选密码或私钥，
 * 回传 [AuthMethod] 与「是否勾选记住凭据」；[AuthMethod] 为 null = 取消。
 *
 * 安全：未勾选「记住凭据」时，密码与私钥口令只存在于本组合的 remember 状态里 ——
 * 不落盘、不进模型、不打日志，对话框离组合即消失；勾选后由调用方
 * （[com.barezen.ssh.ui.shell.AppShell]）写入系统钥匙串，本对话框自身不落盘。
 * 安全说明文案随分支变化（见下方两处 Text），保证任何分支都不断言「不写入本地文件」。
 *
 * @param prefill 钥匙串中已有的认证方式：按类型预填密码或私钥路径 + 口令（不跨类型填充）。
 * @param keychainAvailable 平台钥匙串可用性；false 时不渲染勾选框，改为明示凭据不会保存。
 * @param onResult (认证方式, 是否记住凭据)；取消为 (null, false)。
 */
@Composable
fun ConnectDialog(
    server: Server,
    hideAddresses: Boolean = false,
    prefill: AuthMethod? = null,
    keychainAvailable: Boolean = false,
    onResult: (auth: AuthMethod?, remember: Boolean) -> Unit,
) {
    // 预填只作初始值：认证方式由 prefill 决定，但用户可以改（改后不再跨类型回填）。
    val prefilledKey = (prefill as? AuthMethod.PrivateKey)
        ?.takeIf { server.auth is StoredAuth.Key || it.passphrase != null }
    var useKey by remember(server) { mutableStateOf(prefilledKey != null || server.auth is StoredAuth.Key) }
    var password by remember(server) {
        mutableStateOf((prefill as? AuthMethod.Password)?.value.orEmpty())
    }
    var keyPath by remember(server) {
        mutableStateOf(prefilledKey?.keyPath ?: (server.auth as? StoredAuth.Key)?.keyPath ?: "")
    }
    // 口令框留空时回落到钥匙串口令（避免把已存口令再显示成一个可读的明文字段）
    var passphrase by remember(server) { mutableStateOf("") }
    var rememberCredential by remember(server) { mutableStateOf(false) }
    // 回传与文案共用的单一判据：钥匙串可用且用户勾选 → 凭据真的会进钥匙串
    val storedInKeychain = keychainAvailable && rememberCredential

    AlertDialog(
        onDismissRequest = { onResult(null, false) },
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
                    value = if (hideAddresses) ADDRESS_MASK else server.port.toString(),
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

                // 凭据落点信息条：私钥路径本就以明文存在服务器记录里，故这一行不泄露任何新信息；
                // 密码明文绝不在此回显（只按类型回落到默认的「密码」分段）。
                if (prefill != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "已存入系统钥匙串",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (keychainAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberCredential,
                            onCheckedChange = { rememberCredential = it },
                            modifier = Modifier.testTag(CONNECT_REMEMBER_TAG),
                        )
                        Text("记住凭据", fontSize = 13.sp)
                    }
                } else {
                    // 钥匙串不可用：显式说明回退行为（不造可用性，不给可点勾选框）。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "系统钥匙串不可用，凭据不会保存",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 安全说明与实际落点一致：未勾选＝仅内存、不写盘；勾选＝进系统钥匙串；
                // 钥匙串不可用＝说明回退（此时勾选框不渲染，rememberCredential 恒为 false）。
                Text(
                    when {
                        storedInKeychain -> "勾选后，凭据将存入系统钥匙串。"
                        keychainAvailable -> "密码仅保存在内存中，不会写入本地文件。"
                        else -> "密码仅保存在内存中；本机钥匙串不可用，凭据不会保存。"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult(
                        if (useKey) {
                            // 口令框为空 = 沿用钥匙串里的口令（用户没有输入新口令）
                            val effective = passphrase.ifBlank { prefilledKey?.passphrase }
                            AuthMethod.PrivateKey(keyPath.trim(), effective?.ifBlank { null })
                        } else {
                            AuthMethod.Password(password)
                        },
                        storedInKeychain,
                    )
                }
            ) { Text("连接") }
        },
        dismissButton = { TextButton(onClick = { onResult(null, false) }) { Text("取消") } },
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
