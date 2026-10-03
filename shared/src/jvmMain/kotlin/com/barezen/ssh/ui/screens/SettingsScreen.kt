// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsScreen.kt
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.NoopAiKeyStore
import com.barezen.ssh.ui.theme.BareZenSize
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.LocalBareZenColors

/** 设置八分类（apple.html `.settings-nav`）。 */
private val SettingsCategories =
    listOf("外观", "终端", "连接", "智能助手", "凭据", "存储", "更新", "关于")

/**
 * 设置屏（apple.html `.settings-layout`）：左列 200dp 分类导航（bg 底、右 1px 细线）
 * + 右侧内容（32px 内边距滚动区）。
 *
 * [aiKeys] 为 AI API key 存取端口；未提供时用 [NoopAiKeyStore]。
 * 顶部通知区在 loadNotice / saveError 非空时出现。
 */
@Composable
fun SettingsScreen(model: AppModel, aiKeys: AiKeyStore = NoopAiKeyStore) {
    var selected by remember { mutableStateOf(0) }
    val settings = model.settings
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current

    Row(Modifier.fillMaxSize()) {
        // 左列分类导航
        Column(
            Modifier
                .width(BareZenSize.settingsNavWidth)
                .fillMaxHeight()
                .background(colors.surfaceContainerLow)
                .padding(horizontal = BareZenSpace.md, vertical = BareZenSpace.xl),
        ) {
            SettingsCategories.forEachIndexed { index, name ->
                val active = index == selected
                val shape = RoundedCornerShape(8.dp)
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(shape)
                        .background(if (active) colors.surfaceContainerHighest else Color.Transparent)
                        // 焦点环须在 clickable 之前
                        // 焦点环须在 clickable 之前（见 FocusRing.kt 约束）
                        .then(
                            if (focused) Modifier.border(2.dp, extras.accentOnSubtle, shape)
                            else Modifier
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { selected = index }
                        .testTag("settings-nav-$index")
                        .padding(horizontal = BareZenSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
        // 细线
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.outlineVariant),
        )

        // 右列
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            settings.loadNotice?.let { notice ->
                Box(Modifier.fillMaxWidth().padding(BareZenSpace.xxl)) {
                    SettingsNotice(notice) { settings.dismissLoadNotice() }
                }
            }
            settings.saveError?.let { err ->
                Box(Modifier.fillMaxWidth().padding(BareZenSpace.xxl)) {
                    SettingsNotice(err) { settings.dismissSaveError() }
                }
            }

            when (selected) {
                0 -> AppearanceSettingsSection(settings)
                1 -> TerminalSettingsSection(settings)
                2 -> ConnectionSettingsSection(settings, model.servers)
                3 -> AiSettingsSection(settings, aiKeys)
                4 -> SettingsSectionScaffold("凭据") {
                    SettingCard {
                        Text(
                            "凭据库属 M3，尚未接入。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = BareZenSpace.lg),
                        )
                        Text(
                            "（系统钥匙串集成在后续里程碑实现）",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = BareZenSpace.lg),
                        )
                    }
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
