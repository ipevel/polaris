// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 图表组件）。

package com.slte.app.ui.v5

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.utils.FormatUtils

/* ============================================================
   v5 图表：近 30 天柱状图 / 用量环形
   ============================================================ */

/** 柱状图最多画多少天。 */
private const val BARS_MAX_DAYS = 30

/**
 * 每日流量柱状图（接**真实**记录）。
 *
 * 此前这里是硬编码假数据：固定 30 根 `BARS` 配固定 "6GB"/"08-26"/"09-09" 标签，
 * 无论真实用量多少都画同一张图——图表说谎比没有图表更糟，故整体替换为按 [records] 绘制：
 * 峰值柱顶标注具体流量、横轴取首/中/末三天的真实 MM-DD。
 *
 * 视觉上刻意避开"图表库默认脸"：不画虚线网格（虚线 + 均分刻度是任何图表组件的默认长相），
 * 不留纵轴刻度槽（峰值已经标在柱顶，槽里孤零零一个「0」既占掉 40dp 宽度、又和基线重复），
 * 柱子改纵向渐变、越往下越淡——用明度差表达高度，而不是"一根实色柱顶着一排灰柱"的硬对比。
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
    val labelStyle = TextStyle(fontSize = V5Type.sp9_5, fontFamily = FontFamily.Monospace)

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (count == 0) return@Canvas
        val peakLabel = measurer.measure(FormatUtils.traffic(peak), labelStyle)
        val labelHeight = peakLabel.size.height.toFloat()
        val labelGap = V5Spacing.dp2.toPx()
        // 上下各让出一条标签带：峰值数字待在顶带内，日期待在底带内，都不压柱子
        val plotTop = labelHeight + labelGap
        val plotBottom = size.height - labelHeight - labelGap
        val plotLeft = 0f
        val plotRight = size.width
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val scale = peak.coerceAtLeast(1L)
        val barGap = V5Spacing.dp2.toPx()
        val barWidth = ((plotRight - plotLeft - barGap * (count - 1)) / count).coerceAtLeast(1f)
        val corner = (barWidth * 0.45f).coerceAtMost(V5Spacing.dp4.toPx())

        // 唯一一条基线，不画虚线网格。
        drawLine(c.hairline2, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), V5Spacing.dp1.toPx())

        days.forEachIndexed { i, record ->
            val barHeight = record.totalBytes.toFloat() / scale * plotHeight
            if (barHeight <= 0f) return@forEachIndexed
            val left = plotLeft + i * (barWidth + barGap)
            val top = plotBottom - barHeight
            // 最新一天略亮：不用图例也能认出"今天"，但不做"一根实色 + 其余灰"的硬对比
            val latest = i == count - 1
            val brush =
                Brush.verticalGradient(
                    colors = listOf(
                        c.accent.copy(alpha = if (latest) 0.95f else 0.62f),
                        c.accent.copy(alpha = if (latest) 0.40f else 0.18f),
                    ),
                    startY = top,
                    endY = plotBottom,
                )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            // drawRoundRect 会把底边一并削圆，再用直角矩形把底部压回基线
            drawRect(
                brush = brush,
                topLeft = Offset(left, top + barHeight - corner),
                size = Size(barWidth, corner.coerceAtMost(barHeight)),
            )
        }

        // 峰值柱数值：标在柱顶上方（已预留的顶带内），横向夹在绘图区内
        if (peak > 0L) {
            val peakIndex = days.indexOfFirst { it.totalBytes == peak }
            if (peakIndex >= 0) {
                val center = plotLeft + peakIndex * (barWidth + barGap) + barWidth / 2f
                val left = (center - peakLabel.size.width / 2f)
                    .coerceIn(plotLeft, (plotRight - peakLabel.size.width).coerceAtLeast(plotLeft))
                drawText(peakLabel, color = c.text3, topLeft = Offset(left, plotTop - labelGap - labelHeight))
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

/** ISO 日期转 MM-DD 轴标签。 */
private fun shortDate(iso: String): String = if (iso.length >= 10) iso.substring(5) else iso

