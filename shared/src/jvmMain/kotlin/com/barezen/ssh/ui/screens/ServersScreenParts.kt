// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ServersScreenParts.kt
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ui.ADDRESS_MASK
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenMonoSmall

/*
 * ServersScreen 的列表向展示部件（自 ServersScreen.kt 原样拆出，文案/令牌/testTag 未改）：
 * ServersHeader / ServersEmptyState / ServerCard / StatTile / TagBadge。
 * 编辑表单在 ServerEditDialog.kt；屏本体与接线留在 ServersScreen.kt。
 * 部件只接收算好的状态，不访问模型之外的平台能力。
 */

/**
 * 屏头部：横幅二态（连接失败错误横幅 / 未连接计数横幅 + 禁用「全部重连」占位）、
 * 搜索框（testTag server-search）、标签 chips。
 *
 * 横幅只反映**活动会话**的失败态：非活动会话的失败属于标签条/终端屏的事，
 * 拿到这里会把「前端正常」的服务器页错误地染成失败态。
 */
@Composable
internal fun ServersHeader(model: AppModel, onRetry: (Server) -> Unit) {
    val failedState = model.registry.active?.state as? ConnectionState.Failed
    // 已连接 = 该服务器有一条及以上 Connected 会话（多会话下同一台机器可能连开两条）
    val connectedIds = model.registry.sessions
        .filter { it.state is ConnectionState.Connected }
        .map { it.server.id }
        .toSet()
    val unconnectedCount = model.servers.count { it.id !in connectedIds }
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
internal fun ServersEmptyState(filtered: Boolean, onCreate: () -> Unit) {
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

/**
 * 服务器卡片：头部（dns 图标/名称/地址/标签徽章/编辑）、统计瓦片或未连接提示体、状态行、操作行。
 *
 * [connected] 由调用方按会话注册表算好：该服务器是否存在 Connected 会话（可能多条）。
 * [activeLatencyMs] 仅在「本卡就是活动会话」时非空——延迟属于会话而非服务器，
 * 多条会话时给出单条延迟即是撒谎（spec §3.5）。
 * [sessionCount] 为 0 时按未连接呈现。
 */
@Composable
internal fun ServerCard(
    server: Server,
    hideAddresses: Boolean,
    connected: Boolean,
    activeLatencyMs: Long?,
    sessionCount: Int,
    onConnect: (Server) -> Unit,
    onNewTerminal: (Server) -> Unit,
    onOpenFiles: () -> Unit,
    onEdit: () -> Unit,
) {
    val isUp = connected
    // 多会话徽标：>=2 条时报条数且不带延迟（延迟已不属于该卡的单一语义）；
    // 恰 1 条且为活动会话时报「已连接 · N ms」，与改造前逐字一致。
    val statusText = when {
        !isUp -> "未连接"
        sessionCount > 1 -> "已连接 · $sessionCount 个会话"
        activeLatencyMs != null -> "已连接 · $activeLatencyMs ms"
        else -> "已连接"
    }

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
                        if (hideAddresses) ADDRESS_MASK else "${server.host}:${server.port}",
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
                            // 状态点二值：已连接 primary，其余中性——失败不再染红此点。
                            // 理由：失败是**会话**的属性；本卡的「已连接」判定问的是"该机器是否有
                            // 连接会话"，若同时把失败态涂在点上，会出现「红点 + 已连接」的自相矛盾
                            // （同一台机器一条会话失败、另一条正常）。失败在终端屏与横幅上如实呈现。
                            if (isUp) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                // 状态行二分支：已连接（带延迟或条数）/ 未连接——颜色之外必有文字冗余
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun StatTile(label: String, modifier: Modifier = Modifier) {
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
internal fun TagBadge(tag: String) {
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

