// shared/src/jvmMain/kotlin/com/barezen/barezen_ssh/ui/screens/SettingsUpdateSection.kt
package com.barezen.barezen_ssh.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.barezen_ssh.app.SettingsModel
import com.barezen.barezen_ssh.settings.UpdateChannel
import com.barezen.barezen_ssh.settings.UpdateChecker
import com.barezen.barezen_ssh.settings.UpdateResult
import kotlinx.coroutines.launch

private val REPO_REGEX = Regex("""^[\w.-]+/[\w.-]+$""")

/**
 * 设置·更新分类（设计 §8.5 / §11.4）：更新源可配置，**未配置不发起任何请求、不声称任何版本**。
 * 不下载、不自动安装——只做检查与跳转。
 */
@Composable
fun UpdateSettingsSection(settings: SettingsModel, checker: UpdateChecker) {
    val s = settings.settings
    val scope = rememberCoroutineScope()
    var repoInput by remember(s.updateRepo) { mutableStateOf(s.updateRepo.orEmpty()) }
    var repoError by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf("未配置更新源") }
    var checking by remember { mutableStateOf(false) }

    // 更新源：placeholder owner/repo；格式合法即生效并保存（即时生效，D5）
    OutlinedTextField(
        value = repoInput,
        onValueChange = {
            repoInput = it
            repoError = it.isNotEmpty() && !REPO_REGEX.matches(it)
            if (it.isEmpty() || REPO_REGEX.matches(it)) {
                settings.update { cur -> cur.copy(updateRepo = it.takeIf { r -> REPO_REGEX.matches(r) }) }
            }
        },
        isError = repoError,
        placeholder = { Text("owner/repo", fontSize = 13.sp) },
        supportingText = {
            if (repoError) Text("格式应为 owner/repo", fontSize = 12.sp)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("update-repo-input"),
    )

    val channelLabels = listOf("稳定", "预览")
    val channels = listOf(UpdateChannel.STABLE, UpdateChannel.PREVIEW)
    ChoiceRow(
        title = "发布频道",
        desc = "稳定仅匹配正式发布；预览包含预发布",
        options = channelLabels,
        selectedIndex = channels.indexOf(s.updateChannel),
        onSelect = { i -> settings.update { it.copy(updateChannel = channels[i]) } },
    )

    ToggleRow(
        title = "自动检查",
        desc = "启动时检查更新",
        checked = s.autoCheckUpdates,
        onCheckedChange = { v -> settings.update { it.copy(autoCheckUpdates = v) } },
    )

    // 立即检查：updateRepo 为空时禁用（不造数：没配置就别说能检查）
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnHelper2(
            title = "立即检查",
            desc = if (s.updateRepo.isNullOrBlank()) "请先填写更新源" else null,
            modifier = Modifier.weight(1f),
        )
        val enabled = !s.updateRepo.isNullOrBlank() && !checking
        Button(
            onClick = {
                val repo = s.updateRepo ?: return@Button
                checking = true
                checkResult = "正在检查…"
                scope.launch {
                    val r = checker.check(repo, s.updateChannel, com.barezen.barezen_ssh.BuildInfo.VERSION)
                    checkResult = describe(r)
                    checking = false
                }
            },
            enabled = enabled,
            modifier = Modifier.testTag("update-check-button"),
        ) { Text("立即检查", fontSize = 12.sp) }
    }

    Text(
        checkResult,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private fun describe(r: UpdateResult): String = when (r) {
    UpdateResult.NotConfigured -> "未配置更新源"
    is UpdateResult.UpToDate -> "已是最新版本（${r.checkedAt} 检查）"
    is UpdateResult.NewerAvailable -> "发现新版本 ${r.latest}（当前 ${com.barezen.barezen_ssh.BuildInfo.VERSION}）"
    is UpdateResult.Uncomparable -> "最新发布为 ${r.latest}，但版本号格式无法比较"
    is UpdateResult.Failed -> r.message
}

/** 两行文本（标题 + 可选描述），供「立即检查」行使用。 */
@Composable
private fun ColumnHelper2(title: String, desc: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        if (desc != null) {
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
