// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/AiPanel.kt（T-7：P6 AI 运维侧栏，2026-10-03 移入终端屏）
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.app.AiChatModel
import com.barezen.ssh.app.AiKeyStore
import com.barezen.ssh.app.AiMessage
import com.barezen.ssh.app.AiRole
import com.barezen.ssh.app.extractCommands
import com.barezen.ssh.terminal.LocalTerminalBridge
import com.barezen.ssh.terminal.TerminalBridge
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzSwitch
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenMonoSmall
import com.barezen.ssh.ui.theme.BareZenSize
import com.barezen.ssh.ui.theme.LocalBareZenColors

/** 面板尺寸（apple.css `.ai-sidebar`）：宽 320dp，与终端同底色（不另起一层）。 */
internal val AI_PANEL_EXPANDED_WIDTH = 320.dp
internal const val AI_CONTEXT_MAX_LINES = 200

/**
 * AI 运维侧栏（apple.css `.ai-sidebar`）：三段式——header（运维助手 + 收起）| body（消息流或空态）
 * | composer（上下文/模式选择、输入框、权限档、模型名）。
 *
 * - 对话：用户右对齐 / AI 左对齐，消息流式渲染；
 * - 模型下拉只渲染配置里的真实值（单条目，不造假备选）；
 * - 「附带终端上下文」默认关；开启时发送前从 [bridge] 取滚动缓冲区尾部 [AI_CONTEXT_MAX_LINES] 行；
 * - AI 回复中的候选命令必须经审批对话框确认后才写入终端 —— [TerminalBridge.writeCommand]
 *   是唯一写入口，无审批不注入；
 * - 未配置（endpoint/model/key 任一缺失）时输入禁用并给出引导文字。
 */
@Composable
internal fun AiSidebar(
    chat: AiChatModel,
    keys: AiKeyStore,
    sessionName: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    // 终端桥在**组合期**取一次：onClick 是非组合闭包，不能在里面调 @Composable getter。
    val bridge = LocalTerminalBridge.current
    var input by remember { mutableStateOf("") }
    var attachContext by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var pendingInject by remember { mutableStateOf<String?>(null) }
    var injectError by remember { mutableStateOf<String?>(null) }
    var lastSendNote by remember { mutableStateOf<String?>(null) }

    val config = chat.configProvider()
    val configured = config != null

    Row(
        modifier
            .width(AI_PANEL_EXPANDED_WIDTH)
            .fillMaxHeight()
            .background(extras.terminalBg),
    ) {
        // 与终端区之间的 1px 分隔（apple.css 靠 terminal-bg 同色，改用细线区分两块内容）
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outlineVariant))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // header
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BareZenSize.tabHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(extras.accentOnSubtle))
                Spacer(Modifier.width(8.dp))
                Text(
                    "运维助手",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Btn(
                    "收起",
                    onClose,
                    kind = com.barezen.ssh.ui.components.BtnKind.ghost,
                    small = true,
                    icon = Icons.Outlined.KeyboardArrowDown,
                )
            }
            HorizontalLine()

            // body
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (chat.messages.isEmpty()) {
                    AiEmptyState()
                }
                chat.messages.forEach { message ->
                    AiMessageBubble(message) { command -> pendingInject = command }
                }
                chat.error?.let { err ->
                    Text(
                        err,
                        color = colors.error,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("ai-error"),
                    )
                }
                lastSendNote?.let { note ->
                    Text(note, color = colors.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            // composer
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 上下文与模式
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiSelectButton(
                        text = sessionName ?: "未连接会话",
                        icon = Icons.Outlined.Tune,
                        onClick = { modelMenuOpen = true },
                        testTag = "ai-context-button",
                    )
                    Spacer(Modifier.weight(1f))
                    AiSelectButton(
                        text = "运维模式",
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { modelMenuOpen = true },
                    )
                }

                // 输入框
                val inputShape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(inputShape)
                        .background(colors.surfaceContainer)
                        .border(1.dp, colors.outline, inputShape)
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(
                                if (configured) "输入运维指令或问题…" else "在设置-智能助手填写 API 配置后可用",
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                            )
                        },
                        enabled = configured && !chat.isStreaming,
                        singleLine = true,
                        colors = transparentFieldColors(),
                        modifier = Modifier.weight(1f).testTag("ai-input"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (configured && !chat.isStreaming && input.isNotBlank()) extras.accentOnSubtle else colors.outline)
                            .clickable(
                                enabled = configured && !chat.isStreaming && input.isNotBlank(),
                            ) {
                                val text = input.trim()
                                if (text.isEmpty()) return@clickable
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
                            }
                            .testTag("ai-send")
                            .semantics { contentDescription = "发送" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (configured && input.isNotBlank()) colors.onPrimary else colors.onSurfaceVariant,
                        )
                    }
                }

                // 权限档 + 模型名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceContainerHighest)
                            .border(1.dp, colors.outline, RoundedCornerShape(6.dp))
                            .padding(2.dp),
                    ) {
                        PermChip("自动", selected = !attachContext, onClick = { attachContext = false })
                        PermChip(
                            "Yolo",
                            selected = attachContext,
                            onClick = { attachContext = true },
                            testTag = "ai-context-switch",
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        config?.model?.takeIf { it.isNotBlank() } ?: "未配置模型",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = extras.accentOnSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }

                // 钥匙串降级提示（T-1 模式：降级可见）
                if (!keys.keychainAvailable) {
                    Text(
                        "系统钥匙串不可用，API key 仅保存在内存",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 模型下拉（单条目 = 配置里的模型名，照实渲染）
    DropdownMenu(
        expanded = modelMenuOpen,
        onDismissRequest = { modelMenuOpen = false },
    ) {
        DropdownMenuItem(
            text = { Text(config?.model ?: "未配置模型", fontSize = 12.sp) },
            onClick = { modelMenuOpen = false },
        )
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
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            command,
                            style = BareZenMonoBody,
                            modifier = Modifier.padding(8.dp).testTag("ai-inject-command"),
                        )
                    }
                    injectError?.let {
                        Text(it, color = extras.onErrorContainer, fontSize = 12.sp)
                    }
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
                ) { Text("注入") }
            },
            dismissButton = { TextButton(onClick = { pendingInject = null }) { Text("取消") } },
        )
    }
}

