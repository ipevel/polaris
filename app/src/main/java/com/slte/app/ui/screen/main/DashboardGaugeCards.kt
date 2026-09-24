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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 首页连接 Hero（唯一主焦点）：状态胶囊 + 时长、66dp 电源主操作 + 当前节点、
 * 下行/上行两格、底部速度波形。原先分散在启动开关/速度/波形三处的信息收敛到一张卡。
 */
@Composable
internal fun ConnectionHeroCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedSinceElapsedMs: Long,
    serverName: String,
    uploadSpeedBps: Long,
    downloadSpeedBps: Long,
    speedHistory: List<Pair<Long, Long>>,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // connectedSinceElapsedMs 是点开关时的 SystemClock.elapsedRealtime 时刻（断开为 0），
    // 这里每秒刷新「当前时刻 - 起点」得到实时运行时长
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
    val durationPrefix = stringResource(R.string.dashboard_duration)
    val showNode = isConnected || isConnecting
    val nodeName = if (showNode) serverName.ifBlank { Constants.PLACEHOLDER_DASH } else Constants.PLACEHOLDER_DASH

    SlteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HeroStatusChip(isConnected = isConnected, isConnecting = isConnecting)
                Text(
                    text =
                    when {
                        isConnecting -> stringResource(R.string.status_connecting)
                        isConnected -> "$durationPrefix ${formatUptime(elapsedMs)}"
                        else -> stringResource(R.string.dashboard_wait_connect)
                    },
                    style = SlteType.valueSmall,
                    color =
                    if (isConnected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.md),
            ) {
                HeroPowerButton(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    onToggle = onToggleConnection,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_current_node),
                        style = SlteType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = nodeName,
                        style = SlteType.value,
                        color =
                        if (showNode) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!showNode) {
                        Text(
                            text = stringResource(R.string.dashboard_select_node_hint),
                            style = SlteType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
            ) {
                HeroSpeedTile(
                    arrow = "↓",
                    label = stringResource(R.string.traffic_download),
                    value = FormatUtils.speed(downloadSpeedBps),
                    accent = SlteColors.current.accentInteractive,
                    modifier = Modifier.weight(1f),
                )
                HeroSpeedTile(
                    arrow = "↑",
                    label = stringResource(R.string.traffic_upload),
                    value = FormatUtils.speed(uploadSpeedBps),
                    accent = SlteColors.current.brandGold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.dividerThickness)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            SpeedWaveform(
                history = speedHistory,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(top = Dimens.gap.sm),
            )
        }
    }
}

