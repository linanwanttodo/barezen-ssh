// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/screens/ServersScreen.kt
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import com.barezen.barezen_ssh.ui.theme.BareZenMonoBody
import com.barezen.barezen_ssh.ui.theme.BareZenMonoSmall
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

    val servers = model.servers
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 设计包 §服务器屏·空态 mock 无头部（横幅/搜索/标签）→ 真空态只留空态块；
            // 筛选空态（有服务器但匹配为空）保留头部，否则搜索框消失无法恢复。
            if (servers.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ServersHeader(model, onRetry = { model.requestConnect(it) })
                }
            }
            if (model.visibleServers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ServersEmptyState(
                        filtered = servers.isNotEmpty(),
                        onCreate = { editing = null; dialogOpen = true },
                    )
                }
            } else {
                items(model.visibleServers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        connection = model.connection,
                        onConnect = { model.requestConnect(it) },
                        onNewTerminal = onNewTerminal,
                        onOpenFiles = onOpenFiles,
                        onEdit = { editing = server; dialogOpen = true },
                    )
                }
            }
        }

        // 右下角新建（设计稿 right/bottom 24px，primary = accent/onAccent）；
        // 真空态按编排裁决不渲染（空态块自带「新建服务器」按钮，避免同文案双节点）。
        if (servers.isNotEmpty()) {
            Button(
                onClick = { editing = null; dialogOpen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建服务器")
            }
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

/**
 * 屏头部：横幅二态（连接失败错误横幅 / 未连接计数横幅 + 禁用「全部重连」占位）、
 * 搜索框（testTag server-search）、标签 chips。
 */
@Composable
private fun ServersHeader(model: AppModel, onRetry: (Server) -> Unit) {
    val failedState = model.connection as? ConnectionState.Failed
    val connectedId = (model.connection as? ConnectionState.Connected)?.server?.id
    val unconnectedCount = model.servers.count { it.id != connectedId }
    val allTags = remember(model.servers) { model.servers.flatMap { it.tags }.distinct() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (failedState != null) {
            // 错误横幅（设计包 `.banner.error`：error-bg 底 + **1px error 描边** + radius-md(6) +
            // padding 12/16；重试按钮是 `.btn.ghost.sm.text-error` = 无描边 + error 文字色）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "连接失败：${failedState.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    // ghost + text-error：TextButton（无描边）+ error 文字色，照设计包
                    TextButton(
                        onClick = { onRetry(failedState.server) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("重试") }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(6.dp),
                // 设计包 .banner：panel 底 + 1px border + radius-md(6)。Task 4 watchlist 共 3 项，
                // 本轮只清掉「缺描边」这一项；空态图标 32dp vs mock 48px 仍未处理。
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    // 设计包 .banner padding = var(--space-3) var(--space-4) = 12 / 16
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Spacer(Modifier.weight(1f))
                    // 占位 M2：真源接入前禁用（不造数——无重连能力就不给可点入口）。
                    // 设计包 index.html:242 此按钮是 `.btn.ghost.sm`，`.btn.ghost` = 透明底 + 无描边，
                    // 故对应 M3 的 TextButton（不是 OutlinedButton——那会多出一圈描边）；
                    // 计划 Task 4 也明文允许「ghost 观感：TextButton 形态亦可」。
                    TextButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag("reconnect-all"),
                    ) { Text("全部重连") }
                }
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

/**
 * 空态 / 筛选空态（跨全宽 item，居中列）：
 * - 真空态（`filtered = false`）＝设计包 §服务器屏·空态：Dns 图标 + 「暂无服务器」+
 *   「添加第一台服务器以开始使用」+ 自带「新建服务器」按钮（右下角按钮此刻不渲染，见编排裁决）。
 * - 筛选空态（`filtered = true`）：设计包未覆盖此分支，沿用空态骨架换文案、不带按钮
 *   （右下角按钮在场，同文案会双节点；筛选恢复靠头部搜索框）。
 */
@Composable
private fun ServersEmptyState(filtered: Boolean, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(
            if (filtered) "没有匹配的服务器" else "暂无服务器",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            if (filtered) "换个关键词或标签再试试" else "添加第一台服务器以开始使用",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!filtered) {
            Button(onClick = onCreate) { Text("新建服务器") }
        }
    }
}

/** 服务器卡片：头部（dns 图标/名称/地址/标签徽章/编辑）、统计瓦片或未连接提示体、状态行、操作行。 */
@Composable
private fun ServerCard(
    server: Server,
    connection: ConnectionState,
    onConnect: (Server) -> Unit,
    onNewTerminal: (Server) -> Unit,
    onOpenFiles: () -> Unit,
    onEdit: () -> Unit,
) {
    val connectedConn = connection as? ConnectionState.Connected
    val failedConn = connection as? ConnectionState.Failed
    val isUp = connectedConn != null && connectedConn.server.id == server.id
    val isFailedHere = failedConn != null && failedConn.server.id == server.id
    val statusText = if (isUp) "已连接 · ${connectedConn.latencyMs} ms" else "未连接"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${server.host}:${server.port}",
                        style = BareZenMonoBody,
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

            if (isUp) {
                // 统计瓦片行（M4 占位，不造数）：三格 1fr，值一律「—」
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("负载", Modifier.weight(1f))
                    StatTile("内存", Modifier.weight(1f))
                    StatTile("运行", Modifier.weight(1f))
                }
            } else {
                // 未连接提示体（设计包内联样式 index.html:284）：
                // panel 底 + 1px border-subtle + radius-md(6) + padding 12 + 12sp secondary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            when {
                                isUp -> MaterialTheme.colorScheme.primary
                                isFailedHere -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                // 状态行三分支：已连接 / 连接失败（该卡）/ 未连接——颜色之外必有文字冗余
                Text(
                    if (isFailedHere) "连接失败" else statusText,
                    fontSize = 12.sp,
                    color = if (isFailedHere) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (!isUp) {
                    TextButton(onClick = { onConnect(server) }) { Text("连接") }
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
                    Text("打开文件传输")
                }
            }
        }
    }
}

/**
 * 统计瓦片（设计包组件表：6px 圆角 + border-subtle 描边）。
 * 卡片底已是 surface，瓦片走 panel(surfaceContainerLow) 形成层次；
 * 值为「—」＝ M4 占位（不造数）。
 */
@Composable
private fun StatTile(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("—", style = BareZenMonoSmall)
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
            // 设计包组件表「对话框 8px 圆角」；原实现 16dp（简报笔误记作 12→8，按实际代码改）
            shape = RoundedCornerShape(8.dp),
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
