// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ServersScreenParts.kt
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.barezen.ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.barezen.ssh.ui.components.Badge
import com.barezen.ssh.ui.components.BadgeTone
import com.barezen.ssh.ui.components.Banner
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzCard
import com.barezen.ssh.ui.components.FilterChipPill
import com.barezen.ssh.ui.components.StatusDot
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenMonoSmall
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.LocalBareZenColors

/*
 * ServersScreen 的列表向展示部件：ServersHeader / ServersEmptyState / ServerCard / StatTile / TagBadge。
 * 编辑表单在 ServerEditDialog.kt；屏本体与接线留在 ServersScreen.kt。
 */

/**
 * 屏头部（apple.html `.banner` + `.toolbar`）：
 * 横幅二态（连接失败错误横幅 / 未连接计数横幅 + 禁用「全部重连」占位）、
 * 搜索框（testTag server-search）、标签筛选 chip 组（含「全部」）。
 *
 * 横幅只反映**活动会话**的失败态：非活动会话的失败属于标签条/终端屏的事。
 */
@Composable
internal fun ServersHeader(model: AppModel, onRetry: (Server) -> Unit) {
    val failedState = model.registry.active?.state as? ConnectionState.Failed
    val connectedIds = model.registry.sessions
        .filter { it.state is ConnectionState.Connected }
        .map { it.server.id }
        .toSet()
    val unconnectedCount = model.servers.count { it.id !in connectedIds }
    val allTags = remember(model.servers) { model.servers.flatMap { it.tags }.distinct() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BareZenSpace.md)) {
        if (failedState != null) {
            Banner(
                text = "连接失败：${failedState.message}",
                tone = BadgeTone.error,
                icon = Icons.Outlined.Error,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Btn("重试", { onRetry(failedState.server) }, kind = BtnKind.ghost, small = true)
            }
        } else {
            Banner(
                text = "$unconnectedCount 台服务器未连接",
                tone = BadgeTone.warning,
                icon = Icons.Outlined.Sync,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 占位：无重连能力就不给可点入口（不造功能）
                Btn("全部重连", {}, kind = BtnKind.ghost, small = true, enabled = false)
            }
        }

        // 工具条（apple.css `.toolbar`）：搜索占位 + chip 组
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BareZenSpace.md),
        ) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.setQuery(it) },
                modifier = Modifier
                    .weight(1f)
                    .size(width = 400.dp, height = 32.dp)
                    .testTag("server-search"),
                placeholder = { Text("按名称、地址或标签搜索服务器", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = searchFieldColors(),
            )
            if (allTags.isNotEmpty()) {
                FilterChipPill(
                    "全部",
                    selected = model.selectedTag == null,
                    onClick = { model.selectTag(null) },
                )
                allTags.forEach { tag ->
                    FilterChipPill(
                        tag,
                        selected = model.selectedTag == tag,
                        onClick = {
                            model.selectTag(if (model.selectedTag == tag) null else tag)
                        },
                    )
                }
            }
        }
    }
}

/** 搜索框：贴合 apple.css `.input.search`（panel 底 + 描边 + 32 高），去掉 M3 的浮动标签与大内边距。 */
@Composable
private fun searchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedBorderColor = LocalBareZenColors.current.accentOnSubtle,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

/**
 * 空态 / 筛选空态：Dns 图标 + 标题 + 提示；真空态自带「新建服务器」按钮。
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
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (filtered) "换个关键词或标签再试试" else "添加第一台服务器以开始使用",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!filtered) {
            Btn("新建服务器", onCreate, kind = BtnKind.primary)
        }
    }
}

/**
 * 服务器卡片（apple.html `.server-card`）：bg 底卡内套 panel 瓦片；头部（名称/地址/标签/编辑）|
 * 统计三格或未连接提示 | 状态行 | 双按钮操作行。
 *
 * [connected] 由调用方按会话注册表算好；[activeLatencyMs] 仅在「本卡就是活动会话」时非空
 * ——延迟属于会话而非服务器，多条会话时给出单条延迟即是撒谎。
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
    val colors = MaterialTheme.colorScheme
    val isUp = connected
    val statusText = when {
        !isUp -> "未连接"
        sessionCount > 1 -> "已连接 · $sessionCount 个会话"
        activeLatencyMs != null -> "已连接 · $activeLatencyMs ms"
        else -> "已连接"
    }

    val cardInteraction = remember { MutableInteractionSource() }
    val cardHovered by cardInteraction.collectIsHoveredAsState()
    BzCard(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(cardInteraction),
        raised = false,
        contentPadding = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    if (cardHovered) colors.surfaceContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .padding(BareZenSpace.xl),
            verticalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
        ) {
            // 头部
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        server.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (hideAddresses) ADDRESS_MASK else "${server.host}:${server.port}",
                        style = BareZenMonoBody,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (server.tags.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            server.tags.forEach { TagBadge(it) }
                        }
                    }
                }
                Spacer(Modifier.width(BareZenSpace.md))
                Btn("编辑", onEdit, small = true)
            }

            if (isUp) {
                // 统计三格（值一律「—」= 未接入主机指标前不造数）
                Row(horizontalArrangement = Arrangement.spacedBy(BareZenSpace.sm)) {
                    StatTile("负载", Modifier.weight(1f))
                    StatTile("内存", Modifier.weight(1f))
                    StatTile("运行", Modifier.weight(1f))
                }
            } else {
                // 未连接提示体（apple.css `.server-hint`）
                Surface(
                    color = colors.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(BareZenSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Insights,
                            null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "连接后查看负载、内存和运行时间。",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // 状态行
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUp) {
                    StatusDot(
                        on = true,
                        modifier = Modifier.testTag("server-status-dot-connected"),
                    )
                } else {
                    StatusDot(
                        on = false,
                        hollow = true,
                        modifier = Modifier.testTag("server-status-dot-disconnected"),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(statusText, fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (!isUp) {
                    Btn("连接", { onConnect(server) }, kind = BtnKind.ghost, small = true)
                }
            }

            // 操作行
            Row(horizontalArrangement = Arrangement.spacedBy(BareZenSpace.sm)) {
                Btn(
                    "新建终端",
                    { onNewTerminal(server) },
                    kind = BtnKind.primary,
                    icon = Icons.Filled.Terminal,
                    iconContentDescription = null,
                    modifier = Modifier.weight(1f),
                )
                Btn(
                    "文件传输",
                    onOpenFiles,
                    kind = BtnKind.secondary,
                    icon = Icons.Outlined.Upload,
                    iconContentDescription = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 统计瓦片（apple.css `.stat`）：panel 底 + 1px border-subtle + 圆角 8；
 * 标签 10sp 大写字距展开，值等宽 14sp/600。值为「—」= 不造数。
 */
@Composable
internal fun StatTile(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(BareZenSpace.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Text(
                "—",
                style = BareZenMonoSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
        }
    }
}

/** 标签徽章（apple.css `.chip.sm`）。 */
@Composable
internal fun TagBadge(tag: String) {
    Badge(tag, tone = BadgeTone.neutral)
}
