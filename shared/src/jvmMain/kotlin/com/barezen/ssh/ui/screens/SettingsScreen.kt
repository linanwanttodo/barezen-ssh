// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.ui.theme.focusRing

/** 设置八分类（左列导航；右列 6 真分类路由到 section，智能助手/凭据为诚实占位）。 */
private val SettingsCategories =
    listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")

/**
 * 设置屏（index.html「设置屏 · 分类布局」）：左列 200dp 分类导航（客户端状态切换）+
 * 右侧内容。右列按 [selected] 路由到 6 个分类 section，智能助手/凭据为诚实占位。
 *
 * 顶部通知区在 [SettingsModel.loadNotice] / [SettingsModel.saveError] 非空时出现，
 * 各带一个「知道了」关闭按钮。
 */
@Composable
fun SettingsScreen(model: AppModel) {
    var selected by remember { mutableStateOf(0) }
    val settings = model.settings

    Row(Modifier.fillMaxSize()) {
        // 左列：200dp、surface 底（与主侧栏同层）、右 1dp border
        Column(
            Modifier.width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsCategories.forEachIndexed { index, name ->
                val active = index == selected
                val catShape = RoundedCornerShape(6.dp)
                Row(
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .clip(catShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.surfaceContainer
                            else Color.Transparent
                        )
                        // 键盘焦点环（需求 §2 硬约束 3）：须在 clickable 之前才能观察到焦点
                        .focusRing(catShape)
                        .clickable { selected = index }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline))

        // 右列：顶部通知区（按需）+ 分类内容
        Column(Modifier.weight(1f).fillMaxHeight()) {
            settings.loadNotice?.let { notice ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    SettingsNotice(notice) { settings.dismissLoadNotice() }
                }
            }
            settings.saveError?.let { err ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    SettingsNotice(err) { settings.dismissSaveError() }
                }
            }

            when (selected) {
                0 -> AppearanceSettingsSection(settings)
                1 -> TerminalSettingsSection(settings)
                2 -> ConnectionSettingsSection(settings, model.servers)
                3 -> SettingsSectionScaffold("智能助手") {
                    Text(
                        "智能助手属 M5，尚未接入。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text(
                        "（AI 会话侧栏与命令审批流在后续里程碑实现）",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                4 -> SettingsSectionScaffold("凭据") {
                    Text(
                        "凭据库属 M3，尚未接入。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text(
                        "（系统钥匙串集成在后续里程碑实现）",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                5 -> StorageSettingsSection(
                    settings = settings,
                    servers = model.servers,
                    onImport = { list -> list.forEach { model.saveServer(it) } },
                )
                6 -> UpdateSettingsSection(
                    settings = settings,
                    checker = remember { com.barezen.ssh.settings.UpdateChecker.production() },
                )
                7 -> AboutSettingsSection(settings)
            }
        }
    }
}

/** 六个真分类 section 均已在独立文件实现（Task 7–12），设置屏本文件不再保留过渡空壳。 */