/** 连接状态胶囊：状态色圆点 + 文案，未连接时退化为中性表面色。 */
@Composable
private fun HeroStatusChip(
    isConnected: Boolean,
    isConnecting: Boolean,
) {
    val statusColor =
        when {
            isConnecting -> SlteColors.current.statusWarning
            isConnected -> SlteColors.current.statusSuccess
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val statusBg =
        when {
            isConnecting -> SlteColors.current.statusWarningBg
            isConnected -> SlteColors.current.statusSuccessBg
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val labelRes =
        when {
            isConnecting -> R.string.status_connecting
            isConnected -> R.string.status_connected
            else -> R.string.status_disconnected
        }

    Row(
        modifier =
        Modifier
            .clip(RoundedCornerShape(SlteRadii.pill))
            .background(statusBg)
            .padding(horizontal = Dimens.gap.sm, vertical = Dimens.gap.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap.xs),
    ) {
        Box(
            modifier =
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Text(
            text = stringResource(labelRes),
            style = SlteType.label,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 首页主操作：66dp 圆形电源键，连接=绿 / 连接中=橙（禁点）/ 未连接=中性槽色。 */
@Composable
private fun HeroPowerButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val container =
        when {
            isConnecting -> SlteColors.current.statusWarning
            isConnected -> SlteColors.current.statusSuccess
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val tint =
        if (isConnected || isConnecting) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
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
            .size(66.dp)
            .shadow(
                elevation = if (isConnected || isConnecting) 16.dp else 0.dp,
                shape = CircleShape,
                ambientColor = container.copy(alpha = 0.5f),
                spotColor = container.copy(alpha = 0.7f),
            )
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
            modifier = Modifier.size(30.dp),
        )
    }
}

/** 速度格：上行/下行各占一半宽度，数值等宽；金色只作上行数据序列色。 */
@Composable
private fun HeroSpeedTile(
    arrow: String,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .clip(RoundedCornerShape(SlteRadii.inner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.gap.md, vertical = Dimens.gap.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = arrow,
                style = SlteType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(modifier = Modifier.width(Dimens.gap.xs))
            Text(
                text = label,
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = value,
            style = SlteType.value,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 旧版 Hero 卡（环形会话流量 + 速度 + 波形）：首页 v4 已被 [ConnectionHeroCard] 取代，
 * 保留组件本体供其他入口复用，签名不变。
 */
@Composable
internal fun HeroTrafficCard(
    sessionUploadBytes: Long,
    sessionDownloadBytes: Long,
    uploadSpeedBps: Long,
    downloadSpeedBps: Long,
    speedHistory: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    SlteCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SlteRadii.card),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            // 行1：116dp 环形图（左）+ 右侧速度区（下行/上行两格竖排）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TrafficDonut(
                        uploadBytes = sessionUploadBytes,
                        downloadBytes = sessionDownloadBytes,
                        modifier = Modifier.size(116.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = FormatUtils.traffic(sessionUploadBytes + sessionDownloadBytes),
                            style = SlteType.heading,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_traffic_total),
                            style = SlteType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 右侧速度区：实时网速小标题 + 下行/上行两格（各占满右列宽度）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(SlteColors.current.accentInteractive),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.dashboard_network_speed),
                            style = SlteType.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HeroSpeedCell(
                        arrow = "↓",
                        label = stringResource(R.string.traffic_download),
                        value = FormatUtils.speed(downloadSpeedBps),
                        valueColor = SlteColors.current.accentInteractive,
                    )
                    HeroSpeedCell(
                        arrow = "↑",
                        label = stringResource(R.string.traffic_upload),
                        value = FormatUtils.speed(uploadSpeedBps),
                        valueColor = SlteColors.current.brandGold,
                    )
                }
            }
            // 行2：底部 sparkline，顶部加细分隔线
            Spacer(modifier = Modifier.height(Dimens.gap.md))
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.dividerThickness)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
            SpeedWaveform(
                history = speedHistory,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(top = 10.dp),
            )
        }
    }
}

/** 上下行速度格：左侧箭头+标签，右侧数值；背景为次级表面色，圆角小卡。 */
@Composable
private fun HeroSpeedCell(
    arrow: String,
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SlteRadii.inner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.gap.md, vertical = Dimens.gap.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = arrow,
                style = SlteType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = SlteType.caption,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = SlteType.value,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内网 IP 卡：等宽字体显示本机局域网地址。首页 v4 已并入会话信息卡，签名不变。 */
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
            style = SlteType.value,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内存卡：当前 app 进程占用内存（PSS），不再展示系统总内存。首页 v4 已并入会话信息卡。 */
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
                text =
                if (appMemoryUsedMb > 0) {
                    FormatUtils.traffic(appMemoryUsedMb.toLong() * BYTES_PER_MB)
                } else {
                    Constants.PLACEHOLDER_DASH
                },
                style = SlteType.value,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 启动开关卡：未启动显示灰色开关 + 服务已就绪，启动后实时累计运行时长。首页 v4 已并入 Hero。 */
@Composable
internal fun UptimeCard(
    connectedSinceElapsedMs: Long,
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // connectedSinceElapsedMs 是点开关时的 SystemClock.elapsedRealtime 时刻（断开为 0），
    // 这里每秒刷新「当前时刻 - 起点」得到实时运行时长
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
                style = SlteType.value,
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
            tint = MaterialTheme.colorScheme.onPrimary
        }
        isConnected -> {
            container = SlteColors.current.statusSuccess
            tint = MaterialTheme.colorScheme.onPrimary
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
            .shadow(
                elevation = if (isConnected || isConnecting) 12.dp else 0.dp,
                shape = CircleShape,
                ambientColor = container.copy(alpha = 0.5f),
                spotColor = container.copy(alpha = 0.7f),
            )
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
            modifier = Modifier.size(Dimens.icon.md),
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
    SlteCard(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
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
                    style = SlteType.label,
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
