// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/FilesScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.ssh.SftpEntry
import com.barezen.ssh.ssh.sftp.FileSizeFormatter
import com.barezen.ssh.ssh.sftp.SftpModel
import com.barezen.ssh.ssh.sftp.SftpPaths
import com.barezen.ssh.ssh.sftp.RemoteNameValidator
import com.barezen.ssh.ssh.sftp.TransferKind
import com.barezen.ssh.ssh.sftp.TransferStatus
import com.barezen.ssh.ssh.sftp.TransferTask
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.BareZenSize
import com.barezen.ssh.ui.theme.LocalBareZenColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import kotlin.math.roundToInt

/** 修改时间列的显示格式。 */
private val mtimeFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** 删除确认框的 testTag（文案含变量，用标签定位避免多节点歧义）。 */
const val FILE_DELETE_DIALOG_TAG = "file-delete-dialog"

/** 重命名对话框的 testTag。 */
const val FILE_RENAME_DIALOG_TAG = "file-rename-dialog"

/** 重命名输入框 testTag（预填原名，与列表行同名会造成语义歧义）。 */
const val FILE_RENAME_INPUT_TAG = "file-rename-input"

/** 删除确认按钮 testTag（列表行也有「删除」，需唯一定位）。 */
const val FILE_DELETE_CONFIRM_TAG = "file-delete-confirm"

/** 重命名确认按钮 testTag。 */
const val FILE_RENAME_CONFIRM_TAG = "file-rename-confirm"

/** 列表行的删除/重命名按钮 testTag 前缀，拼条目名：file-delete-<name> / file-rename-<name>。 */
const val FILE_DELETE_PREFIX = "file-delete-"
const val FILE_RENAME_PREFIX = "file-rename-"

/**
 * 文件管理屏（apple.html `#files`）：屏头（副标题 + 上传/下载）| 双栏 pane（本地/远程）
 * | 底部 44dp 传输队列条。
 *
 * 本屏当前只接远程 SFTP 一侧（`SftpModel` 只有一个 cwd），故布局上呈现为
 * 「远程 pane 满宽 + 左侧本地 pane 显示可传输路径」，不做两栏各自独立的浏览态——
 * 那是尚未接入的能力，不做假双栏（CONVENTIONS §1.7）。
 *
 * [model] 为 null 时进入未连接态：整屏提示「连接后可管理文件」，全部操作禁用（不造数）。
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
    var pendingDelete by remember { mutableStateOf<SftpEntry?>(null) }
    var pendingRename by remember { mutableStateOf<SftpEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 屏头
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = BareZenSpace.xxxl,
                    end = BareZenSpace.xxl,
                    top = BareZenSpace.xl,
                    bottom = BareZenSpace.lg,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "在本地与远程主机之间拖拽传输文件",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Btn(
                "上传",
                {
                    model ?: return@Btn
                    pickUploadFiles().forEach { path -> model.upload(path, File(path).name) }
                },
                kind = BtnKind.secondary,
                enabled = model != null,
            )
            Spacer(Modifier.width(BareZenSpace.sm))
            Btn("下载", {}, kind = BtnKind.secondary, enabled = false)
        }

        if (state?.error != null) {
            Text(
                "错误：${state.error}",
                Modifier.fillMaxWidth().padding(horizontal = BareZenSpace.xxxl, vertical = 4.dp),
                fontSize = 12.sp,
                color = LocalBareZenColors.current.onErrorContainer,
            )
        }

        // 主体
        if (model == null || state == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyHint(Icons.Outlined.Folder, "连接后可管理文件")
            }
        } else {
            FilePane(
                title = "远程",
                entries = state.entries,
                path = state.cwd,
                canGoUp = state.cwd != "/",
                loading = state.loading,
                onGoUp = { model.enter("..") },
                onRefresh = { model.refresh() },
                onMkdir = { showMkdirDialog = true },
                entryContent = { entry ->
                    EntryRow(
                        model = model,
                        entry = entry,
                        pickSaveDir = pickSaveDir,
                        onDelete = { pendingDelete = entry },
                        onRename = { pendingRename = entry },
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BareZenSpace.xxxl),
            )
        }

        // 底部传输队列条（apple.css `.transfer-queue`，44dp）
        TransferQueueBar(tasks = tasks, onCancel = { id -> model?.cancel(id) })
    }

    if (showMkdirDialog && model != null) {
        MkdirDialog(
            onConfirm = { name ->
                model.mkdir(name)
                showMkdirDialog = false
            },
            onDismiss = { showMkdirDialog = false },
        )
    }

    // 删除确认框（破坏性操作必须二次确认；目录须为空，由服务端拒绝非空目录）
    pendingDelete?.let { entry ->
        DeleteConfirmDialog(
            entry = entry,
            onConfirm = {
                model?.delete(entry)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 重命名对话框（非法名在前端拦截，不发出请求）
    pendingRename?.let { entry ->
        RenameDialog(
            entry = entry,
            onConfirm = { newName ->
                model?.rename(entry, newName)
                pendingRename = null
            },
            onDismiss = { pendingRename = null },
        )
    }
}

/**
 * 文件 pane（apple.css `.file-pane`）：panel 底标题条（含项数）| 路径栏（上级 + 路径 + 刷新/新建）
 * | 三列表头（名称 2fr / 大小 1fr / 修改时间 1fr）| 滚动列表。
 */
