// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/SettingsAiSection.kt（T-7：设置·智能助手）
package com.barezen.ssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.SettingsModel
import com.barezen.ssh.settings.AppSettings

/**
 * 设置 · 智能助手（T-7）：OpenAI 兼容 API 的三项配置。
 * - endpoint / model 即时落盘（settings.update，与外观/连接等分区一致）；
 * - API key 走 [AiKeyStore]（系统钥匙串），**绝不写入 settings.json**；钥匙串不可用
 *   时降级内存并如实提示（T-1 模式）；
 * - 校验照实反馈：endpoint 非 http(s) 显示错误（收敛在 [AppSettings.sanitized]）。
 */
@Composable
internal fun AiSettingsSection(settings: SettingsModel, keys: AiKeyStore) {
    val colors = MaterialTheme.colorScheme
    val s = settings.settings

    SettingsSectionScaffold("智能助手") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = s.aiEndpoint,
                onValueChange = { v -> settings.update { it.copy(aiEndpoint = v) } },
                label = { Text("API endpoint") },
                placeholder = { Text("https://api.example.com/v1") },
                supportingText = {
                    Text("OpenAI 兼容地址，填到 /v1；请求时拼接 /chat/completions", fontSize = 12.sp)
                },
                isError = s.aiEndpoint.isNotBlank() &&
                    !AppSettings.isValidUrl(s.aiEndpoint.trim().trimEnd('/')),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ai-endpoint-input"),
            )
            OutlinedTextField(
                value = s.aiModel,
                onValueChange = { v -> settings.update { it.copy(aiModel = v) } },
                label = { Text("模型名称") },
                placeholder = { Text("服务方提供的模型名，例如 gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ai-model-input"),
            )

            var keyDraft by remember(keys) { mutableStateOf(keys.loadKey() ?: "") }
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("API key") },
                supportingText = {
                    Text(
                        if (keys.keychainAvailable) {
                            "保存到系统钥匙串，不写入设置文件"
                        } else {
                            "系统钥匙串不可用，key 仅保存在内存，退出即丢失"
                        },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ai-key-input"),
            )
            Row {
                TextButton(
                    onClick = {
                        if (keyDraft.isBlank()) keys.deleteKey() else keys.saveKey(keyDraft)
                    },
                    modifier = Modifier.testTag("ai-key-save"),
                ) {
                    Text(if (keyDraft.isBlank()) "删除 Key" else "保存 Key")
                }
            }
        }
    }
}
