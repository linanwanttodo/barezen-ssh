// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/AiPanel.kt（T-7：P6 AI 运维侧栏）
package com.barezen.ssh.ui.shell

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.AiMessage
import com.barezen.ssh.app.AiRole
import com.barezen.ssh.app.AiChatModel
import com.barezen.ssh.app.extractCommands
import com.barezen.ssh.terminal.TerminalBridge
import com.barezen.ssh.ui.screens.EmptyHint
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenMonoSmall

/** 面板尺寸约定（T-7）：展开 320dp，折叠为仅图标的 56dp rail。 */
internal val AI_PANEL_EXPANDED_WIDTH = 320.dp
internal val AI_PANEL_RAIL_WIDTH = 56.dp
internal const val AI_CONTEXT_MAX_LINES = 200

/**
 * AI 运维侧栏（T-7）。右侧可折叠面板：
 * - 对话结构：「对话」标题 + 新建会话；消息流用户右对齐 / AI 左对齐，纯文本流式渲染；
 * - 模型下拉只渲染配置里的真实值（单条目，不造假备选）；
 * - 「附带终端上下文」开关默认关；开启时发送前从 [bridge] 取滚动缓冲区快照（尾部
 *   [AI_CONTEXT_MAX_LINES] 行），消息区如实显示「已附带终端输出 N 行」；
 * - AI 回复中的候选命令（代码块/提示符行）必须经确认对话框（显示确切命令）后才能
 *   写入终端 —— [TerminalBridge.writeCommand] 是唯一写入口，无审批不注入；
 * - 未配置（endpoint/model/key 任一缺失）时输入禁用并给出引导文字；
 * - 配色黑白灰，红色仅用于错误。
 */
@Composable
internal fun AiPanel(
    chat: AiChatModel,
    keys: AiKeyStore,
    bridge: TerminalBridge?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        AiPanelExpanded(chat, keys, bridge, onToggle, modifier)
    } else {
        AiPanelRail(onToggle, modifier)
    }
}

@Composable
private fun AiPanelRail(onToggle: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.width(AI_PANEL_RAIL_WIDTH).fillMaxHeight()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(AI_PANEL_RAIL_WIDTH, 40.dp)
                .testTag("ai-panel-toggle")
                .semantics { contentDescription = "展开智能助手面板" },
        ) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiPanelExpanded(
    chat: AiChatModel,
    keys: AiKeyStore,
    bridge: TerminalBridge?,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    var attachContext by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var pendingInject by remember { mutableStateOf<String?>(null) }
    var injectError by remember { mutableStateOf<String?>(null) }
    // 发送附注（上一条消息的上下文情况，如实展示，含「开了开关但没取到输出」）
    var lastSendNote by remember { mutableStateOf<String?>(null) }

    val config = chat.configProvider()
    val configured = config != null

    Column(modifier.width(AI_PANEL_EXPANDED_WIDTH).fillMaxHeight().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        // 头部：「对话」+ 新建会话 + 折叠
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        ) {
            Text("对话", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { chat.newSession() },
                modifier = Modifier
                    .testTag("ai-new-session")
                    .semantics { contentDescription = "新建会话" },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .testTag("ai-panel-toggle")
                    .semantics { contentDescription = "收起智能助手面板" },
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }

        // 消息流：用户右对齐 / AI 左对齐
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chat.messages.isEmpty()) {
                EmptyHint(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    text = "与 AI 讨论终端里的问题。可开启「附带终端上下文」让助手看到最近输出。",
                )
            }
            chat.messages.forEachIndexed { index, message ->
                AiMessageBubble(message) { command -> pendingInject = command }
            }
            chat.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.testTag("ai-error"))
            }
            lastSendNote?.let { note ->
                Text(note, color = colors.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        // 模型下拉（单条目 = 配置里的模型名，照实渲染，不造假备选）
        Box(Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = config?.model?.takeIf { it.isNotBlank() } ?: "未配置模型",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable(enabled = configured) { modelMenuOpen = true }
                    .testTag("ai-model"),
            )
            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(config?.model ?: "", fontSize = 12.sp) },
                    onClick = { modelMenuOpen = false },
                )
            }
        }

        // 附带终端上下文开关（默认关）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Text("附带终端上下文", fontSize = 12.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = attachContext,
                onCheckedChange = { attachContext = it },
                modifier = Modifier.testTag("ai-context-switch"),
            )
        }

        // 钥匙串降级提示（T-1 模式：降级可见）
        if (!keys.keychainAvailable) {
            Text(
                "系统钥匙串不可用，API key 仅保存在内存",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        // 输入行：输入 + 发送
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        if (configured) "输入问题…" else "在设置-智能助手填写 API 配置后可用",
                        fontSize = 12.sp,
                    )
                },
                enabled = configured && !chat.isStreaming,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                modifier = Modifier.weight(1f).testTag("ai-input"),
            )
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty()) return@IconButton
                    val context = if (attachContext && bridge != null) {
                        bridge.lastLines(AI_CONTEXT_MAX_LINES)
                    } else {
                        emptyList()
                    }
                    lastSendNote = when {
                        !attachContext -> null
                        context.isEmpty() -> "已开启附带，但当前没有可用的终端输出"
                        else -> "已附带终端输出 " + context.size + " 行"
                    }
                    chat.send(text, if (attachContext) context else null)
                    input = ""
                },
                enabled = configured && !chat.isStreaming && input.isNotBlank(),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag("ai-send")
                    .semantics { contentDescription = "发送" },
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
            }
        }
    }

    // 命令注入审批对话框：显示确切命令，确认后经 bridge 写入（无审批不注入）
    pendingInject?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingInject = null },
            title = { Text("注入终端命令", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将在当前终端执行以下命令，请确认其来源可信：", fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            command,
                            style = BareZenMonoBody,
                            modifier = Modifier.padding(8.dp).testTag("ai-inject-command"),
                        )
                    }
                    injectError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ok = bridge?.writeCommand(command) ?: false
                        injectError = if (ok) null else "当前没有可注入的终端会话"
                        if (ok) pendingInject = null
                    },
                    modifier = Modifier.testTag("ai-inject-confirm"),
                ) {
                    Text("注入")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInject = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 单条消息：AI 左对齐（surfaceContainer），用户右对齐（primary 容器中性化后的黑/白灰）。 */
@Composable
private fun AiMessageBubble(message: AiMessage, onInject: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = if (message.role == AiRole.USER) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (message.role == AiRole.USER) colors.surfaceContainerHighest else colors.surfaceContainerLow,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Text(
                text = when {
                    message.text.isNotEmpty() -> message.text
                    message.streaming -> "正在思考…"
                    else -> "（空回复）"
                },
                fontSize = 13.sp,
                textAlign = if (message.role == AiRole.USER) TextAlign.End else TextAlign.Start,
                modifier = Modifier.padding(8.dp),
            )
        }
        if (message.attachedLines > 0) {
            Text(
                "已附带终端输出 " + message.attachedLines + " 行",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }
        // 助手消息完成后：解析注入候选命令（每条独立审批）
        if (message.role == AiRole.ASSISTANT && !message.streaming) {
            extractCommands(message.text).forEach { command ->
                TextButton(
                    onClick = { onInject(command) },
                    modifier = Modifier.testTag("ai-inject-candidate"),
                ) {
                    Text(
                        "注入 " + command,
                        style = BareZenMonoSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
