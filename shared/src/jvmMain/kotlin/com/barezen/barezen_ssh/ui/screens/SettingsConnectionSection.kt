// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsConnectionSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.servers.Server
import com.barezen.barezen_ssh.settings.ConflictPolicy

/** 设置·连接分类（设计 §8.3）：启动时连接、隐藏服务器地址、文件名冲突策略。 */
@Composable
fun ConnectionSettingsSection(settings: SettingsModel, servers: List<Server>) {
    val s = settings.settings

    SettingsSectionScaffold("连接") {
        // 启动时连接：选一台，不是连全部（启动连 N 台会一次打出 N 条 SSH 连接）
        val labels = listOf("不自动连接") + servers.map { it.name.ifBlank { it.host } }
        val selectedIndex = servers.indexOfFirst { it.id == s.autoConnectServerId }
            .let { if (it < 0) 0 else it + 1 }
        val target = servers.firstOrNull { it.id == s.autoConnectServerId }
        val desc = when {
            s.autoConnectServerId == null -> null
            target == null -> "该服务器已不存在"
            else -> null
        }
        ChoiceRow(
            title = "启动时连接",
            desc = desc,
            options = labels,
            selectedIndex = selectedIndex,
            onSelect = { i ->
                settings.update { it.copy(autoConnectServerId = if (i == 0) null else servers[i - 1].id) }
            },
        )

        ToggleRow(
            title = "隐藏服务器地址",
            desc = "直播/录屏时把服务器列表与连接对话框里的地址与端口显示为掩码（编辑对话框不受影响）",
            checked = s.hideAddresses,
            onCheckedChange = { v -> settings.update { it.copy(hideAddresses = v) } },
        )

        val policyLabels = listOf("询问", "覆盖", "跳过", "重命名")
        val policies = listOf(
            ConflictPolicy.ASK, ConflictPolicy.OVERWRITE, ConflictPolicy.SKIP, ConflictPolicy.RENAME,
        )
        ChoiceRow(
            title = "文件名冲突策略",
            desc = "（待文件传输接入后生效）",
            options = policyLabels,
            selectedIndex = policies.indexOf(s.conflictPolicy).coerceAtLeast(0),
            onSelect = { i -> settings.update { it.copy(conflictPolicy = policies[i]) } },
        )
    }
}
