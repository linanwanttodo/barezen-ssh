// shared/src/jvmMain/kotlin/com/barezen/ssh/ui/shell/CommandPaletteUi.kt
package com.barezen.ssh.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.barezen.ssh.app.PaletteItem
import com.barezen.ssh.app.filterPalette
import com.barezen.ssh.ui.components.Badge
import com.barezen.ssh.ui.components.BadgeTone
import com.barezen.ssh.ui.theme.LocalBareZenColors

/** 命令面板的最大高度：超过就滚动，不撑破窗口。 */
private val PaletteMaxHeight = 420.dp

/**
 * 命令面板浮层：居中弹窗，输入框 + 结果列表。
 *
 * 交互：↑/↓ 移动选中、Enter 执行、Esc 关闭。选中项随滚动保持可见。
 * 结果为空时显示「无匹配命令」——不静默，也不给假结果。
 */
@Composable
internal fun CommandPaletteDialog(
    items: List<PaletteItem>,
    onPick: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, items) { filterPalette(items, query) }
    var selected by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // 查询变化后选中项复位（否则可能停在不存在的下标上）
    LaunchedEffect(query) { selected = 0 }
    // 选中项滚入视野
    LaunchedEffect(selected, results.size) {
        if (selected in results.indices) listState.scrollToItem(selected)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .width(560.dp)
                .heightIn(max = PaletteMaxHeight)
                .clip(shape)
                .background(colors.surfaceContainerHighest)
                .border(1.dp, colors.outline, shape)
                .testTag("command-palette"),
        ) {
            // 搜索框
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(LocalBareZenColors.current.accentOnSubtle))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.onSurface,
                    ),
                    cursorBrush = SolidColor(LocalBareZenColors.current.accentOnSubtle),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { results.getOrNull(selected)?.let(onPick) },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { ev ->
                            when (ev.key) {
                                androidx.compose.ui.input.key.Key.DirectionUp -> {
                                    if (results.isNotEmpty()) {
                                        selected = (selected - 1).coerceAtLeast(0)
                                    }
                                    true
                                }
                                androidx.compose.ui.input.key.Key.DirectionDown -> {
                                    if (results.isNotEmpty()) {
                                        selected = (selected + 1).coerceAtMost(results.lastIndex)
                                    }
                                    true
                                }
                                androidx.compose.ui.input.key.Key.Enter -> {
                                    results.getOrNull(selected)?.let(onPick)
                                    true
                                }
                                else -> false
                            }
                        }
                        .testTag("command-palette-input")
                        .semantics { contentDescription = "命令搜索" },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索命令、服务器…",
                                fontSize = 15.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }

            if (results.isEmpty()) {
                Text(
                    "无匹配命令",
                    Modifier.padding(24.dp),
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = PaletteMaxHeight),
                ) {
                    itemsIndexed(results) { index, item ->
                        PaletteRow(
                            item = item,
                            selected = index == selected,
                            onClick = { onPick(item) },
                        )
                    }
                }
            }
        }
    }
}

/** 单行命令：标题 + 副标题 + 类型徽标。选中项用 panel 底 + 左侧 accent 竖条。 */
@Composable
private fun PaletteRow(item: PaletteItem, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extras = LocalBareZenColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (selected) colors.surfaceContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("palette-row-" + item.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.width(3.dp).height(44.dp).background(extras.accentOnSubtle))
        } else {
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(
                    item.subtitle,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Badge(
            text = when (item.kind) {
                PaletteItem.Kind.NAVIGATE -> "界面"
                PaletteItem.Kind.CONNECT -> "连接"
                PaletteItem.Kind.EDIT_SERVER -> "编辑"
                PaletteItem.Kind.DELETE_SERVER -> "删除"
            },
            tone = when (item.kind) {
                PaletteItem.Kind.DELETE_SERVER -> BadgeTone.error
                PaletteItem.Kind.CONNECT -> BadgeTone.accent
                else -> BadgeTone.neutral
            },
        )
        Spacer(Modifier.width(16.dp))
    }
}
