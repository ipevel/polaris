// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 图表组件）。

package com.slte.app.ui.v5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.utils.FormatUtils

/* ============================================================
   v5 图表：近 30 天柱状图 / 用量环形
   ============================================================ */

/** 柱状图最多画多少天（与 v4 侧 TrafficTrendChart 同口径）。 */
private const val BARS_MAX_DAYS = 30

/** 纵轴刻度槽宽：刻度数字占左侧，柱子不压数字。 */
private val BarsYGutter = 40.dp

/**
 * 每日流量柱状图（接**真实**记录）。
 *
 * 此前这里是硬编码假数据：固定 30 根 `BARS` 配固定 "6GB"/"08-26"/"09-09" 标签，
 * 无论真实用量多少都画同一张图——图表说谎比没有图表更糟，故整体替换为按 [records] 绘制：
 * 纵轴刻度取峰值本身、横轴日期取首/中/末三天的真实 MM-DD、峰值柱顶标注具体流量。
 * 布局口径与 v4 侧 TrafficTrendChart 一致（同一套轴带 / 柱宽 / 峰值标注 / 最新一天满不透明），
 * 只是换成 v5 配色，且卡片外壳由调用方给（本组件只画图）。
 *
 * [records] 正序倒序都接受，内部统一按 date 正序取最近 [BARS_MAX_DAYS] 天。
 */
@Composable
fun BarsChart(
    records: List<TrafficLogRecord>,
    modifier: Modifier = Modifier,
    height: Dp = 118.dp,
) {
    val c = V5ThemeColors.current
    val days = remember(records) { records.sortedBy { it.date }.takeLast(BARS_MAX_DAYS) }
    val count = days.size
    val peak = days.maxOfOrNull { it.totalBytes } ?: 0L
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (count == 0) return@Canvas
        val maxLabel = measurer.measure(FormatUtils.traffic(peak), labelStyle)
        val zeroLabel = measurer.measure("0", labelStyle)
        val labelHeight = zeroLabel.size.height.toFloat()
        val labelGap = 2.dp.toPx()
        // 上下各让出一条标签带：峰值数字待在顶带内，日期待在底带内，都不压柱子
        val plotTop = labelHeight + labelGap
        val plotBottom = size.height - labelHeight - labelGap
        val plotLeft = BarsYGutter.toPx()
        val plotRight = size.width
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val scale = peak.coerceAtLeast(1L)
        val barGap = 2.dp.toPx()
        val barWidth = ((plotRight - plotLeft - barGap * (count - 1)) / count).coerceAtLeast(1f)
        val corner = 2.5.dp.toPx().coerceAtMost(barWidth / 2f)

        // 基线 + 50% / 100% 虚线网格
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val hair = 1.dp.toPx()
        drawLine(c.hairline2, Offset(plotLeft, plotTop), Offset(plotRight, plotTop), hair, pathEffect = dash)
        drawLine(
            c.hairline2,
            Offset(plotLeft, plotTop + plotHeight / 2f),
            Offset(plotRight, plotTop + plotHeight / 2f),
            hair,
            pathEffect = dash,
        )
        drawLine(c.hairline, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), hair)

        days.forEachIndexed { i, record ->
            val barHeight = record.totalBytes.toFloat() / scale * plotHeight
            if (barHeight <= 0f) return@forEachIndexed
            val left = plotLeft + i * (barWidth + barGap)
            val top = plotBottom - barHeight
            // 最新一天满不透明、更早的日子降透明度：不用图例也能认出最新一天
            val color = c.accent.copy(alpha = if (i == count - 1) 1f else 0.42f)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            // drawRoundRect 会把底边一并削圆，再用直角矩形把底部压回基线
            drawRect(
                color = color,
                topLeft = Offset(left, top + barHeight - corner),
                size = Size(barWidth, corner.coerceAtMost(barHeight)),
            )
        }

        // 纵轴只标基线 0：顶端刻度与峰值柱标注必然是同一个数字（峰值就是最大值），
        // 两处都画会在图上并排出现两个相同的数（真机截图实测），故只留贴柱的那一处。
        val gutterRight = plotLeft - 2.dp.toPx()
        drawText(zeroLabel, color = c.text3, topLeft = Offset(gutterRight - zeroLabel.size.width, plotBottom - labelHeight))

        // 峰值柱数值：标在柱顶上方（已预留的顶带内），横向夹在绘图区内
        if (peak > 0L) {
            val peakIndex = days.indexOfFirst { it.totalBytes == peak }
            if (peakIndex >= 0) {
                val center = plotLeft + peakIndex * (barWidth + barGap) + barWidth / 2f
                val left = (center - maxLabel.size.width / 2f)
                    .coerceIn(plotLeft, (plotRight - maxLabel.size.width).coerceAtLeast(plotLeft))
                drawText(maxLabel, color = c.text3, topLeft = Offset(left, plotTop - labelGap - labelHeight))
            }
        }

        // 横轴刻度：首 / 中 / 末三天的真实 MM-DD（此前是写死的 08-26 / 09-09）
        listOf(0, count / 2, count - 1).distinct().forEach { i ->
            val date = days[i].date
            if (date.isBlank()) return@forEach
            val layout = measurer.measure(shortDate(date), labelStyle)
            val center = plotLeft + i * (barWidth + barGap) + barWidth / 2f
            val left = (center - layout.size.width / 2f)
                .coerceIn(plotLeft, (plotRight - layout.size.width).coerceAtLeast(plotLeft))
            drawText(layout, color = c.text3, topLeft = Offset(left, plotBottom + labelGap))
        }
    }
}

/** ISO 日期转 MM-DD 轴标签（与 v4 侧 TrafficTrendChart 同口径）。 */
private fun shortDate(iso: String): String = if (iso.length >= 10) iso.substring(5) else iso

/** 用量环形（下行主色 + 上行琥珀），可选中心文字。 */
@Composable
fun V5Donut(
    size: Dp,
    stroke: Dp,
    down: Float,
    up: Float,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    Canvas(modifier.size(size)) {
        val st = stroke.toPx()
        val inset = st / 2
        val arcSize = Size(this.size.minDimension - st, this.size.minDimension - st)
        val tl = Offset(inset, inset)
        drawArc(c.surface3, 0f, 360f, false, topLeft = tl, size = arcSize, style = Stroke(st))
        drawArc(
            c.accent,
            -90f,
            down * 360f,
            false,
            topLeft = tl,
            size = arcSize,
            style = Stroke(st, cap = StrokeCap.Round),
        )
        if (up > 0f) {
            drawArc(
                c.up,
                -90f + down * 360f,
                up * 360f,
                false,
                topLeft = tl,
                size = arcSize,
                style = Stroke(st, cap = StrokeCap.Round),
            )
        }
    }
}
