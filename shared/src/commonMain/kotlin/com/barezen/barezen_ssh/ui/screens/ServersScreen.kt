// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/screens/ServersScreen.kt
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.servers.StoredAuth
import com.barezen.barezen_ssh.ssh.ConnectionState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 服务器列表屏（prototype §S1）：横幅 / 搜索 / 标签筛选 / 服务器卡片网格 + 新建、编辑对话框。
 *
 * LazyVerticalGrid 即整屏滚动容器（头部为跨全宽 span 项）——
 * 不能把 LazyVerticalGrid 放进 verticalScroll 的 Column（会被
 * checkScrollableContainerConstraints 以「infinity maximum height」拒绝）。
 */
@Composable
fun ServersScreen(
    model: AppModel,
    onNewTerminal: (Server) -> Unit,
    onOpenFiles: () -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Server?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ServersHeader(model)
            }
            items(model.visibleServers, key = { it.id }) { server ->
                ServerCard(
                    server = server,
                    connection = model.connection,
                    onNewTerminal = onNewTerminal,
                    onOpenFiles = onOpenFiles,
                    onEdit = { editing = server; dialogOpen = true },
                )
            }
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { editing = null; dialogOpen = true },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("新建服务器", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (dialogOpen) {
        ServerEditDialog(
            server = editing,
            onDismiss = { dialogOpen = false },
            onSave = { saved ->
                model.saveServer(saved)
                dialogOpen = false
            },
        )
    }
}

/** 屏头部：未连接横幅、搜索框（testTag server-search）、标签 chips。 */
@Composable
private fun ServersHeader(model: AppModel) {
    val connectedId = (model.connection as? ConnectionState.Connected)?.server?.id
    val unconnectedCount = model.servers.count { it.id != connectedId }
    val allTags = remember(model.servers) { model.servers.flatMap { it.tags }.distinct() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$unconnectedCount 台服务器未连接",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        OutlinedTextField(
            value = model.query,
            onValueChange = { model.setQuery(it) },
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).testTag("server-search"),
            placeholder = { Text("按名称、地址或标签搜索服务器") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )

        if (allTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = model.selectedTag == tag,
                        onClick = {
                            model.selectTag(if (model.selectedTag == tag) null else tag)
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

/** 服务器卡片：头部（dns 图标/名称/地址/标签徽章/编辑）、未连接提示体、状态行、操作行。 */
@Composable
private fun ServerCard(
    server: Server,
    connection: ConnectionState,
    onNewTerminal: (Server) -> Unit,
    onOpenFiles: () -> Unit,
    onEdit: () -> Unit,
) {
    val connectedConn = connection as? ConnectionState.Connected
    val isUp = connectedConn != null && connectedConn.server.id == server.id
    val statusText = if (isUp) "已连接 · ${connectedConn.latencyMs} ms" else "未连接"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${server.host}:${server.port}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (server.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            server.tags.forEach { tag -> TagBadge(tag) }
                        }
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑服务器",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (!isUp) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "连接后查看负载、内存和运行时间。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isUp) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (!isUp) {
                    // 连接流程 Task 8 接入；本任务仅展示状态。
                    TextButton(onClick = {}) { Text("连接") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNewTerminal(server) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建终端")
                }
                OutlinedButton(onClick = onOpenFiles, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开文件管理")
                }
            }
        }
    }
}

/** 标签徽章：rounded 999、scHighest 背景。 */
@Composable
private fun TagBadge(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            tag,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 新增/编辑共用对话框。字段：名称 / 地址 / 端口（默认 22）/ 用户名 /
 * 认证方式（密码、私钥文件）/ 标签（逗号分隔）；标题「新建服务器」/「编辑服务器」。
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ServerEditDialog(
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
            shape = RoundedCornerShape(16.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !useKey, onClick = { useKey = false })
                    Text("密码")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = useKey, onClick = { useKey = true })
                    Text("私钥文件")
                }
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
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
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
                        }
                    ) { Text("保存") }
                }
            }
        }
    }
}
