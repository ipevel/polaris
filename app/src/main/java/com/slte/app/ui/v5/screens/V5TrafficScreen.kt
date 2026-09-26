// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.screen.traffic.TrafficData
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.BarsChart
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.V5Banner
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Donut
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.utils.FormatUtils
import kotlin.math.roundToInt

/* ============================================================
   v5 流量页：用量环形 + 每日柱状图 + 每日明细清单
   （数据接线：TrafficViewModel 的 TrafficData）
   ============================================================ */

/** 环形图直径；中心要放得下「合计流量」+ 数值两行，故不能更小。 */
private val DonutSize = 116.dp

@Composable
internal fun V5TrafficScreen(
    data: TrafficData,
    onNavSelect: (NavTab) -> Unit,
    onRetry: () -> Unit = {},
) {
    val c = V5ThemeColors.current
    val totalDown = data.records.sumOf { it.downloadBytes }
    val totalUp = data.records.sumOf { it.uploadBytes }
    val total = totalDown + totalUp
    val hasRecords = data.records.isNotEmpty()
    V5PageScaffold(tab = NavTab.TRAFFIC, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_traffic))
        V5ScrollBody(NavTab.TRAFFIC) {
            // —— 用量环形（整改要求 4）：此前只有「合计」一个大数字加两块并排瓷片，
            // 上下行占比要靠心算。现在环形图 + 中心合计 + 右侧图例一次说清比例与数值。
            // 仍然只在**确有记录**时渲染：否则会把"没拿到数据"伪装成"流量为 0"
            //（无套餐 / 加载失败时尤其误导）。
            if (hasRecords) {
                V5Card {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(Modifier.size(DonutSize), contentAlignment = Alignment.Center) {
                            V5Donut(
                                size = DonutSize,
                                stroke = 13.dp,
                                down = if (total > 0L) totalDown.toFloat() / total else 0f,
                                up = if (total > 0L) totalUp.toFloat() / total else 0f,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.v5_traffic_total), fontSize = 10.5.sp, color = c.text2)
                                Text(
                                    FormatUtils.traffic(total),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text,
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            TrafficLegend(
                                c.accent,
                                stringResource(R.string.v5_traffic_down),
                                FormatUtils.traffic(totalDown),
                                percentOf(totalDown, total),
                            )
                            TrafficLegend(
                                c.up,
                                stringResource(R.string.v5_traffic_up),
                                FormatUtils.traffic(totalUp),
                                percentOf(totalUp, total),
                            )
                        }
                    }
                }
                // —— 每日柱状图：全部来自 records（此前 V5Charts.BarsChart 是硬编码假数据，
                // 无论真实用量多少都画同一张图）
                V5Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.traffic_chart_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text,
                        )
                        BarsChart(records = data.records)
                    }
                }
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
            // 加载失败且一条记录都没有 = 死路：此前只有一行红字，没有任何出口。
            // 现在给错误态 + 可点重试（与订单页 ErrorState(onRetry) 同一约定）。
            data.errorMessageRes?.let { res ->
                if (!hasRecords && !data.isLoading) {
                    V5Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            V5Banner(ChipTone.DANGER, stringResource(res))
                            V5Button(stringResource(R.string.notice_retry), ButtonStyle.TONAL, small = true, onClick = onRetry)
                        }
                    }
                }
            }
            // 已有数据但刷新失败：内联横幅。此前这种失败写进了 toastRes，而 toastRes 全仓无消费者
            // → 用户完全看不到（"刷新失败但界面看起来没变"）。
            data.toastRes?.let { res ->
                if (hasRecords) {
                    V5Banner(ChipTone.DANGER, stringResource(res))
                }
            }
            if (!hasRecords && !data.isLoading && data.errorMessageRes == null) {
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

/**
 * 环形图图例一行：色点 + 名称 + 数值 + 占比。
 *
 * 色点必须与 [V5Donut] 的弧色同源（下行 accent / 上行 up），否则图例与环形对不上。
 */
@Composable
private fun TrafficLegend(
    color: Color,
    label: String,
    value: String,
    percent: String,
) {
    val c = V5ThemeColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 12.5.sp, color = c.text2)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = c.text,
        )
        Text(percent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.text3)
    }
}

/** 占比文案。总量为 0 时给 "--" 而不是 "0%"：那会伪装成"未被占用"。 */
private fun percentOf(
    part: Long,
    total: Long,
): String = if (total <= 0L) "--" else "${(part * 100.0 / total).roundToInt()}%"
