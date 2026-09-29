// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/shell/AppShell.kt
package com.barezen.barezen_ssh.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.app.Destination
import com.barezen.barezen_ssh.ui.screens.ConnectDialog
import com.barezen.barezen_ssh.ui.screens.DashboardScreen
import com.barezen.barezen_ssh.ui.screens.FilesScreen
import com.barezen.barezen_ssh.ui.screens.PortsScreen
import com.barezen.barezen_ssh.ui.screens.ServersScreen
import com.barezen.barezen_ssh.ui.screens.SettingsScreen
import com.barezen.barezen_ssh.ui.screens.TerminalScreen

/**
 * 应用外壳：200px 可折叠侧边导航（设计包「外壳/侧边导航」）+ 右侧内容区路由。
 * 连接确认对话框与连接中/失败覆盖层为壳级挂载——Task 5 约定：重写壳时原样保留这两处挂载。
 */
@Composable
fun BareZenAppContent(model: AppModel) {
    var collapsed by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppSidebar(
            model = model,
            collapsed = collapsed,
            onToggle = { collapsed = !collapsed },
            modifier = Modifier.fillMaxHeight(),
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            when (model.current) {
                Destination.DASHBOARD -> DashboardScreen()
                Destination.SERVERS -> ServersScreen(
                    model = model,
                    onNewTerminal = { model.requestConnect(it) },
                    onOpenFiles = { model.navigate(Destination.FILES) },
                )
                Destination.TERMINAL -> TerminalScreen(model)
                Destination.FILES -> FilesScreen()
                Destination.PORTS -> PortsScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }

    // 连接确认对话框：确认 → confirmConnect（内部委托 startConnect 主路径），取消 → 关闭
    model.pendingConnect?.let { server ->
        ConnectDialog(
            server = server,
            onResult = { auth ->
                if (auth != null) model.confirmConnect(auth) else model.dismissConnect()
            },
        )
    }

    // 连接中/失败覆盖层：与 ConnectDialog 同为壳级挂载（Task 8 重写壳时原样迁移这两行）
    ConnectFlowOverlays(model)
}

/** 侧边导航：展开 200dp / 折叠 56dp，surface 底、右缘 1dp borderSubtle 竖线；不渲染 badge（非目标）。 */
@Composable
private fun AppSidebar(
    model: AppModel,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(if (collapsed) 56.dp else 200.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            SidebarHeader(collapsed)
            SidebarItem(Destination.DASHBOARD, model, collapsed)
            SidebarItem(Destination.SERVERS, model, collapsed)
            SidebarItem(Destination.TERMINAL, model, collapsed)
            SidebarItem(Destination.FILES, model, collapsed)
            // 设计包 mock-nav-spacer：文件与端口转发之间的弹性间隔
            Spacer(Modifier.weight(1f))
            SidebarItem(Destination.PORTS, model, collapsed)
            SidebarItem(Destination.SETTINGS, model, collapsed)
            Spacer(Modifier.weight(1f))
            SidebarToggle(collapsed, onToggle)
        }
        // 右缘 1dp borderSubtle 竖线（Box 后置）
        Box(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** 顶部标题行：高 56dp、padding 16；折叠时只留图标。 */
@Composable
private fun SidebarHeader(collapsed: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = if (collapsed) 18.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Terminal,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        if (!collapsed) {
            Text("BareZen", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun navIcon(to: Destination): ImageVector = when (to) {
    // Monitoring 在 CMP 1.7.3 material-icons-extended 不存在 → 简报授权回退 QueryStats
    Destination.DASHBOARD -> Icons.Outlined.QueryStats
    Destination.SERVERS -> Icons.Outlined.Dns
    Destination.TERMINAL -> Icons.Outlined.Terminal
    Destination.FILES -> Icons.Outlined.Folder
    Destination.PORTS -> Icons.Outlined.SyncAlt
    Destination.SETTINGS -> Icons.Outlined.Settings
}

/** 导航项：40dp 行、clip 6dp、选中 panel 底；折叠时仅图标（水平居中）。 */
@Composable
private fun SidebarItem(dest: Destination, model: AppModel, collapsed: Boolean) {
    val isSelected = model.current == dest
    Row(
        Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = if (collapsed) 18.dp else 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            )
            .clickable { model.navigate(dest) }
            .semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = navIcon(dest),
            contentDescription = dest.label,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!collapsed) {
            Text(
                dest.label,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 底部收起/展开按钮：展开态 ChevronLeft + 「收起」，折叠态仅 ChevronRight。 */
@Composable
private fun SidebarToggle(collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = if (collapsed) 18.dp else 12.dp)
            .clickable { onToggle() }
            .testTag("sidebar-toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = if (collapsed) "展开侧边栏" else "收起侧边栏",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!collapsed) {
            Text(
                "收起",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
