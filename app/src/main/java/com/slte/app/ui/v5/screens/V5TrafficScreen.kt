// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.screen.traffic.TrafficData
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.MacaronTile
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.TileTone
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.utils.FormatUtils

/* ============================================================
   v5 流量页：汇总瓷片 + 每日明细清单
   （数据接线：TrafficViewModel 的 TrafficData）
   ============================================================ */

@Composable
internal fun V5TrafficScreen(
    data: TrafficData,
    onNavSelect: (NavTab) -> Unit,
) {
    val c = V5ThemeColors.current
    val totalDown = data.records.sumOf { it.downloadBytes }
    val totalUp = data.records.sumOf { it.uploadBytes }
    V5PageScaffold(tab = NavTab.TRAFFIC, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_traffic))
        V5ScrollBody(NavTab.TRAFFIC) {
            // —— 合计（上下行相加）：此前只有上下行两个瓷贴，用户看不到总量
            V5Card {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.v5_traffic_total),
                            fontSize = 12.5.sp,
                            color = c.text2,
                        )
                        Text(
                            FormatUtils.traffic(totalDown + totalUp),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = c.accent,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacaronTile(TileTone.BLUE, stringResource(R.string.v5_traffic_down), FormatUtils.traffic(totalDown), Modifier.weight(1f))
                MacaronTile(TileTone.ORANGE, stringResource(R.string.v5_traffic_up), FormatUtils.traffic(totalUp), Modifier.weight(1f))
            }
            if (data.isLoading) {
                V5Card {
                    Text(
                        stringResource(R.string.v5_traffic_loading),
                        fontSize = 12.5.sp,
                        color = c.text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            }
            data.errorMessageRes?.let { res ->
                V5Card {
                    Text(
                        stringResource(res),
                        fontSize = 12.5.sp,
                        color = c.danger,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
            }
            if (data.records.isEmpty() && !data.isLoading) {
                V5Card {
                    Text(
                        stringResource(R.string.v5_traffic_empty),
                        fontSize = 12.5.sp,
                        color = c.text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            }
            if (data.records.isNotEmpty()) {
                V5CardFlat(Modifier.fillMaxWidth()) {
                    data.records.forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(record.date, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                Text(
                                    stringResource(R.string.v5_traffic_down) + " " + FormatUtils.traffic(record.downloadBytes),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text3,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    FormatUtils.traffic(record.totalBytes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text,
                                )
                                Text(
                                    stringResource(R.string.v5_traffic_up) + " " + FormatUtils.traffic(record.uploadBytes),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text3,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
                V5Chip(ChipTone.NEUTRAL, stringResource(R.string.v5_traffic_hint))
            }
        }
    }
}
