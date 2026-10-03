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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.barezen.ssh.ui.components.Badge
import com.barezen.ssh.ui.components.BadgeTone
import com.barezen.ssh.ui.components.Banner
import com.barezen.ssh.ui.components.Btn
import com.barezen.ssh.ui.components.BtnKind
import com.barezen.ssh.ui.components.BzCard
import com.barezen.ssh.ui.components.BzSwitch
import com.barezen.ssh.ui.components.Muted
import com.barezen.ssh.ui.components.SectionTitle
import com.barezen.ssh.ui.components.SegmentedControl
import com.barezen.ssh.ui.components.StatusDot
import com.barezen.ssh.ui.theme.BareZenMonoBody
import com.barezen.ssh.ui.theme.BareZenSpace
import com.barezen.ssh.ui.theme.LocalBareZenColors
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
 * 端口转发页接线：只**订阅**活动会话的 [ForwardManager] 并驱动自动启用，不创建也不释放它。
 *
 * 隧道所有权在**会话**（[com.barezen.ssh.app.JvmSessionResources]，设计 §3.4 方案 A）而不是 Host：
 * Host 随标签切换重新组合，若由 Host 持有 manager，用户切走标签就会把正在使用的隧道一起关掉。
 * 释放统一由 `SessionRegistry` 在关闭标签/断开时执行（「关闭即释放、不保活」）。
 *
 * managerFlow 仍是 StateFlow：会话尚未连上时为 null，PortsScreen 据此呈现禁用态。
 */
@Composable
fun PortsHost(model: com.barezen.ssh.app.AppModel) {
    val active = model.registry.active
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 资源没变就不重建 flow：切走再切回拿到的是同一个 JvmSessionResources 实例，
    // 故 liveEntries（flatMapLatest）不会因切换而重新订阅、活动转发列表不丢。
    val resources = active?.resources as? com.barezen.ssh.app.JvmSessionResources
    val managerFlow = remember(resources) {
        MutableStateFlow<ForwardManager?>(resources?.forwardManager)
    }

    // 自动启用只在**会话首次**进入活动态时执行一次（JvmSessionResources 自己记账）：
    // 该会话已被用户手动关掉的转发，不得因切回标签而被重新打开。
    androidx.compose.runtime.LaunchedEffect(resources) {
        resources?.ensureAutoStartApplied { FileForwardRuleStore().list().filter { it.autoStart } }
    }
    PortsScreen(model = PortsModel(scope = scope, managerFlow = managerFlow))
}
/**
 * 端口转发屏（apple.html `#forwards`）：屏头（副标题 + 活动计数）| 信息横幅 |
 * 新建表单卡（类型 tab + 三列字段 + 底部服务器/保存）| 已保存规则卡 | 活动转发卡。
 *
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
        // 屏头（apple.css `.screen-header`）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = BareZenSpace.xxxl,
                    end = BareZenSpace.xxl,
                    top = BareZenSpace.xl,
                    bottom = BareZenSpace.lg,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "通过 SSH 隧道转发本地或远程端口",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "活动转发 ${liveEntries.count { it.state == ForwardEntryState.ACTIVE }} 条",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BareZenSpace.xxxl)
                .padding(bottom = BareZenSpace.xxxl),
            verticalArrangement = Arrangement.spacedBy(BareZenSpace.lg),
        ) {
            Banner(
                text = if (connected) {
                    "已连接。新增转发会立即启用，规则保存在本机。"
                } else {
                    "未连接。连接后可启用转发，转发规则将保存在本机。"
                },
                tone = BadgeTone.accent,
                icon = Icons.Outlined.Info,
                modifier = Modifier.fillMaxWidth(),
            )

            // 新建转发表单（apple.css `.pf-form`）
            BzCard(modifier = Modifier.fillMaxWidth()) {
                // 类型 tab（apple.css `.pf-tabs`）：动态 SOCKS5 底层不支持，禁用并标注
                SegmentedControl(
                    options = listOf("本地监听", "服务器监听", "SOCKS5"),
                    selectedIndex = when (kind) {
                        ForwardKind.LOCAL -> 0
                        ForwardKind.REMOTE -> 1
                    },
                    disabledIndices = setOf(2),
                    onSelect = { i ->
                        kind = when (i) {
                            1 -> ForwardKind.REMOTE
                            else -> ForwardKind.LOCAL
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Muted("本地监听：本机监听；服务器监听：远端监听。动态（SOCKS5）即将支持", size = 11)
                Spacer(Modifier.height(BareZenSpace.lg))

                // 三列字段（apple.css `.pf-fields`）
                Row(horizontalArrangement = Arrangement.spacedBy(BareZenSpace.md)) {
                    PortField(
                        "本地端口",
                        bindPortText,
                        { bindPortText = it },
                        connected,
                        "ports-bind-port",
                        Modifier.weight(1f),
                    )
                    PortField(
                        "远程主机",
                        targetHost,
                        { targetHost = it },
                        connected,
                        "ports-target-host",
                        Modifier.weight(1f),
                    )
                    PortField(
                        "远程端口",
                        targetPortText,
                        { targetPortText = it },
                        connected,
                        "ports-target-port",
                        Modifier.weight(1f),
                    )
                }

                if (!connected) {
                    Spacer(Modifier.height(BareZenSpace.md))
                    Muted("连接后可启用转发", size = 12)
                }
                if (formError != null) {
                    Spacer(Modifier.height(BareZenSpace.md))
                    Text(
                        formError!!,
                        fontSize = 12.sp,
                        color = LocalBareZenColors.current.onErrorContainer,
                        modifier = Modifier.testTag("ports-form-error"),
                    )
                }

                // 底部（apple.css `.pf-form-footer`）
                Spacer(Modifier.height(BareZenSpace.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Muted(if (connected) "选择服务器：当前活动会话" else "选择服务器：未连接", size = 12)
                    Spacer(Modifier.weight(1f))
                    BzSwitch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                        enabled = connected,
                    )
                    Spacer(Modifier.width(8.dp))
                    Muted("连接时自动启用", size = 12)
                    Spacer(Modifier.width(BareZenSpace.lg))
                    Btn(
                        "保存规则",
                        { model.submit(kind, bindPortText, targetHost, targetPortText) },
                        kind = BtnKind.primary,
                        small = true,
                        enabled = connected,
                    )
                }
            }

            // 已保存规则
            BzCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("已保存的规则")
                Spacer(Modifier.height(BareZenSpace.md))
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

            // 活动转发
            BzCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("活动转发")
                Spacer(Modifier.height(BareZenSpace.md))
                val active = liveEntries.filter { it.state == ForwardEntryState.ACTIVE }
                if (active.isEmpty()) {
                    EmptyHint(Icons.Outlined.SwapHoriz, "当前没有正在运行的转发。")
                } else {
                    active.forEach { entry ->
                        LiveForwardRow(
                            entry = entry,
                            onStop = { model.managerFlow.value?.remove(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** 表单字段（apple.css `.pf-field`）：标签在上、输入框在下。 */
