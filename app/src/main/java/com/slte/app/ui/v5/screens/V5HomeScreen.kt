// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.slte.app.R
import com.slte.app.ui.screen.main.DashboardData
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.GradientIcon
import com.slte.app.ui.v5.HeroConnectButton
import com.slte.app.ui.v5.IconTone
import com.slte.app.ui.v5.LedgerData
import com.slte.app.ui.v5.MacaronTile
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.ProgressTrack
import com.slte.app.ui.v5.SparkChart
import com.slte.app.ui.v5.TileTone
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Ledger
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.utils.FormatUtils

/* ============================================================
   v5 首页：大圆连接钮 + 速率瓷片/曲线 + 会话信息 + 套餐用量
   （数据接线：MainViewModel 的 DashboardData）
   ============================================================ */

@Composable
private fun SpeedTile(
    tone: TileTone,
    label: String,
    bps: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    MacaronTile(tone, label, modifier, icon = icon) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = V5ThemeColors.current.text)) {
                    append(FormatUtils.traffic(bps) + " ")
                }
                withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = V5ThemeColors.current.text)) {
                    append("bps")
                }
            },
        )
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
    val expiredLabel =
        if (data.expiredAt > 0L) {
            stringResource(R.string.usage_expired_on, FormatUtils.formatExpiryDate(data.expiredAt))
        } else {
            ""
        }
    V5Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientIcon(IconTone.BLUE, Icons.Outlined.CreditCard)
                Text(
                    data.planName.ifBlank { stringResource(R.string.usage_no_plan) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                Spacer(Modifier.weight(1f))
                V5Chip(
                    if (data.isValid) ChipTone.OK else ChipTone.DANGER,
                    if (data.isValid) stringResource(R.string.usage_valid) else stringResource(R.string.usage_expired),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.usage_used), fontSize = 12.5.sp, color = c.text2)
                Text(
                    FormatUtils.traffic(data.usedBytes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = c.accent,
                )
                Text(stringResource(R.string.usage_total, FormatUtils.traffic(data.totalBytes)), fontSize = 12.5.sp, color = c.text3)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(fraction * 100).toInt()}%",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = c.accent,
                )
            }
            ProgressTrack(fraction)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(expiredLabel, fontSize = 12.5.sp, color = c.text3)
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
private fun SessionCard(data: DashboardData) {
    val c = V5ThemeColors.current
    val connected = data.isConnected
    V5Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.session_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Spacer(Modifier.weight(1f))
                Text(
                    if (connected) stringResource(R.string.session_running) else stringResource(R.string.session_not_running),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.text3,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacaronTile(
                    TileTone.BLUE,
                    stringResource(R.string.session_current_ip),
                    Modifier.weight(1f),
                    icon = Icons.Outlined.Public,
                    small = true,
                ) {
                    Text(
                        data.currentIp,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = c.text,
                    )
                }
                MacaronTile(
                    TileTone.ORANGE,
                    stringResource(R.string.session_used),
                    Modifier.weight(1f),
                    icon = Icons.Outlined.BarChart,
                    small = true,
                ) {
                    Text(
                        FormatUtils.traffic(data.sessionDownloadBytes + data.sessionUploadBytes),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = c.text,
                    )
                }
            }
            V5Ledger(
                listOf(
                    LedgerData(
                        stringResource(R.string.session_lan_ip),
                        data.lanIp,
                        icon = Icons.Outlined.Memory,
                        color = if (connected) c.text else c.text3,
                    ),
                    LedgerData(
                        stringResource(R.string.session_memory),
                        stringResource(R.string.session_memory_mb, data.appMemoryUsedMb),
                        icon = Icons.Outlined.Cloud,
                    ),
                ),
            )
        }
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
) {
    val c = V5ThemeColors.current
    val connected = data.isConnected
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
        ) { }

    // 与 v4 MainScreen 相同的连接开关流程：无套餐引导续费；通知权限请求；
    // VPN 授权意图先于实际开关
    val handleToggle = {
        if (!data.hasPlan) {
            android.widget.Toast
                .makeText(context, context.getString(R.string.dashboard_no_plan_tip), android.widget.Toast.LENGTH_SHORT)
                .show()
            onRenew()
        } else {
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
        Unit
    }

    LaunchedEffect(Unit) { refreshKernelInfo() }

    V5PageScaffold(tab = NavTab.HOME, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.app_name)) {
            if (connected) {
                V5Chip(ChipTone.OK, stringResource(R.string.v5_connected_rule), icon = Icons.Outlined.Shield, large = true)
            } else {
                V5Chip(ChipTone.NEUTRAL, stringResource(R.string.v5_not_connected), icon = Icons.Outlined.Cloud, large = true)
            }
        }
        V5ScrollBody(NavTab.HOME) {
            // —— 主角：大圆连接钮卡
            V5Card {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        V5Chip(
                            if (connected) ChipTone.OK else ChipTone.NEUTRAL,
                            if (connected) stringResource(R.string.v5_protected) else stringResource(R.string.v5_unprotected),
                            dot = true,
                            large = true,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (connected) stringResource(R.string.v5_connected_wait) else stringResource(R.string.v5_waiting),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.text3,
                        )
                    }
                    HeroConnectButton(
                        connected = connected,
                        modifier = Modifier.padding(top = 4.dp),
                        onClick = handleToggle,
                    )
                    V5Chip(
                        if (connected) ChipTone.OK else ChipTone.ACCENT,
                        if (connected) {
                            stringResource(R.string.v5_connected_node, data.serverName)
                        } else {
                            stringResource(R.string.v5_ready_no_node)
                        },
                        dot = !connected,
                        icon = if (connected) Icons.Outlined.Shield else null,
                        large = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            // —— 速率瓷片
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedTile(TileTone.BLUE, stringResource(R.string.v5_down_speed), data.downloadSpeedBps, Icons.Outlined.ArrowDownward, Modifier.weight(1f))
                SpeedTile(TileTone.ORANGE, stringResource(R.string.v5_up_speed), data.uploadSpeedBps, Icons.Outlined.ArrowUpward, Modifier.weight(1f))
            }
            // —— 速率曲线
            V5Card {
                if (connected) {
                    SparkChart()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.v5_chart_hint),
                            fontSize = 10.5.sp,
                            color = c.text3,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
            }
            SessionCard(data)
            PlanUsageCard(data, onRenew)
        }
    }
}
