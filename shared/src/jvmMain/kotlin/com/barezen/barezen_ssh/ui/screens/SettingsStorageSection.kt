// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsStorageSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.settings.ConnectionTransfer
import com.barezen.barezen_ssh.settings.OpenSshConfigParser
import com.barezen.barezen_ssh.settings.SkipReason
import java.io.File
import javax.swing.JFileChooser

/**
 * 设置·存储分类（设计 §8.4）：连接导出/导入 + 数据目录。
 * 导出走**两步流程**（选位置 → 确认对话框含默认不勾的「包含私钥路径」）——
 * 逐次确认而非记住勾选，防止用户遗忘导致每次导出都悄悄泄露路径。
 */
@Composable
fun StorageSettingsSection(
    settings: SettingsModel,
    servers: List<Server>,
    onImport: (List<Server>) -> Unit,
) {
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var includeKeyPath by remember { mutableStateOf(false) }

    fun chooseSave(defaultName: String): File? {
        val chooser = JFileChooser()
        chooser.selectedFile = File(defaultName)
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun chooseOpen(initialDir: String?): File? {
        val chooser = JFileChooser(initialDir?.let { File(it) })
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun exportTo(format: String, file: File, includeKeyPath: Boolean, count: Int) {
        val text = if (format == "json") {
            ConnectionTransfer.exportJson(servers, includeKeyPath)
        } else {
            ConnectionTransfer.exportCsv(servers)
        }
        file.writeText(text)
        resultMessage = "已导出 $count 台服务器到 ${file.absolutePath}"
    }

    fun importFrom(file: File, openSsh: Boolean) {
        resultMessage = try {
            if (openSsh) {
                val r = OpenSshConfigParser.parse(file.readText())
                onImport(r.added)
                buildString {
                    append("新增 ${r.added.size} 台")
                    val reasons = buildReasonCounts(r.skipped)
                    if (reasons.isNotEmpty()) append("，跳过 ${r.skipped.size} 台（$reasons）")
                    if (r.includeSkipped) append("；未跟随 Include 指令")
                }
            } else {
                val imported = ConnectionTransfer.importJson(file.readText())
                val r = ConnectionTransfer.merge(servers, imported)
                onImport(r.added)
                buildString {
                    append("新增 ${r.added.size} 台")
                    val reasons = buildReasonCounts(r.skipped)
                    if (reasons.isNotEmpty()) append("，跳过 ${r.skipped.size} 台（$reasons）")
                }
            }
        } catch (e: Exception) {
            "导入失败：${e.message ?: e.toString()}"
        }
    }

    // 测试与语义：数据目录绝对路径始终渲染（设计 §13.8 storageShowsAbsoluteDataDir）
    val dataDir = File(System.getProperty("user.home"), ".barezen")
    val clipboard = LocalClipboardManager.current

    SettingsSectionScaffold("存储") {
        ActionRow(
            title = "导出连接",
            desc = null,
            actions = listOf(
                "导出 JSON…" to {
                    chooseSave("connections.json")?.let { f ->
                        pendingExport = PendingExport("json", f)
                        includeKeyPath = false
                    }
                },
                "导出 CSV…" to {
                    chooseSave("connections.csv")?.let { f ->
                        pendingExport = PendingExport("csv", f)
                        includeKeyPath = false
                    }
                },
            ),
        )
        ActionRow(
            title = "导入连接",
            desc = null,
            actions = listOf(
                "导入 JSON…" to {
                    chooseOpen(null)?.let { importFrom(it, openSsh = false) }
                },
                "导入 OpenSSH 配置…" to {
                    chooseOpen(System.getProperty("user.home") + "/.ssh")?.let { importFrom(it, openSsh = true) }
                },
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColumnHelper(
                title = "数据目录",
                desc = dataDir.absolutePath,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                try {
                    java.awt.Desktop.getDesktop().open(dataDir)
                } catch (_: Exception) {
                    clipboard.setText(AnnotatedString(dataDir.absolutePath))
                    resultMessage = "无法打开目录，路径已复制到剪贴板"
                }
            }) { Text("打开目录", fontSize = 12.sp) }
        }
        resultMessage?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    // 导出确认对话框：复选框默认不勾（安全优先，逐次确认）
    pendingExport?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text("导出连接", fontSize = 16.sp) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeKeyPath, onCheckedChange = { includeKeyPath = it })
                        Text("包含私钥路径", fontSize = 13.sp)
                    }
                    Text(
                        "私钥路径会暴露你的用户名与目录结构，仅在需要完整迁移时勾选。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportTo(pending.format, pending.file, includeKeyPath, servers.size)
                    pendingExport = null
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) { Text("取消") }
            },
        )
    }
}

private data class PendingExport(val format: String, val file: File)

/** 跳过原因按设计 §10.4 聚合计数：如「1 台缺少用户名、1 台为通配 Host」。 */
private fun buildReasonCounts(skipped: List<SkipReason>): String =
    skipped.groupingBy { it }.eachCount().entries.joinToString("、") { (reason, n) ->
        val label = when (reason) {
            is SkipReason.NoUser -> "缺少用户名"
            is SkipReason.WildcardHost -> "通配 Host"
            is SkipReason.Duplicate -> "已存在相同连接"
        }
        "$n 台$label"
    }

/** 两行文本（标题 + 描述）的轻量列，供数据目录行使用。 */
@Composable
private fun ColumnHelper(title: String, desc: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
