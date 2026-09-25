// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.RoutingReservedNames
import com.slte.app.ui.screen.server.NodeItem
import com.slte.app.ui.screen.server.ServerData
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.GradientIcon
import com.slte.app.ui.v5.IconTone
import com.slte.app.ui.v5.LatencyText
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.RadioDot
import com.slte.app.ui.v5.TileTone
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.V5TopIconButton
import com.slte.app.ui.v5.tile
import com.slte.app.utils.Constants

/* ============================================================
   v5 节点页
   1) 快捷出口：自动选择 / 故障转移
   2) 全部节点：手动点选（作用于「🚀 节点选择」主组）
   3) 分流规则组：每条规则组独立指定出口（跟随/自动/故障转移/直连/拦截/具体节点）
   ============================================================ */

/** 主选择组名（保留组名集合首项 = 🚀 节点选择）。 */
private val primaryGroupName: String = RoutingReservedNames.first()

/** 延迟展示：null=未测速、超时=危险色，其余按阈值分色。 */
@Composable
private fun latencyLabel(delay: Int?): Pair<String, ChipTone> = when {
    delay == null -> "--" to ChipTone.NEUTRAL
    delay >= Constants.DELAY_TIMEOUT -> stringResource(R.string.server_timeout) to ChipTone.DANGER
    delay < 120 -> "$delay ms" to ChipTone.OK
    delay < 300 -> "$delay ms" to ChipTone.WARN
    else -> "$delay ms" to ChipTone.DANGER
}

/** 主选择组当前挂载的节点名；未连接/未就绪时为 null。 */
private fun primaryNode(groups: List<KernelProxyGroupInfo>): String? = groups.firstOrNull { it.name == primaryGroupName }?.now

/** 分流规则组 = 除结构组（主选择/自动/故障转移/漏网之鱼）外的全部策略组。 */
private fun routingGroupsOf(groups: List<KernelProxyGroupInfo>): List<KernelProxyGroupInfo> = groups.filter { it.name !in RoutingReservedNames }

@Composable
private fun groupExitLabel(group: KernelProxyGroupInfo): String {
    val now = group.now ?: return stringResource(R.string.v5_group_unset)
    return when (now) {
        primaryGroupName -> stringResource(R.string.v5_group_follow_primary)
        "DIRECT" -> stringResource(R.string.routing_outbound_direct)
        "REJECT" -> stringResource(R.string.routing_outbound_block)
        else -> now
    }
}

@Composable
internal fun V5NodesScreen(
    data: ServerData,
    groups: List<KernelProxyGroupInfo>,
    isLoadingGroups: Boolean,
    testingGroup: String?,
    isTestingAll: Boolean,
    onQuickSelect: (Int) -> Unit,
    onSelectNode: (Int) -> Unit,
    onSelectInGroup: (String, String) -> Unit,
    onTestGroup: (String) -> Unit,
    onStartSpeedTest: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onRoutingRules: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
) {
    val c = V5ThemeColors.current
    val current = primaryNode(groups)
    val groupsForRouting = routingGroupsOf(groups)
    var exitSheetGroup by remember { mutableStateOf<KernelProxyGroupInfo?>(null) }

    V5PageScaffold(tab = NavTab.NODES, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_nodes)) {
            V5TopIconButton(Icons.Outlined.Sync, onRefreshSubscription)
            V5TopIconButton(Icons.Outlined.Speed, onStartSpeedTest)
        }
        V5ScrollBody(NavTab.NODES) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.v5_nodes_summary, data.nodes.size, groupsForRouting.size),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.text3,
                )
                Spacer(Modifier.weight(1f))
                if (isTestingAll) {
                    V5Chip(ChipTone.ACCENT, stringResource(R.string.v5_speed_testing), dot = true)
                }
            }

            // —— 快捷出口
            V5Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientIcon(IconTone.GREEN, Icons.Outlined.AutoAwesome)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.v5_quick_pick), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(stringResource(R.string.v5_quick_pick_desc), fontSize = 11.5.sp, color = c.text3)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickTile(
                            TileTone.GREEN,
                            stringResource(R.string.v5_auto_pick),
                            data.autoNode ?: "--",
                            Modifier.weight(1f),
                        ) { onQuickSelect(0) }
                        QuickTile(
                            TileTone.PURPLE,
                            stringResource(R.string.v5_fallback_pick),
                            data.fallbackNode ?: "--",
                            Modifier.weight(1f),
                        ) { onQuickSelect(-1) }
                    }
                }
            }

            // —— 全部节点：手动点选（作用于主选择组）
            V5CardFlat(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.v5_all_nodes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Spacer(Modifier.weight(1f))
                    Text(
                        current ?: stringResource(R.string.v5_group_unset),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.accent,
                    )
                }
                if (data.nodes.isEmpty()) {
                    Text(
                        stringResource(R.string.v5_no_nodes),
                        fontSize = 12.5.sp,
                        color = c.text3,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                    )
                }
                data.nodes.forEachIndexed { index, node ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    NodeRow(
                        node = node,
                        selected = node.name == current,
                        testing = isTestingAll,
                        onClick = { onSelectNode(node.id) },
                    )
                }
            }

            // —— 分流规则组：每条规则组独立出口
            if (groupsForRouting.isNotEmpty()) {
                Text(
                    stringResource(R.string.v5_routing_groups),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = c.text3,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                V5CardFlat(Modifier.fillMaxWidth()) {
                    groupsForRouting.forEachIndexed { index, group ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                        V5RowItem(
                            title = group.name,
                            sub = stringResource(R.string.v5_group_exit) + " · " + groupExitLabel(group),
                            trailing = {
                                V5Button(
                                    stringResource(R.string.server_speed_test),
                                    ButtonStyle.TONAL,
                                    small = true,
                                    leadingIcon = Icons.Outlined.MonitorHeart,
                                    onClick = { onTestGroup(group.name) },
                                )
                            },
                            chevron = true,
                            onClick = { exitSheetGroup = group },
                        )
                    }
                }
            } else if (isLoadingGroups) {
                V5Card {
                    Text(
                        stringResource(R.string.v5_groups_loading),
                        fontSize = 12.5.sp,
                        color = c.text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            }

            // —— 分流规则管理入口
            V5Card {
                V5RowItem(
                    title = stringResource(R.string.settings_routing_rules),
                    sub = stringResource(R.string.settings_local_routing_desc),
                    icon = SlteIcons.Route,
                    highlight = true,
                    chevron = true,
                    onClick = onRoutingRules,
                )
            }
        }
    }

    exitSheetGroup?.let { group ->
        GroupExitSheet(
            group = group,
            onDismiss = { exitSheetGroup = null },
            onSelect = { member ->
                onSelectInGroup(group.name, member)
                exitSheetGroup = null
            },
        )
    }
}

@Composable
private fun NodeRow(
    node: NodeItem,
    selected: Boolean,
    testing: Boolean,
    onClick: () -> Unit,
) {
    val (label, tone) = latencyLabel(node.delay)
    V5RowItem(
        title = node.name,
        sub = if (node.countryCode.isNotBlank() && node.countryCode != "XX") node.countryCode else null,
        leading = { RadioDot(on = selected) },
        trailing = {
            if (testing) {
                Text("--", fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = V5ThemeColors.current.text3)
            } else {
                LatencyText(label, tone)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun QuickTile(
    tone: TileTone,
    label: String,
    node: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    val t = c.tile(tone)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(t.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
        Text(node, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
    }
}