/**
 * 用量环形（双环）：外环下行（品牌蓝渐变），内环上行（琥珀）。
 *
 * 为什么拆成同心两环：上下行是**两个独立口径**（各自占总量的比例），单环把两段弧首尾
 * 相接，占比只能靠弧长目测；两环各自成圆后，两个占比可以同时读出来。
 *
 * 刻意**不画底轨**：一圈灰槽会把"占比"读成"进度条刻度"，而且视觉上多出一圈固定轨道
 * （与连接钮同理——用户明确不要预定的可见轨道）。没被数据占到的角度就是留白。
 *
 * 动效分两层：①弧长在数据变化时用 900ms 缓动扫入（内环延后 140ms）；
 * ②弧端一颗光点 + 沿弧端向外飘散的细密粒子，让这页看起来是"在跑的"而不只是一张静图。
 * 弧端特效仅在确有占比时绘制，否则会在 12 点方向凭空多出一团光。
 */
@Composable
fun V5Donut(
    size: Dp,
    down: Float,
    up: Float,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    val animatedDown by animateFloatAsState(
        targetValue = down.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "donutDown",
    )
    val animatedUp by animateFloatAsState(
        targetValue = up.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "donutUp",
    )
    // 粒子相位：与弧长动画无关的独立循环，让端点持续"冒"。周期取整数秒，不抢注意力。
    val transition = rememberInfiniteTransition(label = "donutFx")
    val particlePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "donutParticlePhase",
    )
    val outerStroke = 18.dp
    val innerStroke = 15.dp
    val ringGap = 4.dp
    Canvas(modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = (size.toPx() - outerStroke.toPx()) / 2f
        val innerR = outerR - outerStroke.toPx() / 2f - ringGap.toPx() - innerStroke.toPx() / 2f

        // 外环：下行，从 12 点方向顺时针扫入。无底轨，没占到的角度就是留白。
        drawArc(
            brush = c.accentGrad,
            startAngle = -90f,
            sweepAngle = animatedDown * 360f,
            useCenter = false,
            topLeft = Offset(cx - outerR, cy - outerR),
            size = Size(outerR * 2f, outerR * 2f),
            style = Stroke(outerStroke.toPx(), cap = StrokeCap.Round),
        )
        // 内环：上行。用琥珀的同色渐变，避免第二环变成一块死色。
        drawArc(
            brush = Brush.linearGradient(listOf(c.up, c.up.copy(alpha = 0.72f))),
            startAngle = -90f,
            sweepAngle = animatedUp * 360f,
            useCenter = false,
            topLeft = Offset(cx - innerR, cy - innerR),
            size = Size(innerR * 2f, innerR * 2f),
            style = Stroke(innerStroke.toPx(), cap = StrokeCap.Round),
        )

        // 弧端特效：光晕 + 高光点 + 沿弧端切向散开的粒子。
        fun drawArcFx(
            radius: Float,
            fraction: Float,
            strokePx: Float,
            tint: Color,
        ) {
            if (fraction <= 0.01f) return
            val endAngle = -90f + fraction * 360f
            val endRad = Math.toRadians(endAngle.toDouble())
            val tipX = cx + kotlin.math.cos(endRad).toFloat() * radius
            val tipY = cy + kotlin.math.sin(endRad).toFloat() * radius

            val glowR = strokePx * 2.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(tipX, tipY),
                    radius = glowR,
                ),
                radius = glowR,
                center = Offset(tipX, tipY),
            )
            drawCircle(Color.White.copy(alpha = 0.45f), radius = strokePx * 0.22f, center = Offset(tipX, tipY))

            val count = 12
            for (i in 0 until count) {
                val life = (particlePhase + i.toFloat() / count) % 1f
                // 以弧端为中心向两侧铺开约 ±22°，形成一小片尘屑而不是一条直线。
                val spread = (i - (count - 1) / 2f) * 4f
                val a = Math.toRadians((endAngle + spread).toDouble())
                val dist = radius + life * 26.dp.toPx()
                val fade = (1f - life) * (1f - life)
                drawCircle(
                    color = tint,
                    radius = (0.3f + (i % 3) * 0.15f).dp.toPx(),
                    center = Offset(
                        cx + kotlin.math.cos(a).toFloat() * dist,
                        cy + kotlin.math.sin(a).toFloat() * dist,
                    ),
                    alpha = fade * 0.4f,
                )
            }
        }
        drawArcFx(outerR, animatedDown, outerStroke.toPx(), c.accent)
        drawArcFx(innerR, animatedUp, innerStroke.toPx(), c.up)
    }
}
