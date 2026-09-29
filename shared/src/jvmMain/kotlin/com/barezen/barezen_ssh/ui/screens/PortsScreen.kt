// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/PortsScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 端口转发屏（index.html「端口转发屏 · 占位布局（M3）」）：
 * 信息横幅（0 条为真值——无活动转发）+ 表单/列表两张占位卡；「新建转发」禁用。
 */
@Composable
fun PortsScreen() {
    Column(Modifier.fillMaxSize()) {
        // 屏头（通用式样：56dp、horizontal 24）
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("端口转发", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = {}, enabled = false) { Text("新建转发") }
        }

        Column(
            Modifier.weight(1f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 信息横幅：panel 底 + 8dp 圆角 + 1dp border；「0 条」是真值（不造数）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "活动转发：0 条；需要先建立 SSH 连接才能启动新的转发。",
                        fontSize = 13.sp,
                    )
                }
            }

            // 卡 1：转发表单占位
            PlaceholderCard(
                title = "转发表单占位（M3）",
                subtitle = "类型：本地监听 / 服务器监听 / SOCKS5",
            )

            // 卡 2：已保存的配置 / 活动转发列表占位
            PlaceholderCard(title = "已保存的配置 / 活动转发列表占位（M3）", subtitle = null)
        }
    }
}

/** 占位卡（通用卡片皮肤：background 底、8dp 圆角、1dp border）。 */
@Composable
private fun PlaceholderCard(title: String, subtitle: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
