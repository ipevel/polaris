// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.RoutingReservedNames
import com.slte.app.kernel.orderMembers
import com.slte.app.kernel.primaryGroupOf
import com.slte.app.ui.screen.server.ServerData
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.V5TopIconButton

/* ============================================================
   v5 节点页（组 → 成员两层）

   内核生成的结构是 Karing 式的：
     🚀 节点选择   select   = [自动选择, 故障转移, DIRECT] + include-all 全部节点
     自动选择      url-test  ← 独立组，同时又是主组成员
     故障转移      fallback  ← 独立组，同时又是主组成员

   所以「节点选择」这一层必须直接渲染主组的成员列表：自动选择 / 故障转移
   与具体节点是同一层里的可选项。此前把它们做成两枚独立磁贴、节点列表又
   只渲染面板 API 的扁平节点，导致主组选择在主屏不可见，分流组里的
   「跟随节点选择」也就失去了意义。

   第二层是「分流规则组」：每条规则组独立指定出口（跟随/自动/故障转移/
   直连/拦截/具体节点），由 GroupExitSheet 承担。
   ============================================================ */

/** 分流规则组 = 除结构组（主选择/自动/故障转移/漏网之鱼）外的全部策略组。 */
private fun routingGroupsOf(all: List<KernelProxyGroupInfo>): List<KernelProxyGroupInfo> {
    val rest = all.filterNot { it.name in RoutingReservedNames }
    return rest
}

/** 面板节点顺序索引，用于把 include-all 的成员重排回订阅顺序。 */
private fun nodeOrderIndex(data: ServerData): Map<String, Int> {
    val indexed = data.nodes.withIndex()
    return indexed.associate { (index, node) -> node.name to index }
}

@Composable
private fun groupExitLabel(
    group: KernelProxyGroupInfo,
    primaryName: String?,
): String {
    val now = group.now ?: return stringResource(R.string.v5_group_unset)
    return when (now) {
        primaryName -> stringResource(R.string.v5_group_follow_primary)
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
    onSelectPrimary: (String) -> Unit,
    onSelectInGroup: (String, String) -> Unit,
    onTestGroup: (String) -> Unit,
    onStartSpeedTest: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onRoutingRules: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
    // 折叠态由 ViewModel 持有（切 Tab 会销毁本屏组合，局部状态必丢）。
    // 给默认值是为了不破坏截图测试等既有调用点。
    collapsedSections: Set<String> = emptySet(),
    onToggleSection: (String) -> Unit = {},
) {
    val c = V5ThemeColors.current
    val primary = primaryGroupOf(groups)
    val members = primary?.let { orderMembers(it.members, nodeOrderIndex(data)) } ?: emptyList()
    val groupsForRouting = routingGroupsOf(groups)
    // 只存组名：存组对象快照会在组刷新后让弹层里的 now/延迟停在旧值
    var exitSheetGroupName by remember { mutableStateOf<String?>(null) }
    val exitSheetGroup = exitSheetGroupName?.let { name -> groups.firstOrNull { it.name == name } }

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

            // —— 节点选择：主组（🚀 节点选择）的全部成员
            // 结构项（自动选择 / 故障转移 / DIRECT）与具体节点同层可选，
            // 选中态完全由内核回读的主组 now 决定（不做乐观更新）。
            //
            // 折叠（用户反馈「节点选择没有折叠」）：
            // - 头部整行可点，命中区 ≥48dp（此前约 43dp，低于最小触控目标）；
            // - 收起后**仍显示当前出口**——那是用户确认流量走向的唯一入口，不能藏；
            // - 折叠只 gate 成员列表：加载中/无节点的占位必须照样显示，
            //   否则首屏是一张没有行、没有解释的空卡；
            // - 折叠态 key = 组名，存放在 ServerViewModel（本屏局部状态会被切 Tab 销毁）。
            val collapsed = primary != null && primary.name in collapsedSections
            V5CardFlat(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(enabled = primary != null) {
                            primary?.let { onToggleSection(it.name) }
                        }
                        .padding(start = 15.dp, end = 12.dp, top = 13.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        primary?.name ?: stringResource(R.string.v5_all_nodes),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 当前出口：收起后依然可见
                    Text(
                        primary?.now ?: stringResource(R.string.v5_group_unset),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 折叠箭头复用既有图标令牌；不用 ChevronRight（本工程语义是"进入下一层"）
                    if (primary != null) {
                        Icon(
                            if (collapsed) SlteIcons.ExpandMore else SlteIcons.ExpandLess,
                            contentDescription = null,
                            tint = c.text3,
                            modifier = Modifier.padding(start = 4.dp).size(20.dp),
                        )
                    }
                }
                when {
                    // 占位分支在折叠判断之外：收起时也要能看到"在加载/确实没有"
                    members.isEmpty() && isLoadingGroups -> GroupPlaceholder(stringResource(R.string.v5_groups_loading))
                    members.isEmpty() -> GroupPlaceholder(stringResource(R.string.v5_no_nodes))
                    collapsed -> Unit
                    else -> {
                        members.forEachIndexed { index, member ->
                            if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                            MemberRow(
                                member = member,
                                selected = member.name == primary?.now,
                                onClick = { onSelectPrimary(member.name) },
                            )
                        }
                    }
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
                            sub = stringResource(R.string.v5_group_exit) + " · " + groupExitLabel(group, primary?.name),
                            trailing = {
                                if (testingGroup == group.name) {
                                    V5Chip(ChipTone.ACCENT, stringResource(R.string.v5_speed_testing), dot = true)
                                } else {
                                    V5Button(
                                        stringResource(R.string.server_speed_test),
                                        ButtonStyle.TONAL,
                                        small = true,
                                        leadingIcon = Icons.Outlined.MonitorHeart,
                                        onClick = { onTestGroup(group.name) },
                                    )
                                }
                            },
                            chevron = true,
                            onClick = { exitSheetGroupName = group.name },
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
            onDismiss = { exitSheetGroupName = null },
            onSelect = { member ->
                onSelectInGroup(group.name, member)
                exitSheetGroupName = null
            },
        )
    }
}

@Composable
private fun GroupPlaceholder(text: String) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        color = V5ThemeColors.current.text3,
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
    )
}