/** 空态（apple.css `.ai-placeholder`）：40dp 方块 + 标题 + 一行说明。 */
@Composable
private fun AiEmptyState() {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "AI",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = extras.accentOnSubtle,
            )
        }
        Text(
            "选择模型后开始对话",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Text(
            "批准的命令将注入当前终端",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 单条消息：AI 左对齐、用户右对齐；AI 完成后解析出可注入命令，逐条审批。 */
@Composable
private fun AiMessageBubble(message: AiMessage, onInject: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = if (message.role == AiRole.USER) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (message.role == AiRole.USER) colors.surfaceContainerHighest
            else colors.surfaceContainer,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Text(
                text = when {
                    message.text.isNotEmpty() -> message.text
                    message.streaming -> "正在思考…"
                    else -> "（空回复）"
                },
                fontSize = 13.sp,
                color = colors.onSurface,
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
        if (message.role == AiRole.ASSISTANT && !message.streaming) {
            extractCommands(message.text).forEach { command ->
                TextButton(
                    onClick = { onInject(command) },
                    modifier = Modifier.testTag("ai-inject-candidate"),
                ) {
                    Text("注入 " + command, style = BareZenMonoSmall, maxLines = 1)
                }
            }
        }
    }
}

/** 上下文/模式选择按钮（apple.css `.ai-context-btn`）。 */
@Composable
private fun AiSelectButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    val shape = RoundedCornerShape(6.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .height(28.dp)
            .clip(shape)
            .background(if (hovered) colors.surfaceContainerHighest else colors.surfaceContainer)
            .border(1.dp, colors.outline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = extras.accentOnSubtle)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            null,
            modifier = Modifier.size(12.dp),
            tint = colors.onSurfaceVariant,
        )
    }
}

/** 权限档（apple.css `.perm-chip`）：选中反色。[testTag] 用于测试定位。 */
@Composable
private fun PermChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) colors.onSurface else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) colors.surface else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun HorizontalLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 输入框透明化：外层已画好描边与底色，内层不再重复一套框。 */
@Composable
private fun transparentFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
