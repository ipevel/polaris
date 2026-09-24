// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteColors

/** 波形缓冲点数（与 MainViewModel 采样上限一致）：1 点/秒，约 1 分钟滚动窗口 */
private const val WAVE_MAX_POINTS = 60

/** 波形纵轴最小刻度（字节/秒）：全零时保证基线仍平直可见且不除零 */
private const val WAVE_MIN_SCALE_BYTES = 512L * 1024L

/** 曲线峰值占画布高度的比例，顶部留白避免裁切 */
private const val WAVE_HEIGHT_RATIO = 0.86f

/**
 * 速度历史双曲线波形：下载主线（带渐变填充）+ 上传辅线。
 * 供首页 Hero 卡片底部 sparkline 复用。
 */
@Composable
internal fun SpeedWaveform(
    history: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    val downloadColor = SlteColors.current.accentInteractive
    val uploadColor = SlteColors.current.brandGold
    val gridColor = SlteColors.current.statusNeutral.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        // 底部基线
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        if (history.size < 2) return@Canvas
        val downloadSeries = history.map { it.second }
        val uploadSeries = history.map { it.first }
        val downloadPeak = downloadSeries.maxOrNull() ?: 0L
        val uploadPeak = uploadSeries.maxOrNull() ?: 0L
        drawSpeedSeries(
            values = downloadSeries,
            maxValue = maxOf(downloadPeak, WAVE_MIN_SCALE_BYTES).toFloat(),
            color = downloadColor,
            strokeWidth = 2.dp.toPx(),
            filled = true,
        )
        drawSpeedSeries(
            values = uploadSeries,
            maxValue = maxOf(uploadPeak, WAVE_MIN_SCALE_BYTES).toFloat(),
            color = uploadColor,
            strokeWidth = 1.5.dp.toPx(),
            filled = false,
        )
    }
}

/** 绘制一条速度曲线：最新数据靠右，缓冲未满时左侧留空等待补点。 */
private fun DrawScope.drawSpeedSeries(
    values: List<Long>,
    maxValue: Float,
    color: Color,
    strokeWidth: Float,
    filled: Boolean,
) {
    if (values.size < 2) return
    val divisor = (WAVE_MAX_POINTS - 1).coerceAtLeast(1)
    val stepX = size.width / divisor
    val startIndex = WAVE_MAX_POINTS - values.size
    val line = Path()
    values.forEachIndexed { index, value ->
        val x = (startIndex + index) * stepX
        val ratio = (value / maxValue).coerceIn(0f, 1f)
        val y = size.height - ratio * size.height * WAVE_HEIGHT_RATIO
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    if (filled) {
        val fill =
            Path().apply {
                addPath(line)
                lineTo((startIndex + values.size - 1) * stepX, size.height)
                lineTo(startIndex * stepX, size.height)
                close()
            }
        drawPath(
            fill,
            brush =
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.25f), Color.Transparent),
            ),
        )
    }
    drawPath(
        line,
        color = color,
        style =
        Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
