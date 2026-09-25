// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/shell/AppShell.kt
package com.barezen.barezen_ssh.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Settings
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.app.Destination
import com.barezen.barezen_ssh.ui.screens.PlaceholderScreen
import com.barezen.barezen_ssh.ui.screens.ServersScreen
import androidx.compose.ui.graphics.vector.ImageVector

private fun railIcon(to: Destination, selected: Boolean): ImageVector = when (to) {
    Destination.SERVERS -> if (selected) Icons.Filled.Dns else Icons.Outlined.Dns
    Destination.TERMINAL -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
    Destination.FILES -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    Destination.DASHBOARD -> if (selected) Icons.Filled.QueryStats else Icons.Outlined.QueryStats
    Destination.PORTS -> if (selected) Icons.Filled.SwapHoriz else Icons.Outlined.SwapHoriz
    Destination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

@Composable
fun BareZenAppContent(model: AppModel) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        NavigationRail {
            Destination.entries.filter { it in listOf(Destination.SERVERS, Destination.TERMINAL, Destination.FILES, Destination.DASHBOARD) }
                .forEach { dest ->
                    NavigationRailItem(
                        selected = model.current == dest,
                        onClick = { model.navigate(dest) },
                        icon = { Icon(railIcon(dest, model.current == dest), contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            NavigationRailItem(
                selected = model.current == Destination.PORTS,
                onClick = { model.navigate(Destination.PORTS) },
                icon = { Badge { Text("2") }; Icon(railIcon(Destination.PORTS, model.current == Destination.PORTS), contentDescription = "端口转发") },
                label = null,
            )
            NavigationRailItem(
                selected = model.current == Destination.SETTINGS,
                onClick = { model.navigate(Destination.SETTINGS) },
                icon = { Icon(railIcon(Destination.SETTINGS, model.current == Destination.SETTINGS), contentDescription = "设置") },
                label = { Text("设置") },
            )
        }
        Box(Modifier.fillMaxSize().padding(top = 0.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                when (model.current) {
                    Destination.SERVERS -> ServersScreen(
                        model = model,
                        onNewTerminal = { model.navigate(Destination.TERMINAL) },
                        onOpenFiles = { model.navigate(Destination.FILES) },
                    )
                    Destination.TERMINAL -> com.barezen.barezen_ssh.ui.screens.TerminalScreenPlaceholder()
                    Destination.FILES -> PlaceholderScreen("文件")
                    Destination.DASHBOARD -> PlaceholderScreen("仪表盘")
                    Destination.PORTS -> PlaceholderScreen("端口转发")
                    Destination.SETTINGS -> PlaceholderScreen("设置")
                }
            }
        }
    }
}
