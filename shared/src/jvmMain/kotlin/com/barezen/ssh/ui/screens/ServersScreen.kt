// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/ServersScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.servers.Server

/**
 * 服务器列表屏（prototype §S1）：横幅 / 搜索 / 标签筛选 / 服务器卡片网格 + 新建、编辑对话框。
 *
 * 展示部件（头部 / 空态 / 卡片 / 瓦片 / 徽章 / 编辑对话框）在 ServersScreenParts.kt。
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
                        hideAddresses = model.settings.settings.hideAddresses,
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