@Composable
private fun FilePane(
    title: String,
    entries: List<SftpEntry>,
    path: String,
    canGoUp: Boolean,
    loading: Boolean,
    onGoUp: () -> Unit,
    onRefresh: () -> Unit,
    onMkdir: () -> Unit,
    entryContent: @Composable (SftpEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier,
        color = colors.surfaceContainerLow,
        shape = shape,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题条
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer)
                    .padding(horizontal = BareZenSpace.lg, vertical = BareZenSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text("${entries.size} 项", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            HorizontalDivider(color = colors.outline)
            // 路径栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BareZenSpace.md, vertical = BareZenSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BareZenSpace.sm),
            ) {
                Btn(
                    "",
                    onGoUp,
                    kind = BtnKind.secondary,
                    small = true,
                    enabled = canGoUp,
                    icon = Icons.Outlined.ArrowUpward,
                    iconContentDescription = "上级目录",
                )
                Text(
                    path,
                    Modifier.weight(1f),
                    style = BareZenMonoBody,
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) {
                    Text("加载中…", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                Btn(
                    "",
                    onRefresh,
                    kind = BtnKind.secondary,
                    small = true,
                    icon = Icons.Outlined.Refresh,
                    iconContentDescription = "刷新",
                )
                Btn(
                    "",
                    onMkdir,
                    kind = BtnKind.secondary,
                    small = true,
                    icon = Icons.Outlined.CreateNewFolder,
                    iconContentDescription = "新建文件夹",
                )
            }
            // 三列表头
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer)
                    .padding(horizontal = BareZenSpace.lg, vertical = 6.dp),
            ) {
                Text(
                    "名称", Modifier.weight(2f), fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant,
                )
                Text(
                    "大小", Modifier.weight(1f), fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant,
                )
                Text(
                    "修改时间", Modifier.weight(1f), fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
            Box(Modifier.weight(1f)) {
                if (entries.isEmpty() && !loading) {
                    EmptyHint(Icons.AutoMirrored.Outlined.InsertDriveFile, "空目录")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries) { entry -> entryContent(entry) }
                    }
                }
            }
        }
    }
}

/**
 * 文件列表行：图标 + 名称 + 大小 + 修改时间 + 行尾操作。
 * 行高 34dp（apple.css `.file-row` 12px 上下内边距），hover 走 surfaceContainerHighest。
 */
