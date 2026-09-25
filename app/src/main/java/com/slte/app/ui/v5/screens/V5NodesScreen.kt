// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
   v5 节点页：快捷选择 + 策略组列表（Karing 式本地组）+ 测速
   （数据接线：ServerViewModel）
   ============================================================ */

@Composable
private fun latencyOf(delay: Int?): Pair<String, ChipTone> = when {
    delay == null -> "--" to ChipTone.NEUTRAL
    delay >= Constants.DELAY_TIMEOUT -> stringResource(R.string.server_timeout) to ChipTone.DANGER
    else -> "$delay ms" to toneOfDelay(delay)
}

@Composable
private fun stringResTimeout(): String = stringResource(R.string.server_timeout)

private fun toneOfDelay(delay: Int): ChipTone = when {
    delay < 120 -> ChipTone.OK
    delay < 300 -> ChipTone.WARN
    else -> ChipTone.DANGER
}

@Composable
private fun GroupCard(
    group: KernelProxyGroupInfo,
    testing: Boolean,
    onSelect: (String, String) -> Unit,
    onTest: (String) -> Unit,
) {
    val c = V5ThemeColors.current
    val selectable = group.type.equals("Selector", ignoreCase = true)
    V5Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientIcon(
                    if (group.type.equals("URLTest", ignoreCase = true)) IconTone.GREEN else IconTone.BLUE,
                    Icons.Outlined.Hub,
                )
                Column(Modifier.weight(1f)) {
                    Text(group.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(
                        group.type + " · " + stringResource(R.string.v5_group_now, group.now ?: "--"),
                        fontSize = 11.5.sp,
                        color = c.text3,
                    )
                }
                V5Button(
                    stringResource(R.string.server_speed_test),
                    ButtonStyle.TONAL,
                    small = true,
                    leadingIcon = Icons.Outlined.MonitorHeart,
                    onClick = { onTest(group.name) },
                )
            }
            V5CardFlat(Modifier.fillMaxWidth()) {
                group.members.forEachIndexed { index, member ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    val (label, tone) = latencyOf(member.delay)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        V5RowItem(
                            title = member.name,
                            modifier = Modifier.weight(1f),
                            leading = { RadioDot(on = selectable && member.name == group.now) },
                            trailing = {
                                if (testing) {
                                    Text("--", fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = c.text3)
                                } else {
                                    LatencyText(label, tone)
                                }
                            },
                            onClick = if (selectable) {
                                { onSelect(group.name, member.name) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
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
    onSelectInGroup: (String, String) -> Unit,
    onTestGroup: (String) -> Unit,
    onStartSpeedTest: () -> Unit,
    onRefreshSubscription: () -> Unit,
    onRoutingRules: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
) {
    val c = V5ThemeColors.current
    V5PageScaffold(tab = NavTab.NODES, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_nodes)) {
            V5TopAction(Icons.Outlined.Sync, onRefreshSubscription)
            V5TopAction(Icons.Outlined.Speed, onStartSpeedTest)
        }
        V5ScrollBody(NavTab.NODES) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.v5_nodes_summary, data.nodes.size, groups.size),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.text3,
                )
                Spacer(Modifier.weight(1f))
                if (isTestingAll) {
                    V5Chip(ChipTone.ACCENT, stringResource(R.string.v5_speed_testing), dot = true)
                }
            }

            // —— 快捷卡：自动 / 故障转移 / 手动
            V5Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientIcon(IconTone.GREEN, Icons.Outlined.AutoAwesome)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.v5_quick_pick), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                stringResource(R.string.v5_quick_pick_desc),
                                fontSize = 11.5.sp,
                                color = c.text3,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MacaronTileSmall(
                            TileTone.GREEN,
                            stringResource(R.string.v5_auto_pick),
                            data.autoNode ?: "--",
                            data.autoNodeCountryCode,
                            Modifier.weight(1f),
                        ) { onQuickSelect(0) }
                        MacaronTileSmall(
                            TileTone.PURPLE,
                            stringResource(R.string.v5_fallback_pick),
                            data.fallbackNode ?: "--",
                            data.fallbackNodeCountryCode,
                            Modifier.weight(1f),
                        ) { onQuickSelect(-1) }
                    }
                }
            }

            // —— 分流规则入口（Karing 式每条规则组独立出口）
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

            // —— 策略组
            if (groups.isEmpty() && isLoadingGroups) {
                V5Card {
                    Text(
                        stringResource(R.string.v5_groups_loading),
                        fontSize = 12.5.sp,
                        color = c.text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            }
            groups.forEach { group ->
                GroupCard(
                    group = group,
                    testing = testingGroup == group.name,
                    onSelect = onSelectInGroup,
                    onTest = onTestGroup,
                )
            }
        }
    }
}

@Composable
private fun V5TopAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    V5TopIconButton(icon, onClick)
}

@Composable
private fun MacaronTileSmall(
    tone: TileTone,
    label: String,
    node: String,
    country: String?,
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
        country?.let {
            Box(Modifier.padding(top = 3.dp)) { V5Chip(ChipTone.NEUTRAL, it) }
        }
    }
}
