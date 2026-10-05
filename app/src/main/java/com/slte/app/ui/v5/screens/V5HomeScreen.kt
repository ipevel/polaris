// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.slte.app.R
import com.slte.app.ui.screen.main.DashboardData
import com.slte.app.ui.screen.main.proxyModeLabelRes
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.GradientIcon
import com.slte.app.ui.v5.HeroConnectButton
import com.slte.app.ui.v5.IconTone
import com.slte.app.ui.v5.LedgerData
import com.slte.app.ui.v5.LiveDot
import com.slte.app.ui.v5.MacaronTile
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.ProgressTrack
import com.slte.app.ui.v5.SectionTitle
import com.slte.app.ui.v5.TileTone
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Ledger
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.v5Enter
import com.slte.app.utils.FormatUtils

/* ============================================================
   v6 首页：大标题 + 白底色环连接钮 + 状态行 + 速率卡 + 会话/套餐
   （数据接线：MainViewModel 的 DashboardData；连接/权限逻辑与 v5 一致）
   ============================================================ */

/**
 * 速率卡：数值用 [animateFloatAsState] 平滑过渡（v5 结论沿用：
 * 连接后速率每秒刷新，直接换字符串会让数字"跳"，插值后读数连续滑动）。
 */
@Composable
private fun SpeedTile(
    tone: TileTone,
    label: String,
    bps: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedBps by
        animateFloatAsState(
            targetValue = bps.toFloat(),
            animationSpec = tween(650),
            label = "speedTileBps",
        )
    MacaronTile(tone, label, modifier, icon = icon, live = active) {
        if (!active) {
            // 未连接时不显示 "0B/s"：容易被读成"已连接但没流量"，用 "--" 明确表达"暂无速率"。
            Text(
                "--",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = V5ThemeColors.current.text3,
            )
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = V5ThemeColors.current.text)) {
                        append(FormatUtils.traffic(animatedBps.toLong()))
                    }
                    withStyle(SpanStyle(fontSize = V5Type.sp12, fontWeight = FontWeight.SemiBold, color = V5ThemeColors.current.text2)) {
                        // 单位必须是 /s：FormatUtils.traffic() 已经带了 KB/MB/GB 的字节量纲，
                        // 再拼 "bps" 会变成 "17.55MB bps"（量纲与文字都错）。
                        append("/s")
                    }
                },
            )
        }
    }
}

