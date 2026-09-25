// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 图表总高：上下各让出一条轴标签带，中间绘图区保持约 120dp。 */
private val TrendChartHeight = 152.dp
private val TrendBarGap = 2.dp
private val TrendBarRadius = 2.5.dp
private val TrendYLabelGutter = 44.dp
private val TrendLabelGap = 2.dp
private val TrendAxisLabelSize = 9.5.sp
private val TrendAxisLabelLineHeight = 12.sp
private const val TrendMaxDays = 30
private const val EarlierBarAlpha = 0.42f
private const val BaselineZeroLabel = "0"

/** 30 天流量趋势：基线 + 50%/100% 虚线 + 轴刻度 + 峰值标注，柱高可以读成具体流量。 */
@Composable
internal fun TrafficTrendChart(
    records: List<TrafficLogRecord>,
    modifier: Modifier = Modifier,
) {
    // 接口按日期倒序返回，统一转成时间正序绘制：最右一根 = 最新一天，满不透明
    val days = remember(records) { records.sortedBy { it.date }.takeLast(TrendMaxDays) }
    val peakBytes = days.maxOf { it.totalBytes }
    val scaleBytes = peakBytes.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val gridStrong = MaterialTheme.colorScheme.outline
    val gridFaint = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelStyle =
        SlteType.valueSmall.copy(
            fontSize = TrendAxisLabelSize,
            lineHeight = TrendAxisLabelLineHeight,
        )
    val measurer = rememberTextMeasurer()

    SlteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            Text(
                text = stringResource(R.string.traffic_chart_title),
                style = SlteType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            Canvas(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TrendChartHeight),
            ) {
                val zeroLabel = measurer.measure(BaselineZeroLabel, axisLabelStyle)
                val maxLabel = measurer.measure(FormatUtils.traffic(peakBytes), axisLabelStyle)
                // 轴标签高度决定上下两条预留带：峰值数字必须待在顶带内，才不会顶到卡片标题
                val labelHeight = zeroLabel.size.height.toFloat()
                val labelGap = TrendLabelGap.toPx()
                val plotLeft = TrendYLabelGutter.toPx()
                val plotRight = size.width
                val plotTop = labelHeight + labelGap
                val plotBottom = size.height - labelHeight - labelGap
                val plotHeight = plotBottom - plotTop
                val count = days.size
                val barGap = TrendBarGap.toPx()
                val barWidth = (plotRight - plotLeft - barGap * (count - 1)) / count
                val barRadius = TrendBarRadius.toPx().coerceAtMost(barWidth / 2f)
                val maxLabelWidth = maxLabel.size.width.toFloat()
                val maxLabelHeight = maxLabel.size.height.toFloat()
                val zeroLabelWidth = zeroLabel.size.width.toFloat()
                val zeroLabelHeight = zeroLabel.size.height.toFloat()

                // 基线 + 100% / 50% 虚线网格
                drawLine(
                    color = gridStrong,
                    start = Offset(plotLeft, plotBottom),
                    end = Offset(plotRight, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                drawLine(
                    color = gridFaint,
                    start = Offset(plotLeft, plotTop + plotHeight / 2f),
                    end = Offset(plotRight, plotTop + plotHeight / 2f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
                drawLine(
                    color = gridStrong,
                    start = Offset(plotLeft, plotTop),
                    end = Offset(plotRight, plotTop),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )

                days.forEachIndexed { index, record ->
                    val barHeight = record.totalBytes.toFloat() / scaleBytes.toFloat() * plotHeight
                    if (barHeight <= 0f) return@forEachIndexed
                    val left = plotLeft + index * (barWidth + barGap)
                    val top = plotBottom - barHeight
                    // 最新一天满不透明、更早的日子降透明度：不用图例也能认出「今天」
                    val alpha = if (index == count - 1) 1f else EarlierBarAlpha
                    val color = barColor.copy(alpha = alpha)
                    val radius = barRadius.coerceAtMost(barHeight / 2f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    // drawRoundRect 会把底边一并削圆，再用直角矩形把底部压回基线
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top + barHeight - radius),
                        size = Size(barWidth, radius),
                    )
                }

                // 纵轴刻度：最大值贴顶线、0 贴基线，都落在左侧槽内不压柱子
                val yLabelRight = plotLeft - labelGap
                drawText(
                    textLayoutResult = maxLabel,
                    color = labelColor,
                    topLeft = Offset(yLabelRight - maxLabelWidth, plotTop),
                )
                drawText(
                    textLayoutResult = zeroLabel,
                    color = labelColor,
                    topLeft = Offset(yLabelRight - zeroLabelWidth, plotBottom - zeroLabelHeight),
                )

                // 峰值柱数值：标在柱顶上方（已预留的顶部带内，横向夹在绘图区内）
                val peakIndex = days.indexOfFirst { it.totalBytes == peakBytes }
                if (peakIndex >= 0 && peakBytes > 0L) {
                    val peakCenter = plotLeft + peakIndex * (barWidth + barGap) + barWidth / 2f
                    val peakLeft =
                        (peakCenter - maxLabelWidth / 2f)
                            .coerceIn(plotLeft, (plotRight - maxLabelWidth).coerceAtLeast(plotLeft))
                    drawText(
                        textLayoutResult = maxLabel,
                        color = labelColor,
                        topLeft = Offset(peakLeft, plotTop - labelGap - maxLabelHeight),
                    )
                }

                // 横轴刻度：首 / 中 / 末三天的 ISO MM-DD
                val dateIndexes = listOf(0, count / 2, count - 1).distinct()
                for (index in dateIndexes) {
                    val date = days[index].date
                    if (date.isBlank()) continue
                    val layout = measurer.measure(shortDate(date), axisLabelStyle)
                    val width = layout.size.width.toFloat()
                    val center = plotLeft + index * (barWidth + barGap) + barWidth / 2f
                    val left =
                        (center - width / 2f)
                            .coerceIn(plotLeft, (plotRight - width).coerceAtLeast(plotLeft))
                    drawText(
                        textLayoutResult = layout,
                        color = labelColor,
                        topLeft = Offset(left, plotBottom + labelGap),
                    )
                }
            }
        }
    }
}

/** ISO 日期转 MM-DD 轴标签（原 TrafficScreen 内的私有工具，随 v5 迁移保留）。 */
private fun shortDate(iso: String): String = if (iso.length >= 10) iso.substring(5) else iso
