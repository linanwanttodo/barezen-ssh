// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/EmptyHint.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全屏统一空态：居中一列——弱图标 + 一句话（2026-10-03 随 apple.css 重设计：图标 28dp 描边灰）。
 * Dashboard / Files / Ports / Terminal / AI 各屏空态共用此组件，不再各自手写居中布局。
 * modifier 传入 fillMaxSize 时整体垂直居中（终端屏用法），默认包内容尺寸。
 */
@Composable
internal fun EmptyHint(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("empty-hint")
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // spacedBy 带 alignment：modifier 撑满（如终端屏 fillMaxSize）时内容垂直居中，包内容时行为不变
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
