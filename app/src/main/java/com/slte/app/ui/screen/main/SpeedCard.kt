// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 波形缓冲点数（与 MainViewModel 采样上限一致）：1 点/秒，约 1 分钟滚动窗口 */
private const val WAVE_MAX_POINTS = 60

/** 波形纵轴最小刻度（字节/秒）：全零时保证基线仍平直可见且不除零 */
private const val WAVE_MIN_SCALE_BYTES = 512L * 1024L

/** 曲线峰值占画布高度的比例，顶部留白避免裁切 */
private const val WAVE_HEIGHT_RATIO = 0.86f

/**
 * 网络速度卡：标题行 + 实时上下行数值（竖排）+ 速度历史双曲线波形。
 * 与流量统计卡并排组成方形大卡行，连接开关已移至启动时间卡。
 */
@Composable
internal fun SpeedCard(
    uploadSpeedBps: Long,
    downloadSpeedBps: Long,
    speedHistory: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    SlteCard(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            // 行1：渐变圆点 + 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SlteColors.current.accentInteractive),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Text(
                    text = stringResource(R.string.dashboard_network_speed),
                    style = SlteType.body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            // 行2：下行主数值 + 上行次数值（竖排适配半宽卡）
            Text(
                text = "↓ " + stringResource(R.string.traffic_download),
                style = SlteType.bodySmall,
                fontWeight = FontWeight.Medium,
                color = SlteColors.current.accentInteractive,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.xs))
            Text(
                text = FormatUtils.speed(downloadSpeedBps),
                style = SlteType.display,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text = "↑ " + stringResource(R.string.traffic_upload),
                style = SlteType.bodySmall,
                fontWeight = FontWeight.Medium,
                color = SlteColors.current.brandGold,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.xs))
            Text(
                text = FormatUtils.speed(uploadSpeedBps),
                style = SlteType.heading,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            // 行3：速度历史波形（下载主线带渐变填充 + 上传辅线）
            SpeedWaveform(
                history = speedHistory,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

@Composable
private fun SpeedWaveform(
    history: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    val downloadColor = SlteColors.current.accentInteractive
    val uploadColor = SlteColors.current.brandGold
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
