// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 v5 原型工程移植（v5 图表组件；原型脚手架已归档清理）。

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
 * 柱子用纯色 + 顶部一道高光条 —— 用**柱高**表达用量，而不是让柱底淡出去换"明度层次"。
 *
 * 第 13 轮真机复核修正：原先的纵向渐变 `0.62 → 0.18`（endY = plotBottom）让柱底恒为 18% alpha，
 * 实测柱底只有 1.30~1.87:1、20/27 根低于 1.5:1，低流量日看起来像"没数据"；基线 hairline2
 * 更是只有 1.16:1。现改为纯色柱 + text3 基线 + 零值日短桩。
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

        // 唯一一条基线，不画虚线网格。原值 c.hairline2(#EFEDF5) 对白卡只有 1.16:1，轴等于不存在；
        // 换成 c.text3（4.76:1）——基线属于"理解图表所必需的图形对象"，WCAG 1.4.11 要求 ≥3:1。
        drawLine(c.text3, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), V5Spacing.dp1.toPx())

        days.forEachIndexed { i, record ->
            val barHeight = record.totalBytes.toFloat() / scale * plotHeight
            val left = plotLeft + i * (barWidth + barGap)
            // 最新一天：不用图例也能认出"今天"
            val latest = i == count - 1

            // 零值日不再"什么都不画"。原实现 `if (barHeight <= 0f) return@forEachIndexed` 让
            // "0 字节"与"这天没记录"在屏幕上完全等价 —— 用户会以为记录丢失或统计出错。
            // 改为在基线上留一根 2dp 短桩（c.text3，4.76:1），把"有记录、值为 0"显式画出来。
            if (barHeight <= 0f) {
                val stub = V5Spacing.dp2.toPx()
                drawRoundRect(
                    color = c.text3,
                    topLeft = Offset(left, plotBottom - stub),
                    size = Size(barWidth, stub),
                    cornerRadius = CornerRadius(stub / 2f, stub / 2f),
                )
                return@forEachIndexed
            }

            val top = plotBottom - barHeight
            // 纯色柱（c.accent 对白卡 4.61:1）。原实现是纵向渐变 0.62→0.18、endY = plotBottom，
            // 于是**柱底恒为 18% alpha**（1.27:1），柱子越矮越读不出来，实测 20/27 根低于 1.5:1。
            // 高度本身已经在表达用量，不必再让柱底牺牲对比度去换"明度层次"。
            drawRoundRect(
                color = c.accent,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(corner, corner),
            )
            // drawRoundRect 会把底边一并削圆，再用直角矩形把底部压回基线
            drawRect(
                color = c.accent,
                topLeft = Offset(left, top + barHeight - corner),
                size = Size(barWidth, corner.coerceAtMost(barHeight)),
            )
            // 顶部高光条：接管原纵向渐变承担的"收口/层次"，且不落在任何必须达标的对比度上。
            // 今天用满强度 accent2、其余日半强度 —— 保住"一眼认出最新一天"这一原有意图。
            val capHeight = V5Spacing.dp2.toPx().coerceAtMost(barHeight / 3f)
            drawRoundRect(
                color = if (latest) c.accent2 else c.accent2.copy(alpha = 0.5f),
                topLeft = Offset(left + corner * 0.5f, top),
                size = Size((barWidth - corner).coerceAtLeast(1f), capHeight),
                cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f),
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
