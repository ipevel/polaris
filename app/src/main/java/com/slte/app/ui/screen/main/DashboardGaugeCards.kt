// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import java.util.Locale
import kotlinx.coroutines.delay

/** 会话流量卡：大号双色环形图（中心显示合计）+ 图例数值，纵向铺满方形卡。 */
@Composable
internal fun SessionTrafficCard(
    sessionUploadBytes: Long,
    sessionDownloadBytes: Long,
    modifier: Modifier = Modifier,
) {
    GaugeCard(
        title = stringResource(R.string.dashboard_traffic_stats),
        icon = SlteIcons.Traffic,
        modifier = modifier,
    ) {
        // 环形图在剩余空间垂直居中，中心叠加合计流量
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            TrafficDonut(
                uploadBytes = sessionUploadBytes,
                downloadBytes = sessionDownloadBytes,
                modifier = Modifier.size(88.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.dashboard_traffic_total),
                    style = SlteType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = FormatUtils.traffic(sessionUploadBytes + sessionDownloadBytes),
                    style = SlteType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.gap.md))
        // 图例：点 + 标签 + 数值紧凑同行左对齐，两行堆叠
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm)) {
            TrafficLegendItem(
                color = SlteColors.current.accentInteractive,
                label = stringResource(R.string.traffic_download),
                value = FormatUtils.traffic(sessionDownloadBytes),
            )
            TrafficLegendItem(
                color = SlteColors.current.brandGold,
                label = stringResource(R.string.traffic_upload),
                value = FormatUtils.traffic(sessionUploadBytes),
            )
        }
    }
}

/** 内网 IP 卡：等宽字体显示本机局域网地址。 */
@Composable
internal fun LanIpCard(
    lanIp: String,
    modifier: Modifier = Modifier,
) {
    GaugeCard(
        title = stringResource(R.string.dashboard_lan_ip),
        icon = SlteIcons.TunStack,
        modifier = modifier,
    ) {
        Text(
            text = lanIp,
            fontFamily = FontFamily.Monospace,
            style = SlteType.field,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内存卡：当前 app 进程占用内存（PSS），不再展示系统总内存。 */
@Composable
internal fun MemoryCard(
    appMemoryUsedMb: Int,
    modifier: Modifier = Modifier,
) {
    GaugeCard(
        title = stringResource(R.string.dashboard_memory),
        icon = SlteIcons.Server,
        modifier = modifier,
    ) {
        // 内容行与启动时间卡的 40dp 电源按钮行等高，保证并排卡片数值基线对齐
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (appMemoryUsedMb > 0) {
                    FormatUtils.traffic(appMemoryUsedMb.toLong() * BYTES_PER_MB)
                } else {
                    Constants.PLACEHOLDER_DASH
                },
                fontFamily = FontFamily.Monospace,
                style = SlteType.field,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 启动开关卡：未启动显示灰色开关 + 服务已就绪，启动后实时累计运行时长。 */
@Composable
internal fun UptimeCard(
    connectedSinceElapsedMs: Long,
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // connectedSinceElapsedMs 是点开关时的 SystemClock.elapsedRealtime 时刻（断开为 0），
    // 这里每秒刷新"当前时刻 - 起点"得到实时运行时长
    var nowElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(isConnected, isConnecting) {
        if (isConnected || isConnecting) {
            while (true) {
                nowElapsedMs = SystemClock.elapsedRealtime()
                delay(UPTIME_TICK_MS)
            }
        }
    }
    val elapsedMs = (nowElapsedMs - connectedSinceElapsedMs).coerceAtLeast(0L)
    GaugeCard(
        title = stringResource(R.string.dashboard_power_switch),
        icon = SlteIcons.Expiry,
        modifier = modifier,
    ) {
        val timeColor =
            when {
                isConnecting -> SlteColors.current.statusWarning
                isConnected -> SlteColors.current.statusSuccess
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                when {
                    isConnecting -> stringResource(R.string.status_connecting)
                    isConnected -> formatUptime(elapsedMs)
                    else -> stringResource(R.string.service_ready)
                },
                fontFamily = FontFamily.Monospace,
                style = SlteType.field,
                fontWeight = FontWeight.Medium,
                color = timeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PowerToggleButton(
                isConnected = isConnected,
                isConnecting = isConnecting,
                onToggle = onToggleConnection,
            )
        }
    }
}

/** 电源开关按钮：连接=绿、连接中=橙（禁点）、断开=灰。 */
@Composable
private fun PowerToggleButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val container: Color
    val tint: Color
    when {
        isConnecting -> {
            container = SlteColors.current.statusWarning
            tint = Color.White
        }
        isConnected -> {
            container = SlteColors.current.statusSuccess
            tint = Color.White
        }
        else -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val description =
        when {
            isConnecting -> stringResource(R.string.status_connecting)
            isConnected -> stringResource(R.string.status_connected)
            else -> stringResource(R.string.status_disconnected)
        }
    Box(
        modifier =
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = !isConnecting) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SlteIcons.Power,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 仪表卡统一骨架：图标 + 标题 + 内容，与 CurrentIpCard 视觉规格对齐。 */
@Composable
private fun GaugeCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.cardElevation,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SlteColors.current.accentInteractive,
                    modifier = Modifier.size(Dimens.icon.sm),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
                Text(
                    text = title,
                    style = SlteType.body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            content()
        }
    }
}

/** 会话流量环形图：底环 + 下载弧（晶蓝）+ 上传弧（星辉金）。 */
@Composable
internal fun TrafficDonut(
    uploadBytes: Long,
    downloadBytes: Long,
    modifier: Modifier = Modifier,
) {
    val total = uploadBytes + downloadBytes
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val downloadColor = SlteColors.current.accentInteractive
    val uploadColor = SlteColors.current.brandGold

    Canvas(modifier = modifier) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = trackColor.copy(alpha = 0.45f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (total > 0L) {
            val downloadSweep = 360f * (downloadBytes.toFloat() / total)
            drawArc(
                color = downloadColor,
                startAngle = -90f,
                sweepAngle = downloadSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            if (downloadSweep < 360f) {
                drawArc(
                    color = uploadColor,
                    startAngle = -90f + downloadSweep,
                    sweepAngle = 360f - downloadSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
        }
    }
}

/** 图例项：色点 + 标签 + 数值紧凑同行，标签定宽使数值列天然对齐。 */
@Composable
private fun TrafficLegendItem(
    color: Color,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(Dimens.gap.xs))
        Text(
            text = label,
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Dimens.gap.sm))
        Text(
            text = value,
            style = SlteType.field,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 连接时长格式化：HH:mm:ss；未连接显示 --。 */
private fun formatUptime(elapsedMs: Long): String {
    if (elapsedMs <= 0L) return Constants.PLACEHOLDER_DASH
    val totalSeconds = elapsedMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private const val BYTES_PER_MB = 1024L * 1024L

/** 启动开关运行时长刷新间隔（毫秒），每秒跳一次。 */
private const val UPTIME_TICK_MS = 1_000L
