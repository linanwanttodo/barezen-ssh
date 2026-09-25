// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/ui/screens/PlaceholderScreen.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("「$label」功能尚未启用。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TerminalScreenPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("在服务器列表选择「新建终端」以开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
