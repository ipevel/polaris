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
import androidx.compose.runtime.LaunchedEffect
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
import com.slte.app.kernel.KernelProxyMemberKind
import com.slte.app.kernel.PRIMARY_SECTION_KEY
import com.slte.app.kernel.RoutingReservedNames
import com.slte.app.kernel.orderMembers
import com.slte.app.kernel.primaryGroupOf
import com.slte.app.kernel.visibleGroupMembers
import com.slte.app.ui.screen.server.NodeItem
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

/**
 * 分流规则组 = 除结构组（主选择/自动/故障转移/漏网之鱼）与**已解析出的主组**外的全部策略组。
 *
 * 必须排除已解析主组：本地分流关闭 / 面板改名时主组会回退到"第一个 Selector 组"（非保留名），
 * 不排除就会把同一个组渲染成两张卡（主卡 + 分流卡），且两张卡共用折叠键互相踩。
 */
private fun routingGroupsOf(
    all: List<KernelProxyGroupInfo>,
    primaryName: String?,
): List<KernelProxyGroupInfo> = all.filterNot { it.name in RoutingReservedNames || it.name == primaryName }

/** 面板节点顺序索引，用于把 include-all 的成员重排回订阅顺序。 */
private fun nodeOrderIndex(data: ServerData): Map<String, Int> {
    val indexed = data.nodes.withIndex()
    return indexed.associate { (index, node) -> node.name to index }
}

/**
 * 内核未运行（未连接）时的**只读**兜底名单。
 *
 * 节点行只来自内核策略组，于是未连接时整页只剩「暂无节点，请先更新订阅」这句误导文案
 * （用户实测反馈："首页那个连接不点，节点就全不显示"）。但订阅缓存在本地本来就有节点名单
 * （[ServerData.nodes] 由 SubscribeRepository 解析并缓存，离线可用），把它映射成节点行，
 * 用户至少**看得见自己买了哪些节点**。
 *
 * 延迟一律给 null（显示「未测」）：离线拿不到探测结果，编造数字比留空更糟。
 * 只读是刻意的——没有内核可切，点了不会生效，所以由调用方传 readOnlyHint 关掉交互。
 */
internal fun offlineMembersOf(nodes: List<NodeItem>): List<KernelProxyMember> = nodes.map {
    KernelProxyMember(
        name = it.name,
        isGroup = false,
        delay = null,
        kind = KernelProxyMemberKind.NODE,
    )
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
    // 首次进入节点页自动跑一次测速（整改要求 3）：没有这一步，节点行永远停在「未测」，
    // 用户无法判断"自动选择"凭什么选某个节点。真正的节流在 ServerViewModel（每个 App 会话
    // 只自动跑一次）——切 Tab 会重建本屏组合，这里拦不住重复触发。
    onAutoTestOnce: () -> Unit = {},
) {
    val c = V5ThemeColors.current
    val primary = primaryGroupOf(groups)
    // 面板/订阅顺序索引：主组与各分流组都要用它重排节点成员。分流组若漏掉这一步，
    // 会保留内核 include-all 的 UTF-8 字节序，与「节点选择」列表的排序不一致。
    val nodeOrder = nodeOrderIndex(data)
    // 主组过滤掉「故障转移」入口（产品决策），但保留 now 命中项避免"鬼选中"
    val members = primary?.let {
        orderMembers(visibleGroupMembers(it.members, it.now), nodeOrder)
    } ?: emptyList()
    val groupsForRouting = routingGroupsOf(groups, primary?.name)
    // 未连接（内核未运行）时用订阅缓存名单兜底，见 offlineMembersOf 的注释
    val offlineMembers = if (primary == null && members.isEmpty()) offlineMembersOf(data.nodes) else emptyList()

    LaunchedEffect(Unit) { onAutoTestOnce() }

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
                    // 未连接时"0 个分组"会与下方列出的节点名单自相矛盾，换成如实说明
                    text =
                    if (offlineMembers.isNotEmpty()) {
                        stringResource(R.string.v5_nodes_summary_offline, data.nodes.size)
                    } else {
                        stringResource(R.string.v5_nodes_summary, data.nodes.size, groupsForRouting.size)
                    },
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
            // 折叠键用 PRIMARY_SECTION_KEY 而非组名：主组的解析结果会回退/漂移，
            // 用组名做键会与"同名分流组"共用键互相踩（见 RoutingGroups.PRIMARY_SECTION_KEY）。
            NodeGroupCard(
                name = primary?.name ?: stringResource(R.string.v5_all_nodes),
                nowLabel = primary?.now ?: stringResource(R.string.v5_group_unset),
                selectedName = primary?.now,
                members = members.ifEmpty { offlineMembers },
                // 兜底名单必须可见：若沿用折叠态，它会被藏在收起状态里，用户照样"什么都看不到"
                collapsed = if (offlineMembers.isNotEmpty()) false else PRIMARY_SECTION_KEY in collapsedSections,
                enabled = primary != null,
                isLoading = isLoadingGroups,
                readOnlyHint =
                if (offlineMembers.isNotEmpty()) stringResource(R.string.v5_nodes_offline_hint) else null,
                onToggle = { onToggleSection(PRIMARY_SECTION_KEY) },
                onSelect = { onSelectPrimary(it) },
            )

            // —— 分流规则组：与「节点选择」**同一套卡片 UI**（此前是 V5RowItem + 行内文字按钮 + 弹层，
            // 用户要求"分流规则组的 UI 不要，都沿用节点选择的 UI"）。
            // 默认收起（ServerViewModel.syncCollapsedSections 首见即收起）+ 单开手风琴
            // （ServerViewModel.toggleSection 内保证）：每条分流组都 include-all 了全部节点，
            // 而本页滚动体是非懒加载的 Column，全展开会组合出「组数 × 节点数」行。
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
                        // 分流组同样过滤「故障转移」，保留 now 命中项；并套用与「节点选择」相同的
                        // 面板顺序重排（此前漏了 orderMembers，导致两组排序不一致）
                        members = orderMembers(visibleGroupMembers(group.members, group.now), nodeOrder),
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
    // 非空 = 这份名单是**只读**兜底（内核未运行，来自订阅缓存）：顶部显示说明文字，
    // 且每行不可点（没有内核可切，点了不会有任何效果，给交互反馈就是假交互）。
    readOnlyHint: String? = null,
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
                readOnlyHint?.let { GroupPlaceholder(it) }
                members.forEachIndexed { index, member ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    MemberRow(
                        member = member,
                        selected = member.name == selectedName,
                        onClick =
                        if (readOnlyHint == null) {
                            { onSelect(member.name) }
                        } else {
                            null
                        },
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
