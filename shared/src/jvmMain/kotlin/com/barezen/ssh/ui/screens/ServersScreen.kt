// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ServersScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.ConnectionState
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.theme.BareZenSpace

/**
 * 服务器列表屏（apple.html `#servers`）：warn 横幅 / 搜索 + 标签筛选 / 280px 自适应卡片网格 /
 * 底部「+ 新建服务器」。
 *
 * LazyVerticalGrid 即整屏滚动容器（头部为跨全宽 span 项）——不能把 LazyVerticalGrid 放进
 * verticalScroll 的 Column（会被 checkScrollableContainerConstraints 以 infinity maximum height 拒绝）。
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
    // 「已连接」= 该服务器存在**至少一条 Connected 会话**（可能多条）
    val connectedIds = model.registry.sessions
        .filter { it.state is ConnectionState.Connected }
        .map { it.server.id }
        .toSet()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = BareZenSpace.xxxl,
                top = BareZenSpace.xl,
                end = BareZenSpace.xxxl,
                bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
            verticalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
        ) {
            // 设计包：真空态无头部（横幅/搜索/标签）→ 只留空态块；
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
                        hideAddresses = model.settings.settings.hideAddresses,
                        connected = server.id in connectedIds,
                        // 延迟只属于**活动会话**：非活动会话的往返时延不代表这台机器当前的可用性。
                        activeLatencyMs = model.registry.active
                            ?.takeIf { it.server.id == server.id && it.state is ConnectionState.Connected }
                            ?.let { (it.state as ConnectionState.Connected).latencyMs },
                        sessionCount = model.registry.sessions.count { it.server.id == server.id },
                        onConnect = { model.requestConnect(it) },
                        onNewTerminal = onNewTerminal,
                        onOpenFiles = onOpenFiles,
                        onEdit = { editing = server; dialogOpen = true },
                    )
                }
            }
        }

        // 底部新建（apple.html `.screen-footer` 右对齐；真空态按编排不渲染，空态块自带按钮）
        if (servers.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .padding(end = BareZenSpace.xxl, bottom = BareZenSpace.xxl),
                horizontalArrangement = Arrangement.End,
            ) {
                Btn(
                    "新建服务器",
                    { editing = null; dialogOpen = true },
                    kind = BtnKind.primary,
                    icon = Icons.Filled.Add,
                    iconContentDescription = null,
                )
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
