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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.slte.app.R
import com.slte.app.ui.screen.traffic.TrafficData
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.ui.v5.BarsChart
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.SectionTitle
import com.slte.app.ui.v5.V5Banner
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Divider
import com.slte.app.ui.v5.V5Donut
import com.slte.app.ui.v5.V5EmptyState
import com.slte.app.ui.v5.V5ErrorState
import com.slte.app.ui.v5.V5LoadingState
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.v5Enter
import com.slte.app.utils.FormatUtils
import kotlin.math.roundToInt

/* ============================================================
   v6 流量页（iOS 简约风）：用量环形 + 每日柱状图 + 每日明细清单
   （数据接线：TrafficViewModel 的 TrafficData）
   ============================================================ */

/** 环形直径。中心不放文字（合计已移到右侧），环可以做得更粗；但也不能太大——右侧统计栏要放得下「116.57GB 90%」这种最长的一行。 */
private val DonutSize = 132.dp

@Composable
internal fun V5TrafficScreen(
    data: TrafficData,
    onNavSelect: (NavTab) -> Unit,
    onRetry: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
) {
    val c = V5ThemeColors.current
    val totalDown = data.records.sumOf { it.downloadBytes }
    val totalUp = data.records.sumOf { it.uploadBytes }
    val total = totalDown + totalUp
    val hasRecords = data.records.isNotEmpty()
    V5PageScaffold(tab = NavTab.TRAFFIC, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_traffic))
        V5ScrollBody(NavTab.TRAFFIC) {
            // —— 用量环形：双环（外环下行 / 内环上行）+ 右侧统计。
            // 合计数字原本压在环心，会限制环的尺寸；挪到右侧后环心留空，双环可以做得更粗更大。
            // 仍然只在**确有记录**时渲染：否则会把"没拿到数据"伪装成"流量为 0"
            // （无套餐 / 加载失败时尤其误导）。
            if (hasRecords) {
                V5Card(Modifier.v5Enter(0)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp16),
                    ) {
                        V5Donut(
                            size = DonutSize,
                            down = if (total > 0L) totalDown.toFloat() / total else 0f,
                            up = if (total > 0L) totalUp.toFloat() / total else 0f,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                            // 合计是头条数字：置顶、加大，再一条细分隔线把它与两个方向分开。
                            Column {
                                Text(stringResource(R.string.v5_traffic_total), fontSize = V5Type.sp11_5, color = c.text3)
                                Text(
                                    FormatUtils.traffic(total),
                                    fontSize = V5Type.sp16,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text,
                                )
                            }
                            V5Divider()
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
                Column(
                    modifier = Modifier.v5Enter(1),
                    verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
                ) {
                    SectionTitle(stringResource(R.string.traffic_chart_title))
                    V5Card {
                        BarsChart(records = data.records)
                    }
                }
            }
            if (data.isLoading) {
                V5LoadingState(text = stringResource(R.string.v5_traffic_loading))
            }
            // 加载失败且一条记录都没有 = 死路：此前只有一行红字，没有任何出口。
            // 现在走统一错误态：重试（主行动）+ 错误详情 + 一键导出诊断（次行动）。
            data.errorMessageRes?.let { res ->
                if (!hasRecords && !data.isLoading) {
                    V5ErrorState(
                        message = stringResource(res),
                        onRetry = onRetry,
                        retryText = stringResource(R.string.notice_retry),
                        detail = data.errorDetail?.let { stringResource(R.string.traffic_load_failed_detail, it) },
                        secondaryText = stringResource(R.string.diag_export),
                        onSecondary = onExportDiagnostics,
                    )
                }
            }
            // 已有数据但刷新失败：内联横幅。此前这种失败写进了 toastRes，而 toastRes 全仓无消费者
            // → 用户完全看不到（"刷新失败但界面看起来没变"）。
            data.toastRes?.let { res ->
                if (hasRecords) {
                    V5Banner(ChipTone.DANGER, stringResource(res))
                    data.errorDetail?.let { detail ->
                        Text(
                            text = stringResource(R.string.traffic_load_failed_detail, detail),
                            fontSize = V5Type.sp11,
                            color = c.text3,
                        )
                    }
                }
            }
            if (!hasRecords && !data.isLoading && data.errorMessageRes == null) {
                // 区分「确实没有记录」与「面板没有这个接口」：后者此前显示成「暂无流量记录」，
                // 等于告诉用户"你没用过流量"，与事实相反。
                if (data.backendSupportsLog) {
                    V5EmptyState(title = stringResource(R.string.v5_traffic_empty))
                } else {
                    V5EmptyState(
                        title = stringResource(R.string.v5_traffic_unsupported_title),
                        description = stringResource(R.string.v5_traffic_unsupported),
                    )
                }
            }
            if (data.records.isNotEmpty()) {
                Column(
                    modifier = Modifier.v5Enter(2),
                    verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
                ) {
                    SectionTitle(stringResource(R.string.traffic_detail_section))
                    V5CardFlat(Modifier.fillMaxWidth()) {
                        data.records.forEachIndexed { index, record ->
                            if (index > 0) V5Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.date, fontSize = V5Type.sp13_5, fontWeight = FontWeight.SemiBold, color = c.text)
                                    Text(
                                        stringResource(R.string.v5_traffic_down) + " " + FormatUtils.traffic(record.downloadBytes),
                                        fontSize = V5Type.sp11_5,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.text3,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        FormatUtils.traffic(record.totalBytes),
                                        fontSize = V5Type.sp14,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.text,
                                    )
                                    Text(
                                        stringResource(R.string.v5_traffic_up) + " " + FormatUtils.traffic(record.uploadBytes),
                                        fontSize = V5Type.sp11_5,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.text3,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(top = V5Spacing.dp4))
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
        horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp6),
    ) {
        Box(Modifier.size(V5Spacing.dp6).clip(CircleShape).background(color))
        Text(label, fontSize = V5Type.sp11_5, color = c.text2)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = V5Type.sp12_5,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = c.text,
        )
        // 值与占比之间留一道硬间隙：靠 Spacer(weight) 分配时，最长的一行
        // （116.57GB 90%）会把弹性间距压成 0，两个数字贴在一起读不出来。
        Spacer(Modifier.width(V5Spacing.dp8))
        Text(percent, fontSize = V5Type.sp11, fontFamily = FontFamily.Monospace, color = c.text3)
    }
}

/** 占比文案。总量为 0 时给 "--" 而不是 "0%"：那会伪装成"未被占用"。 */
private fun percentOf(
    part: Long,
    total: Long,
): String = if (total <= 0L) "--" else "${(part * 100.0 / total).roundToInt()}%"
