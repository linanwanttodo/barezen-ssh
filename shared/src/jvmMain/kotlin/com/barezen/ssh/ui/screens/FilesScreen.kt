// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/FilesScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.sftp.FileSizeFormatter
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ssh.sftp.SftpPaths
import com.barezen.ssh.ssh.sftp.TransferKind
import com.barezen.ssh.ssh.sftp.TransferStatus
import com.barezen.ssh.ssh.sftp.TransferTask
import com.barezen.ssh.ui.theme.BareZenMonoBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import kotlin.math.roundToInt

/** 修改时间列的显示格式。 */
private val mtimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 文件管理屏：路径栏 + 文件列表 + 底部传输进度区。
 *
 * [model] 为 null 时进入未连接态：整屏提示「连接后可管理文件」，全部操作禁用（不造数）。
 * 本地文件/目录选择经 [pickUploadFiles]/[pickSaveDir] 注入（swing JFileChooser 为默认实现）。
 * AppShell 接线方式：连接成功后在 LaunchedEffect(connected) 中创建 SftpModel（fsFactory 取
 * session.newSftp()），以 remember{ } 持有并传入 FilesScreen(model)；断开时置回 null。
 */
@Composable
fun FilesScreen(
    model: SftpModel? = null,
    pickUploadFiles: () -> List<String> = ::chooseUploadFiles,
    pickSaveDir: () -> String? = ::chooseSaveDir,
) {
    val state = model?.state?.collectAsState()?.value
    val tasks = model?.transfers?.collectAsState()?.value ?: emptyList()
    var showMkdirDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 屏头（通用式样：56dp、horizontal 24）
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("文件传输", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    model ?: return@OutlinedButton
                    pickUploadFiles().forEach { path -> model.upload(path, File(path).name) }
                },
                enabled = model != null,
            ) { Text("上传") }
        }

        // 路径栏：上级 + 当前路径 + 加载中 + 刷新 + 新建文件夹
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { model?.enter("..") },
                enabled = model != null && state?.cwd != "/",
            ) { Text("上级") }
            Spacer(Modifier.width(8.dp))
            Text(
                state?.cwd ?: "—",
                Modifier.weight(1f),
                style = BareZenMonoBody,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state?.loading == true) {
                Text(
                    "加载中…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = { model?.refresh() }, enabled = model != null) { Text("刷新") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { showMkdirDialog = true },
                enabled = model != null,
            ) { Text("新建文件夹") }
        }

        // 错误提示（列表操作失败保留上次成功列表）
        if (state?.error != null) {
            Text(
                "错误：${state.error}",
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // 主体
        if (model == null || state == null) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "连接后可管理文件",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.entries.isEmpty() && !state.loading) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "空目录",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
                items(state.entries) { entry -> EntryRow(model, entry, pickSaveDir) }
            }
        }

        // 底部传输进度区
        if (tasks.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text("传输任务", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                tasks.forEach { task -> TransferRow(task, onCancel = { model?.cancel(task.id) }) }
            }
        }
    }

    // 新建文件夹对话框
    if (showMkdirDialog && model != null) {
        MkdirDialog(
            onConfirm = { name ->
                model.mkdir(name)
                showMkdirDialog = false
            },
            onDismiss = { showMkdirDialog = false },
        )
    }
}

/** 文件列表行：图标 + 名称 + 大小 + 修改时间；目录整行可点进入，文件提供下载按钮。 */
@Composable
private fun EntryRow(model: SftpModel, entry: SftpEntry, pickSaveDir: () -> String?) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = entry.isDirectory) { model.enter(entry.name) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            entry.name,
            Modifier.weight(1f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (entry.isDirectory) "—" else FileSizeFormatter.format(entry.size),
            Modifier.width(88.dp),
            fontSize = 12.sp,
            style = BareZenMonoBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            mtimeFormat.format(Date(entry.mtimeMs)),
            Modifier.width(120.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        if (!entry.isDirectory) {
            TextButton(onClick = {
                val dir = pickSaveDir() ?: return@TextButton
                model.download(
                    remotePath = SftpPaths.join(model.state.value.cwd, entry.name),
                    localPath = File(dir, entry.name).absolutePath,
                    totalBytes = entry.size,
                )
            }) { Text("下载") }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 单个传输任务行：类型 + 名称 + 进度条 + 百分比/状态 + 取消。 */
@Composable
private fun TransferRow(task: TransferTask, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (task.kind == TransferKind.UPLOAD) "上传" else "下载",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            task.name,
            Modifier.width(160.dp),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.totalBytes > 0) {
            val fraction = (task.transferredBytes.toDouble() / task.totalBytes).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f))
            Text(
                "${(fraction * 100).roundToInt()}%",
                fontSize = 12.sp,
                style = BareZenMonoBody,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.weight(1f))
            Text(
                FileSizeFormatter.format(task.transferredBytes),
                fontSize = 12.sp,
                style = BareZenMonoBody,
            )
        }
        when (val status = task.status) {
            is TransferStatus.Done -> Text("完成", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            is TransferStatus.Failed -> Text(
                "失败：${status.message}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
            TransferStatus.Running -> TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

/** 新建文件夹对话框：输入名称，空名不可确认。 */
@Composable
private fun MkdirDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("文件夹名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---- 本地文件/目录选择（swing，默认实现；测试注入假选择器）----

private fun chooseUploadFiles(): List<String> {
    val chooser = JFileChooser()
    chooser.isMultiSelectionEnabled = true
    chooser.dialogTitle = "选择要上传的文件"
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.map { it.absolutePath }
    } else {
        emptyList()
    }
}

private fun chooseSaveDir(): String? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "选择保存目录"
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