@Composable
private fun PlanUsageCard(
    data: DashboardData,
    onRenew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    val fraction =
        if (data.totalBytes > 0L) (data.usedBytes.toFloat() / data.totalBytes).coerceIn(0f, 1f) else 0f
    val daysLeftLabel =
        if (data.isValid && data.daysUntilExpired > 0) {
            stringResource(R.string.v5_days_left, data.daysUntilExpired)
        } else {
            ""
        }
    val expiredLabel =
        if (data.expiredAt > 0L) {
            stringResource(R.string.usage_expired_on, FormatUtils.formatExpiryDate(data.expiredAt))
        } else {
            ""
        }
    val expiryText = listOf(daysLeftLabel, expiredLabel).filter { it.isNotEmpty() }.joinToString(" · ")
    // 用量逼近上限（≥90%）时把数字与进度条转成警示红，提前提醒，避免用超/被限速。
    val nearLimit = fraction >= 0.9f
    val usedColor = if (nearLimit) c.danger else c.accent
    V5Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                GradientIcon(IconTone.BLUE, Icons.Outlined.CreditCard, size = 34.dp)
                Text(
                    data.planName.ifBlank { stringResource(R.string.usage_no_plan) },
                    fontSize = V5Type.sp16,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                Spacer(Modifier.weight(1f))
                V5Chip(
                    if (data.isValid) ChipTone.OK else ChipTone.DANGER,
                    if (data.isValid) stringResource(R.string.usage_valid) else stringResource(R.string.usage_expired),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp6)) {
                Text(stringResource(R.string.usage_used), fontSize = V5Type.sp12_5, color = c.text2)
                Text(
                    FormatUtils.traffic(data.usedBytes),
                    fontSize = V5Type.sp14,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = usedColor,
                )
                Text(stringResource(R.string.usage_total, FormatUtils.traffic(data.totalBytes)), fontSize = V5Type.sp12_5, color = c.text3)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(fraction * 100).toInt()}%",
                    fontSize = V5Type.sp12_5,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = usedColor,
                )
            }
            ProgressTrack(fraction, brush = if (nearLimit) SolidColor(c.danger) else null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp8)) {
                Text(expiryText, fontSize = V5Type.sp12_5, color = c.text3)
                Spacer(Modifier.weight(1f))
                V5Button(
                    stringResource(if (data.hasPlan) R.string.plan_renew_button else R.string.plan_buy_button),
                    ButtonStyle.TONAL,
                    small = true,
                    onClick = onRenew,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    data: DashboardData,
    proxyModeLabel: String?,
    onProxyModeClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    val connected = data.isConnected
    // connectedSinceElapsedMs 是「连接时刻」时间戳（elapsedRealtime 毫秒），不是时长本身，
    // 必须先与当前时刻相减；> 0 的判断顺带兜住异常数据，避免显示成设备开机时长。
    val uptime =
        if (connected && data.connectedSinceElapsedMs > 0L) {
            FormatUtils.formatDuration(SystemClock.elapsedRealtime() - data.connectedSinceElapsedMs)
        } else {
            ""
        }
    V5CardFlat {
        V5RowItem(
            title = stringResource(R.string.action_proxy_mode),
            value = proxyModeLabel,
            chevron = true,
            onClick = onProxyModeClick,
        )
        HorizontalDivider(
            thickness = V5Spacing.dp1,
            color = c.hairline2,
            modifier = Modifier.padding(start = V5Spacing.dp16),
        )
        V5Ledger(
            listOf(
                LedgerData(
                    stringResource(R.string.session_current_ip),
                    data.currentIp.ifBlank { "--" },
                    icon = Icons.Outlined.Public,
                ),
                LedgerData(
                    stringResource(R.string.session_used),
                    FormatUtils.traffic(data.sessionDownloadBytes + data.sessionUploadBytes),
                    icon = Icons.Outlined.BarChart,
                ),
                LedgerData(
                    stringResource(R.string.session_running),
                    uptime.ifBlank { stringResource(R.string.session_not_running) },
                    icon = Icons.Outlined.Memory,
                    color = if (connected) c.text else c.text3,
                ),
            ),
        )
    }
}

@Composable
internal fun V5HomeScreen(
    data: DashboardData,
    onToggleConnection: () -> Unit,
    onVpnPermissionDenied: () -> Unit,
    vpnRequestIntent: () -> android.content.Intent?,
    onRenew: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
    refreshKernelInfo: () -> Unit,
    onProxyModeClick: () -> Unit = {},
) {
    val c = V5ThemeColors.current
    val connected = data.isConnected
    // 连接中态：点连接后到连上之间，界面必须与未连接区分（v5 曾丢失该字段导致截图无差别）。
    val connecting = data.isConnecting
    val context = LocalContext.current
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                onToggleConnection()
            } else {
                onVpnPermissionDenied()
            }
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) {
                android.widget.Toast
                    .makeText(
                        context,
                        context.getString(R.string.notification_permission_denied),
                        android.widget.Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }

    // 连接中再点 = 取消连接：必须**直接**走 onToggleConnection（MainViewModel.toggleConnection
    // 的 isConnecting 分支会停隧道并复位）。绝不能复用下面的"套餐 / 通知权限 / VPN 授权"前置
    // （历史缺陷"连不上也关不掉"）。
    val handleToggle = {
        when {
            connecting -> onToggleConnection()
            !data.hasPlan -> {
                android.widget.Toast
                    .makeText(context, context.getString(R.string.dashboard_no_plan_tip), android.widget.Toast.LENGTH_SHORT)
                    .show()
                onRenew()
            }
            else -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val request = vpnRequestIntent()
                if (request != null) {
                    vpnPermissionLauncher.launch(request)
                } else {
                    onToggleConnection()
                }
            }
        }
        Unit
    }

    LaunchedEffect(Unit) { refreshKernelInfo() }

    // 大状态行：v5 的顶栏状态胶囊移到连接钮下方（iOS 式：圆钮 + 状态 + 节点行）。
    val statusBig = when {
        connected -> stringResource(R.string.v5_connected_title)
        connecting -> stringResource(R.string.v5_connecting)
        else -> stringResource(R.string.v5_not_connected)
    }
    val nodeLine = when {
        connected -> {
            val delay = data.exitDelay
            if (delay != null && delay > 0) "${data.serverName} · $delay ms" else data.serverName
        }
        connecting -> stringResource(R.string.v5_connecting_wait)
        else -> stringResource(R.string.v5_ready_no_node)
    }

    V5PageScaffold(tab = NavTab.HOME, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(NavTab.HOME.labelRes))
        V5ScrollBody(NavTab.HOME) {
            // —— 连接舞台：白底色环大圆钮 + 状态 + 节点行（v6 不再套卡片、不再有氛围底）。
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.v5Enter(0).fillMaxWidth().padding(top = V5Spacing.dp8),
            ) {
                HeroConnectButton(
                    connected = connected,
                    connecting = connecting,
                    onClick = handleToggle,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp6)) {
                    if (connected) {
                        LiveDot()
                    } else {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.text3))
                    }
                    Text(statusBig, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text)
                }
                Spacer(Modifier.height(V5Spacing.dp4))
                Text(
                    nodeLine,
                    fontSize = V5Type.sp15,
                    color = c.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = V5Spacing.dp16),
                )
            }
            // —— 速率卡
            Row(horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp12), modifier = Modifier.v5Enter(1)) {
                SpeedTile(TileTone.BLUE, stringResource(R.string.v5_down_speed), data.downloadSpeedBps, Icons.Outlined.ArrowDownward, connected, Modifier.weight(1f))
                SpeedTile(TileTone.ORANGE, stringResource(R.string.v5_up_speed), data.uploadSpeedBps, Icons.Outlined.ArrowUpward, connected, Modifier.weight(1f))
            }
            // —— 会话 / 套餐：iOS 分组段（代理模式并入会话卡第一行）。
            Column(Modifier.v5Enter(2), verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                SectionTitle(stringResource(R.string.session_title))
                SessionCard(
                    data,
                    proxyModeLabel = proxyModeLabelRes(data.proxyMode)?.let { stringResource(it) },
                    onProxyModeClick = onProxyModeClick,
                )
            }
            Column(Modifier.v5Enter(3), verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                SectionTitle(stringResource(R.string.usage_title))
                PlanUsageCard(data, onRenew)
            }
        }
    }
}
