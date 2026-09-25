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

/** 速率曲线的横向格数（固定刻度，右侧对齐最新采样）；与 MainViewModel 的历史上限一致。 */
private const val WINDOW_POINTS = 60

/** 速率纵轴刻度地板（1 MiB/s，单位与 speedHistory 一致为字节/秒）：低于它按它归一，抑制空闲抖动。 */
private const val SPEED_SCALE_FLOOR_BPS = 1024L * 1024L

/**
 * 实时速率曲线：下行主色面积图 + 上行琥珀细线。
 *
 * [history] 为 `(上传 bps, 下载 bps)` 采样序列（MainViewModel 侧 1Hz、最多 60 点）。
 * 三个必须守住的点（否则会出现"崩溃/假波动/假山峰"）：
 * 1. **点数 < 2 不画线**：空列表取 `[0]` 会越界、单点 `/(size-1)` 会除零得 NaN；
 * 2. **固定 60 点窗口、右对齐**：若按 `i/(n-1)*w` 拉满宽度，刚连接时 2 个点先铺满整宽、
 *    之后每来一个采样整条曲线被压缩一次，是流式图表最典型的抖动；
 * 3. **标尺有地板**：空闲时几百 bps 的抖动若按窗口峰值归一，会被放大成满屏山峰（图表说谎）。
 */
@Composable
fun SparkChart(
    history: List<Pair<Long, Long>> = emptyList(),
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
) {
    val c = V5ThemeColors.current
    // 只用最近 WINDOW_POINTS 个采样，超出的老点丢弃（与 MainViewModel 的上限同源）。
    val samples = if (history.size > WINDOW_POINTS) history.takeLast(WINDOW_POINTS) else history
    // 归一化刻度：上行/下行共用同一峰值，保证两条线的相对高度可比。
    // 峰值取「窗口内最大值」与「1MiB/s 地板」的较大者：
    // - 取窗口最大值 → 两条线相对高度可比，任一方向为 0 时也不会把另一条线压平；
    // - 有地板 → 空闲时几百 B/s 的抖动不会被拉成满屏山峰（那属于图表说谎）。
    val peak = (samples.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L)
        .coerceAtLeast(SPEED_SCALE_FLOOR_BPS)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        // 采样不足两点时画基线：此前这里画的是硬编码假曲线，会让人误以为"有数据但不刷新"
        if (samples.size < 2) {
            drawLine(c.hairline2, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())
            return@Canvas
        }
        // 右对齐：x 按「固定 WINDOW_POINTS 格」定位，最新采样恒在最右侧。
        // 若按 i/(n-1)*w 拉满宽度，刚连接时 2 个点先铺满整宽、之后每来一个采样整条曲线
        // 被压缩一次——流式图表最典型的抖动，故 n 不参与横向刻度。
        val offset = WINDOW_POINTS - samples.size
        fun pts(value: (Pair<Long, Long>) -> Long): List<Offset> = samples.mapIndexed { i, sample ->
            val v = value(sample).coerceAtLeast(0L).toFloat()
            Offset((offset + i) / (WINDOW_POINTS - 1f) * w, h - (v / peak) * (h * 0.92f))
        }

        val down = pts { it.second }
        val up = pts { it.first }
        val area = Path().apply {
            moveTo(down.first().x, h)
            down.forEach { lineTo(it.x, it.y) }
            lineTo(down.last().x, h)
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
