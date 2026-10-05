// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.kernel.OUTBOUND_CONFIG_BLOCK
import com.slte.app.kernel.OUTBOUND_CONFIG_DIRECT
import com.slte.app.kernel.OUTBOUND_CONFIG_PROXY
import com.slte.app.ui.screen.settings.RoutingRuleItem
import com.slte.app.ui.screen.settings.RoutingRulesViewModel
import com.slte.app.ui.screen.settings.RoutingSync
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5Banner
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Divider
import com.slte.app.ui.v5.V5PageBody
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.v5Enter
import kotlin.math.roundToInt

/**
 * v6 分流规则管理页（iOS 简约风；Karing 式每条规则组独立开关 + 自定义规则组）。
 * 状态与写盘逻辑与 v4 版共用 RoutingRulesViewModel。
 */
@Composable
internal fun V5RoutingRulesScreen(
    onBack: () -> Unit,
    viewModel: RoutingRulesViewModel = hiltViewModel(),
) {
    val c = V5ThemeColors.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    val customGroupState by viewModel.customGroupState.collectAsStateWithLifecycle()

    V5PageScaffold(tab = null) {
        V5TopBar(stringResource(R.string.settings_routing_rules), onBack = onBack)
        V5PageBody {
            Text(
                text = stringResource(R.string.routing_rules_desc),
                fontSize = V5Type.sp12,
                color = c.text3,
                modifier = Modifier.padding(horizontal = V5Spacing.dp4).v5Enter(0),
            )

            // —— 内置分流组（上方说明文案即本区小标题，不重复加 SectionTitle）
            V5CardFlat(Modifier.v5Enter(1)) {
                ReorderableRoutingGroups(
                    items = data.items,
                    enabled = data.sync == RoutingSync.Idle,
                    onToggle = viewModel::setEnabled,
                    onMove = viewModel::moveGroup,
                )
            }

            data.errorMessageRes?.let { res ->
                V5Banner(ChipTone.DANGER, stringResource(res))
            }

            // —— 自定义规则组（首行本身就是带高亮的"添加"入口，即本区小标题）
            V5CardFlat(Modifier.v5Enter(2)) {
                V5RowItem(
                    title = stringResource(R.string.routing_custom_add),
                    sub = stringResource(R.string.routing_custom_add_desc),
                    icon = SlteIcons.Add,
                    highlight = true,
                    chevron = true,
                    onClick = viewModel::showAddCustomGroup,
                )
                data.custom.forEach { custom ->
                    V5Divider()
                    V5RowItem(
                        title = custom.name,
                        sub = custom.url,
                        icon = SlteIcons.Delete,
                        danger = true,
                        chevron = true,
                        onClick = { viewModel.removeCustomGroup(custom.name) },
                    )
                }
            }

            // —— 恢复默认
            V5CardFlat(Modifier.v5Enter(3)) {
                V5RowItem(
                    title = stringResource(R.string.routing_rules_reset),
                    sub = stringResource(R.string.routing_rules_reset_desc),
                    icon = SlteIcons.UpdateSubscription,
                    chevron = true,
                    onClick = viewModel::resetDefaults,
                )
            }
        }
    }

    val editing = customGroupState as? com.slte.app.ui.screen.settings.CustomGroupState.Editing
    if (editing != null) {
        com.slte.app.ui.screen.settings.CustomRuleGroupSheet(
            state = editing,
            onNameChange = viewModel::onCustomNameChange,
            onUrlChange = viewModel::onCustomUrlChange,
            onBehaviorChange = viewModel::onCustomBehaviorChange,
            onSubmit = viewModel::submitCustomGroup,
            onDismiss = viewModel::dismissCustomGroup,
        )
    }
}

/**
 * 可拖动排序的内置分流组列表。
 *
 * 拖动只发生在右侧手柄（独立指针节点）上，避免与行内点击手势冲突；拖动过程中
 * 按实测行距换算目标下标并实时让位，松手后回调 [onMove] 落盘（顺序决定规则匹配优先级）。
 */
@Composable
private fun ReorderableRoutingGroups(
    items: List<RoutingRuleItem>,
    enabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val c = V5ThemeColors.current
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pitchPx by remember { mutableFloatStateOf(0f) }

    fun targetFor(index: Int): Int = if (index < 0 || pitchPx <= 0f) {
        index
    } else {
        (index + (dragOffset / pitchPx).roundToInt()).coerceIn(0, items.lastIndex)
    }

    val targetIndex = targetFor(draggingIndex)

    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            val shift =
                when {
                    draggingIndex < 0 -> 0f
                    isDragging -> dragOffset
                    index > draggingIndex && index <= targetIndex -> -pitchPx
                    index < draggingIndex && index >= targetIndex -> pitchPx
                    else -> 0f
                }
            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        // 该项含分割线，故任意非首项的高度即整行的「步距」
                        if (index > 0) pitchPx = coordinates.size.height.toFloat()
                    }
                    .graphicsLayer {
                        translationY = shift
                        if (isDragging) shadowElevation = 8.dp.toPx()
                    }
                    .zIndex(if (isDragging) 1f else 0f),
            ) {
                if (index > 0) V5Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    V5RowItem(
                        title = item.name,
                        sub = stringResource(outboundLabelOf(item.defaultOut)),
                        icon = SlteIcons.Route,
                        value = if (item.enabled) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off),
                        switchState = item.enabled,
                        trailing = {
                            V5Switch(checked = item.enabled)
                        },
                        onClick =
                        if (enabled) {
                            { onToggle(item.name, !item.enabled) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp)
                            .pointerInput(item.name) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex
                                        val to = targetFor(from)
                                        draggingIndex = -1
                                        dragOffset = 0f
                                        if (from >= 0 && to != from) onMove(from, to)
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SlteIcons.DragHandle,
                            contentDescription = stringResource(R.string.routing_rules_drag_handle),
                            tint = c.text3,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun outboundLabelOf(defaultOut: String): Int = when (defaultOut) {
    OUTBOUND_CONFIG_DIRECT -> R.string.routing_outbound_direct
    OUTBOUND_CONFIG_BLOCK -> R.string.routing_outbound_block
    OUTBOUND_CONFIG_PROXY -> R.string.routing_outbound_proxy
    else -> R.string.routing_outbound_proxy
}