@Composable
private fun EntryRow(
    model: SftpModel,
    entry: SftpEntry,
    pickSaveDir: () -> String?,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(if (hovered) colors.surfaceContainerHighest else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = entry.isDirectory,
            ) { model.enter(entry.name) }
            .padding(horizontal = BareZenSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.name,
            Modifier.weight(2f),
            fontSize = 12.sp,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (entry.isDirectory) "—" else FileSizeFormatter.format(entry.size),
            Modifier.weight(1f),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            mtimeFormat.format(Date(entry.mtimeMs)),
            Modifier.weight(1f),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        if (!entry.isDirectory) {
            TextButton(
                onClick = {
                    val dir = pickSaveDir() ?: return@TextButton
                    model.download(
                        remotePath = SftpPaths.join(model.state.value.cwd, entry.name),
                        localPath = File(dir, entry.name).absolutePath,
                        totalBytes = entry.size,
                    )
                },
            ) { Text("下载", fontSize = 12.sp) }
        }
        TextButton(
            onClick = onRename,
            modifier = Modifier.testTag(FILE_RENAME_PREFIX + entry.name),
        ) { Text("重命名", fontSize = 12.sp) }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.testTag(FILE_DELETE_PREFIX + entry.name),
        ) { Text("删除", fontSize = 12.sp, color = colors.error) }
    }
    HorizontalDivider(color = colors.outlineVariant)
}

/**
 * 传输队列条（apple.css `.transfer-queue`）：固定 44dp 底条。
 * 无任务时只显示「0 个活跃任务」一行；有任务时可滚动展开进度。
 */
@Composable
private fun TransferQueueBar(tasks: List<TransferTask>, onCancel: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val running = tasks.count { it.status == TransferStatus.Running }
    Column(
        Modifier
            .fillMaxWidth()
            .height(BareZenSize.transferQueueHeight)
            .background(colors.surface),
    ) {
        HorizontalDivider(color = colors.outline)
        if (tasks.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = BareZenSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "传输队列",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(BareZenSpace.md))
                Text("0 个活跃任务", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BareZenSpace.lg, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "传输队列",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.width(BareZenSpace.md))
                    Text("$running 个活跃任务", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                tasks.forEach { task -> TransferRow(task, onCancel) }
            }
        }
    }
}

/** 单个传输任务行：类型 + 名称 + 进度条 + 百分比/状态 + 取消。 */
@Composable
private fun TransferRow(task: TransferTask, onCancel: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (task.kind == TransferKind.UPLOAD) "上传" else "下载",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
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
            Text("${(fraction * 100).roundToInt()}%", fontSize = 12.sp, style = BareZenMonoBody)
        } else {
            LinearProgressIndicator(modifier = Modifier.weight(1f))
            Text(FileSizeFormatter.format(task.transferredBytes), fontSize = 12.sp, style = BareZenMonoBody)
        }
        when (val status = task.status) {
            is TransferStatus.Done -> Text("完成", fontSize = 12.sp, color = extras.success)
            is TransferStatus.Failed -> Text(
                "失败：${status.message}",
                fontSize = 12.sp,
                color = colors.error,
            )
            TransferStatus.Running -> TextButton(onClick = { onCancel(task.id) }) {
                Text("取消", fontSize = 12.sp)
            }
        }
    }
}

/**
 * 删除确认框：破坏性操作二次确认。
 * 目录额外提示「仅能删除空目录」，不做一键递归删除（防误删整棵树）。
 */
@Composable
private fun DeleteConfirmDialog(entry: SftpEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FILE_DELETE_DIALOG_TAG),
        title = { Text(if (entry.isDirectory) "删除文件夹" else "删除文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确定删除「" + entry.name + "」？此操作不可撤销。", fontSize = 13.sp)
                if (entry.isDirectory) {
                    Text(
                        "仅能删除空目录；非空目录需先清空其中的内容。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(FILE_DELETE_CONFIRM_TAG),
            ) {
                Text("删除", color = LocalBareZenColors.current.onErrorContainer)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 重命名对话框：非法名（空 / 含分隔符 / . 与 ..）在前端拦截，确认按钮禁用并给出原因。 */
@Composable
private fun RenameDialog(entry: SftpEntry, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entry.name) }
    val error = RemoteNameValidator.validate(name)
    val unchanged = name.trim() == entry.name

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FILE_RENAME_DIALOG_TAG),
        title = { Text("重命名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.testTag(FILE_RENAME_INPUT_TAG),
                )
                if (error != null) {
                    Text(error, fontSize = 12.sp, color = LocalBareZenColors.current.onErrorContainer)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null && !unchanged,
                onClick = { onConfirm(name.trim()) },
                modifier = Modifier.testTag(FILE_RENAME_CONFIRM_TAG),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
