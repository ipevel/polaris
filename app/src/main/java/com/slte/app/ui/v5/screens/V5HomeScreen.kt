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
                    append(FormatUtils.traffic(bps))
                }
                withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = V5ThemeColors.current.text)) {
                    // 单位必须是 /s：FormatUtils.traffic() 已经带了 KB/MB/GB 的字节量纲，
                    // 再拼 "bps" 会变成 "17.55MB bps"（量纲与文字都错）。等价的现成写法见 FormatUtils.speed()。
                    append("/s")
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
            // 四项一行一项（整改要求 5）：形式照「内网IP / 内存占用」的账本行（左标签 + 右等宽值），
            // 效果照「当前IP / 本次用量」的瓷片（同色系底色）。此前 IP 与用量挤在两个并排瓷片里，
            // 窄屏下 IPv6 这类长值必然被截断——一行一项才显示得开。
            V5Ledger(
                listOf(
                    LedgerData(
                        stringResource(R.string.session_current_ip),
                        data.currentIp,
                        icon = Icons.Outlined.Public,
                        tone = TileTone.BLUE,
                    ),
                    LedgerData(
                        stringResource(R.string.session_used),
                        FormatUtils.traffic(data.sessionDownloadBytes + data.sessionUploadBytes),
                        icon = Icons.Outlined.BarChart,
                        tone = TileTone.ORANGE,
                    ),
                    LedgerData(
                        stringResource(R.string.session_lan_ip),
                        data.lanIp,
                        icon = Icons.Outlined.Memory,
                        color = if (connected) c.text else c.text3,
                        tone = TileTone.CYAN,
                    ),
                    LedgerData(
                        stringResource(R.string.session_memory),
                        stringResource(R.string.session_memory_mb, data.appMemoryUsedMb),
                        icon = Icons.Outlined.Cloud,
                        tone = TileTone.PURPLE,
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
    // 连接中态：v5 改造时把这个字段丢了（isConnecting 在 ui/v5/ 下零命中），
    // 导致"点连接"到"连上"之间界面与未连接完全一致（截图逐字节相同）。
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
        ) { }

    // 连接中再点 = 取消连接：必须**直接**走 onToggleConnection（MainViewModel.toggleConnection
    // 的 isConnecting 分支会停隧道并复位）。绝不能复用它下面的"套餐 / 通知权限 / VPN 授权"前置：
    // 连接中再弹一次 VPN 授权，用户一拒绝就会被置成未连接、而隧道可能已在途
    //（历史缺陷"连不上也关不掉"）。
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

    V5PageScaffold(tab = NavTab.HOME, onNavSelect = onNavSelect) {
        // 站点名由数据层合并（面板 comm/config → 面板域名 → 订阅 profile-title），
        // 未配置/未拉到才回退应用名。v5 改造时这里被写成固定 app_name，导致
        // DashboardData.siteName 一直是死数据（v4 首页的契约见 git 历史 MainScreen.kt）。
        V5TopBar(siteDisplayName(data.siteName, stringResource(R.string.app_name))) {
            when {
                connected ->
                    V5Chip(ChipTone.OK, stringResource(R.string.v5_connected_rule), icon = Icons.Outlined.Shield, large = true)
                connecting ->
                    V5Chip(ChipTone.ACCENT, stringResource(R.string.v5_connecting), icon = Icons.Outlined.Cloud, large = true)
                else ->
                    V5Chip(ChipTone.NEUTRAL, stringResource(R.string.v5_not_connected), icon = Icons.Outlined.Cloud, large = true)
            }
        }
        V5ScrollBody(NavTab.HOME) {
            // —— 主角：大圆连接钮卡
            V5Card {
                // fillMaxWidth 是必需的：卡片本身按内容宽收缩，而这里最宽的子树只有
                // 176dp 圆钮（352px），少了它就只剩约一半屏宽、与下方通栏瓷片对不齐
                // ——原来是卡片里那行状态 Row 自带 fillMaxWidth 在"撑"着宽度，删掉那行
                //（整改要求 1）后必须自己撑。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 卡片内不再放「未受保护 / 等待连接」那一组状态：顶栏已经有唯一的连接状态，
                    // 两者重复（整改要求 1：两个都取消掉）。
                    HeroConnectButton(
                        connected = connected,
                        connecting = connecting,
                        onClick = handleToggle,
                    )
                    // 节点（含延迟）在按钮**下方**（整改要求 2：按钮在前、节点状态在后）。
                    // 32dp 是硬约束而非审美取值：圆钮的光环/脉冲环向下溢出约 23dp
                    // （V5Components.kt 的 drawBehind r+13dp、脉冲环 r+23dp），余量不足会
                    // 重新出现"按钮挡住节点"的历史缺陷。
                    // 延迟取自测速缓存（未测过则不显示，不写"未测"以免误导）；出口是
                    // 「自动选择」时 serverName 已被 serverInfo() 解析为其当前选中的叶子节点，
                    // 因此这里显示的确实是那个节点的延迟。
                    V5Chip(
                        if (connected) ChipTone.OK else ChipTone.ACCENT,
                        if (connected) {
                            val base = stringResource(R.string.v5_connected_node, data.serverName)
                            val delay = data.exitDelay
                            if (delay != null && delay > 0) "$base · $delay ms" else base
                        } else {
                            stringResource(R.string.v5_ready_no_node)
                        },
                        dot = !connected,
                        icon = if (connected) Icons.Outlined.Shield else null,
                        large = true,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
            // —— 速率瓷片
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedTile(TileTone.BLUE, stringResource(R.string.v5_down_speed), data.downloadSpeedBps, Icons.Outlined.ArrowDownward, Modifier.weight(1f))
                SpeedTile(TileTone.ORANGE, stringResource(R.string.v5_up_speed), data.uploadSpeedBps, Icons.Outlined.ArrowUpward, Modifier.weight(1f))
            }
            // —— 速率曲线（真实采样：MainViewModel.speedWatchJob 每秒写入 speedHistory）
            V5Card {
                if (connected) {
                    SparkChart(history = data.speedHistory)
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
