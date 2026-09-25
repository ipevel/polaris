// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（v5 图表组件）。

package com.slte.app.ui.v5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.ui.theme.V5ThemeColors

/* ============================================================
   v5 图表：实时速率曲线 / 近 30 天柱状图 / 用量环形
   ============================================================ */

private val SPARK_DOWN = floatArrayOf(
    8f, 14f, 11f, 22f, 17f, 30f, 26f, 38f, 33f, 44f, 40f, 52f, 47f, 58f,
    51f, 62f, 56f, 49f, 54f, 44f, 48f, 36f, 41f, 30f, 34f, 26f, 29f, 22f, 26f, 18f,
)
private val SPARK_UP = floatArrayOf(
    4f, 6f, 5f, 9f, 7f, 12f, 10f, 15f, 13f, 18f, 16f, 21f, 19f, 24f,
    21f, 26f, 23f, 20f, 22f, 18f, 20f, 15f, 17f, 13f, 15f, 11f, 13f, 9f, 11f, 7f,
)

/** 实时速率曲线：下行主色面积图 + 上行琥珀细线。 */
@Composable
fun SparkChart(modifier: Modifier = Modifier, height: Dp = 52.dp) {
    val c = V5ThemeColors.current
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val maxY = 66f
        fun pts(a: FloatArray): List<Offset> = a.mapIndexed { i, v -> Offset(i / (a.size - 1f) * w, h - v / maxY * h) }

        val down = pts(SPARK_DOWN)
        val up = pts(SPARK_UP)

        val area = Path().apply {
            moveTo(0f, h)
            down.forEach { lineTo(it.x, it.y) }
            lineTo(w, h)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(c.accent.copy(alpha = 0.24f), Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(
            Path().apply {
                moveTo(down[0].x, down[0].y)
                down.drop(1).forEach { lineTo(it.x, it.y) }
            },
            c.accent,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            Path().apply {
                moveTo(up[0].x, up[0].y)
                up.drop(1).forEach { lineTo(it.x, it.y) }
            },
            c.up.copy(alpha = 0.9f),
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private val BARS = intArrayOf(
    38, 62, 44, 88, 52, 24, 46, 20, 30, 58, 70, 42, 34, 26, 48,
    64, 36, 22, 40, 54, 28, 18, 44, 60, 32, 24, 50, 38, 20, 12,
)

/** 近 30 天柱状图（含虚线刻度 + 日期标签行）。 */
@Composable
fun BarsChart(modifier: Modifier = Modifier, height: Dp = 132.dp) {
    val c = V5ThemeColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            "6GB",
            fontSize = 9.5.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = c.text3,
        )
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val pad = 2.dp.toPx()
            val usable = h - pad * 2
            val gap = 2.dp.toPx()
            val bw = (w - gap * (BARS.size - 1)) / BARS.size
            val max = BARS.max()
            // 虚线刻度
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            drawLine(c.hairline2, Offset(0f, pad), Offset(w, pad), pathEffect = dash)
            drawLine(c.hairline2, Offset(0f, pad + usable / 2), Offset(w, pad + usable / 2), pathEffect = dash)
            drawLine(c.hairline, Offset(0f, h - pad), Offset(w, h - pad))
            BARS.forEachIndexed { i, v ->
                val bh = (v.toFloat() / max) * usable
                val x = i * (bw + gap)
                drawRoundRect(
                    c.accent.copy(alpha = if (i == BARS.size - 1) 1f else 0.4f),
                    topLeft = Offset(x, h - pad - bh),
                    size = Size(bw, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("08-26", fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = c.text3)
            Spacer(Modifier.weight(1f))
            Text("09-09", fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = c.text3)
            Spacer(Modifier.weight(1f))
            Text("今天", fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = c.text3)
        }
    }
}

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
