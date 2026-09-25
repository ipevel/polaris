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
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.kernel.RoutingReservedNames
import com.slte.app.kernel.orderMembers
import com.slte.app.kernel.primaryGroupOf
import com.slte.app.kernel.visibleGroupMembers
import com.slte.app.ui.screen.server.ServerData
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5Switch
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
   直连/拦截/具体节点）。它与「节点选择」用**同一套可展开卡片**渲染
   （见下方 NodeGroupCard），此前的行内文字按钮 + GroupExitSheet 弹层已删除。
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
    // 本地分流方案开关（从设置页迁来；状态与写入逻辑仍复用 SettingsViewModel）
    localRoutingEnabled: Boolean = false,
    localRoutingBusy: Boolean = false,
    onToggleLocalRouting: () -> Unit = {},
) {
    val c = V5ThemeColors.current
    val primary = primaryGroupOf(groups)
    // 主组过滤掉「故障转移」入口（产品决策），但保留 now 命中项避免"鬼选中"
    val members = primary?.let {
        orderMembers(visibleGroupMembers(it.members, it.now), nodeOrderIndex(data))
    } ?: emptyList()
    val groupsForRouting = routingGroupsOf(groups)

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
            // 结构项与具体节点同层可选，选中态完全由内核回读的 now 决定（不做乐观更新）。
            NodeGroupCard(
                name = primary?.name ?: stringResource(R.string.v5_all_nodes),
                nowLabel = primary?.now ?: stringResource(R.string.v5_group_unset),
                selectedName = primary?.now,
                members = members,
                collapsed = primary != null && primary.name in collapsedSections,
                enabled = primary != null,
                isLoading = isLoadingGroups,
                onToggle = { primary?.let { onToggleSection(it.name) } },
                onSelect = { onSelectPrimary(it) },
            )

            // —— 分流规则组：与「节点选择」**同一套卡片 UI**（此前是 V5RowItem + 行内文字按钮 + 弹层，
            // 用户要求"分流规则组的 UI 不要，都沿用节点选择的 UI"）。
            // 默认收起 + 单开手风琴（在 ServerViewModel.toggleSection 内保证）：每条分流组都
            // include-all 了全部节点，而本页滚动体是非懒加载的 Column，全展开会组合出「组数 × 节点数」行。
            if (groupsForRouting.isNotEmpty()) {
                Text(
                    stringResource(R.string.v5_routing_groups),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = c.text3,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                groupsForRouting.forEach { group ->
                    NodeGroupCard(
                        name = group.name,
                        nowLabel = groupExitLabel(group, primary?.name),
                        selectedName = group.now,
                        // 分流组同样过滤「故障转移」，保留 now 命中项
                        members = visibleGroupMembers(group.members, group.now),
                        collapsed = group.name in collapsedSections,
                        enabled = true,
                        isLoading = isLoadingGroups,
                        onToggle = { onToggleSection(group.name) },
                        onSelect = { onSelectInGroup(group.name, it) },
                    )
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

            // —— 底部：分流规则管理（方块入口）+ 本地分流方案开关（按产品要求不加小字描述）
            // 这两项原先在「设置」页，已迁到本页（设置页不再出现，避免同一设置两处入口）。
            V5CardFlat(Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.settings_routing_rules),
                    icon = SlteIcons.Route,
                    highlight = true,
                    chevron = true,
                    onClick = onRoutingRules,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.settings_local_routing),
                    trailing = {
                        V5Switch(checked = localRoutingEnabled)
                    },
                    // 写盘期间禁用（复用 SettingsViewModel 的在途互斥，避免并发写 routing.json）
                    onClick = if (localRoutingBusy) null else onToggleLocalRouting,
                )
            }
        }
    }
}

/**
 * 组卡片：**主组与分流组共用同一套 UI**（用户要求"分流规则组的 UI 不要，都沿用节点选择的 UI"）。
 *
 * 三个不变量：
 * 1. 头部命中区 ≥48dp，且**收起后仍显示当前出口**（用户确认流量走向的唯一入口）；
 * 2. 占位分支在折叠判断**之外**——收起时也要能看到"在加载 / 确实没有"，否则是一张空卡；
 * 3. 选中态只信内核回读的 `selectedName`，不做乐观更新。
 */
@Composable
private fun NodeGroupCard(
    name: String,
    nowLabel: String,
    selectedName: String?,
    members: List<KernelProxyMember>,
    collapsed: Boolean,
    enabled: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val c = V5ThemeColors.current
    V5CardFlat(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(enabled = enabled) { onToggle() }
                .padding(start = 15.dp, end = 12.dp, top = 13.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                nowLabel,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                color = c.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) {
                Icon(
                    if (collapsed) SlteIcons.ExpandMore else SlteIcons.ExpandLess,
                    contentDescription = null,
                    tint = c.text3,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }
        }
        when {
            members.isEmpty() && isLoading -> GroupPlaceholder(stringResource(R.string.v5_groups_loading))
            members.isEmpty() -> GroupPlaceholder(stringResource(R.string.v5_no_nodes))
            collapsed -> Unit
            else -> {
                members.forEachIndexed { index, member ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    MemberRow(
                        member = member,
                        selected = member.name == selectedName,
                        onClick = { onSelect(member.name) },
                    )
                }
            }
        }
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