@Composable
private fun PortField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(BareZenSpace.sm))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(testTag),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = LocalBareZenColors.current.accentOnSubtle,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** 已保存规则行（apple.css `.pf-rule`）：类型徽标 + 等宽描述 + 自动启用开关 + 删除。 */
@Composable
private fun ForwardRuleRow(
    rule: ForwardRule,
    live: ForwardEntry?,
    onDelete: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val failed = live?.state == ForwardEntryState.FAILED
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("ports-rule-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(
            when (rule.kind) {
                ForwardKind.LOCAL -> "本地"
                ForwardKind.REMOTE -> "服务器"
            },
            tone = BadgeTone.neutral,
        )
        Spacer(Modifier.width(BareZenSpace.md))
        Text(
            text = when (rule.kind) {
                ForwardKind.LOCAL -> "localhost:${rule.bindPort} -> ${rule.targetHost}:${rule.targetPort}"
                ForwardKind.REMOTE -> "0.0.0.0:${rule.bindPort} -> ${rule.targetHost}:${rule.targetPort}"
            },
            modifier = Modifier.weight(1f),
            style = BareZenMonoBody,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(BareZenSpace.md))
        // 运行态徽标：颜色之外必有文字（色彩不作唯一指示）
        when {
            failed -> Badge("失败", tone = BadgeTone.error)
            live != null && live.state == ForwardEntryState.ACTIVE -> Badge("已启用", tone = BadgeTone.success)
            else -> Badge("未启用", tone = BadgeTone.neutral)
        }
        if (failed) {
            Spacer(Modifier.width(BareZenSpace.sm))
            Muted("失败：${live.message ?: "原因不明"}", size = 11)
        }
        Spacer(Modifier.width(BareZenSpace.sm))
        BzSwitch(checked = rule.autoStart, onCheckedChange = onAutoStartChange)
        Spacer(Modifier.width(BareZenSpace.sm))
        Btn("删除", onDelete, kind = BtnKind.ghost, small = true)
    }
}

/** 活动转发行（apple.css `.pf-rule.active`）：accent 描边 + 已运行时长 + 停止。 */
@Composable
private fun LiveForwardRow(entry: ForwardEntry, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(
            when (entry.spec.kind) {
                ForwardKind.LOCAL -> "本地"
                ForwardKind.REMOTE -> "服务器"
            },
            tone = BadgeTone.accent,
        )
        Spacer(Modifier.width(BareZenSpace.md))
        Text(
            text = when (entry.spec.kind) {
                ForwardKind.LOCAL ->
                    "localhost:${entry.spec.bindPort} -> ${entry.spec.targetHost}:${entry.spec.targetPort}"
                ForwardKind.REMOTE ->
                    "0.0.0.0:${entry.spec.bindPort} -> ${entry.spec.targetHost}:${entry.spec.targetPort}"
            },
            modifier = Modifier.weight(1f),
            style = BareZenMonoBody,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusDot(on = true)
        Spacer(Modifier.width(BareZenSpace.sm))
        Btn("停止", onStop, kind = BtnKind.danger, small = true)
    }
}
