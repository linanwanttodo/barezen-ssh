// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/screens/PortsScreen.kt
package com.barezen.ssh.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barezen.ssh.servers.ForwardRule
import com.barezen.ssh.servers.ForwardRuleStore
import com.barezen.ssh.servers.FileForwardRuleStore
import com.barezen.ssh.ssh.ForwardKind
import com.barezen.ssh.ssh.ForwardSpec
import com.barezen.ssh.ssh.forward.ForwardEntry
import com.barezen.ssh.ssh.forward.ForwardEntryState
import com.barezen.ssh.ssh.forward.ForwardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 端口转发屏模型：持久化规则（[ForwardRuleStore]）+ 当前连接的转发运行态（[ForwardManager]）。
 * 未连接时 [managerFlow] 为 null——表单禁用，仅可编辑已保存规则。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PortsModel(
    private val scope: CoroutineScope,
    private val ruleStore: ForwardRuleStore = FileForwardRuleStore(),
    val managerFlow: StateFlow<ForwardManager?> = MutableStateFlow(null),
) {
    val rules = MutableStateFlow(ruleStore.list())
    val formError = MutableStateFlow<String?>(null)

    /** 当前连接的转发条目；未连接时恒为空列表。 */
    val liveEntries: StateFlow<List<ForwardEntry>> = managerFlow
        .flatMapLatest { manager -> manager?.entries ?: flowOf(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 校验并保存表单；通过且已连接时立即启用转发。非法输入落成 [formError]，不落盘。 */
    fun submit(kind: ForwardKind, bindPortText: String, targetHost: String, targetPortText: String) {
        val error = validateForm(bindPortText, targetHost, targetPortText)
        if (error != null) {
            formError.value = error
            return
        }
        formError.value = null
        val spec = ForwardSpec(
            kind = kind,
            bindPort = bindPortText.trim().toInt(),
            targetHost = targetHost.trim(),
            targetPort = targetPortText.trim().toInt(),
        )
        ruleStore.upsert(ForwardRule.of(spec))
        rules.value = ruleStore.list()
        managerFlow.value?.let { manager ->
            scope.launch { manager.apply(spec) }
        }
    }

    /** 删除规则；若该转发正在活动，先关隧道再删。 */
    fun delete(rule: ForwardRule) {
        managerFlow.value?.let { manager ->
            manager.entries.value.firstOrNull { it.spec == rule.toSpec() }?.let(manager::remove)
        }
        ruleStore.delete(rule)
        rules.value = ruleStore.list()
    }

    fun setAutoStart(rule: ForwardRule, enabled: Boolean) {
        ruleStore.upsert(rule.copy(autoStart = enabled))
        rules.value = ruleStore.list()
    }

    companion object {
        /** 返回 null 表示通过；否则为面向用户的中文错误。 */
        fun validateForm(bindPortText: String, targetHost: String, targetPortText: String): String? {
            val bindPort = bindPortText.trim().toIntOrNull()
            if (bindPort == null || bindPort !in 1..65535) return "监听端口需为 1-65535 的数字"
            if (targetHost.isBlank()) return "目标主机不能为空"
            val targetPort = targetPortText.trim().toIntOrNull()
            if (targetPort == null || targetPort !in 1..65535) return "目标端口需为 1-65535 的数字"
            return null
        }
    }
}

/**
 * 端口转发屏：规则列表（状态徽标 + 删除 + 自动启用开关）+ 新建转发表单。
 * 未连接（[PortsModel.managerFlow] 为 null）时表单整体禁用并提示「连接后可启用转发」。
 */
@Composable
fun PortsScreen(model: PortsModel = PortsModel(scope = rememberCoroutineScope())) {
    val rules by model.rules.collectAsState()
    val formError by model.formError.collectAsState()
    val liveEntries by model.liveEntries.collectAsState()
    val manager by model.managerFlow.collectAsState()
    val connected = manager != null

    var kind by remember { mutableStateOf(ForwardKind.LOCAL) }
    var bindPortText by remember { mutableStateOf("") }
    var targetHost by remember { mutableStateOf("") }
    var targetPortText by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        // 屏头（通用式样：56dp、horizontal 24）
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("端口转发", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "活动转发：${liveEntries.count { it.state == ForwardEntryState.ACTIVE }} 条",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 信息横幅
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(6.dp),
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
                        if (connected) {
                            "已连接。新增转发会立即启用，规则保存在本机。"
                        } else {
                            "未连接。连接后可启用转发，转发规则将保存在本机。"
                        },
                        fontSize = 13.sp,
                    )
                }
            }

            // 新建转发表单
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "新建转发",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 类型选择：动态（SOCKS5）sshj 0.40 不支持，禁用并如实标注
                    ChoiceRow(
                        title = "类型",
                        desc = "本地：本机监听；远程：服务器监听。动态（SOCKS5）即将支持",
                        options = listOf("本地", "远程", "动态"),
                        selectedIndex = when (kind) {
                            ForwardKind.LOCAL -> 0
                            ForwardKind.REMOTE -> 1
                        },
                        disabledIndices = setOf(2),
                        onSelect = { index ->
                            kind = when (index) {
                                1 -> ForwardKind.REMOTE
                                else -> ForwardKind.LOCAL
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = bindPortText,
                            onValueChange = { bindPortText = it },
                            label = { Text("监听端口") },
                            enabled = connected,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("ports-bind-port"),
                        )
                        OutlinedTextField(
                            value = targetPortText,
                            onValueChange = { targetPortText = it },
                            label = { Text("目标端口") },
                            enabled = connected,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("ports-target-port"),
                        )
                    }
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("目标主机") },
                        enabled = connected,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ports-target-host"),
                    )
                    if (!connected) {
                        Text(
                            "连接后可启用转发",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val currentError = formError
                    if (currentError != null) {
                        Text(
                            currentError,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("ports-form-error"),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                model.submit(kind, bindPortText, targetHost, targetPortText)
                            },
                            enabled = connected,
                        ) {
                            Text("添加")
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = autoStart,
                            onCheckedChange = { autoStart = it },
                            enabled = connected,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "连接时自动启用",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 已保存规则列表
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "转发规则",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rules.isEmpty()) {
                        EmptyHint(Icons.Outlined.SwapHoriz, "暂无转发规则。添加后规则会保存在本机，下次连接可直接启用。")
                    } else {
                        rules.forEach { rule ->
                            ForwardRuleRow(
                                rule = rule,
                                live = liveEntries.firstOrNull { it.spec == rule.toSpec() },
                                onDelete = { model.delete(rule) },
                                onAutoStartChange = { model.setAutoStart(rule, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardRuleRow(
    rule: ForwardRule,
    live: ForwardEntry?,
    onDelete: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = when (rule.kind) {
                    ForwardKind.LOCAL -> "本地 ${rule.bindPort} -> ${rule.targetHost}:${rule.targetPort}"
                    ForwardKind.REMOTE -> "远程 ${rule.bindPort} -> ${rule.targetHost}:${rule.targetPort}"
                },
                fontSize = 14.sp,
            )
            Text(
                text = when {
                    live?.state == ForwardEntryState.FAILED ->
                        "失败：${live.message ?: "原因不明"}"
                    else ->
                        if (rule.autoStart) "连接时自动启用" else "不自动启用"
                },
                fontSize = 12.sp,
                color = if (live?.state == ForwardEntryState.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusBadge(live = live)
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = rule.autoStart,
            onCheckedChange = onAutoStartChange,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onDelete) { Text("删除", fontSize = 12.sp) }
    }
}

@Composable
private fun StatusBadge(live: ForwardEntry?) {
    val (label, container, content) = when {
        live == null -> Triple("未启用", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
        live.state == ForwardEntryState.ACTIVE -> Triple(
            "已启用",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> Triple("失败", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
