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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
   v5 首页：大圆连接钮 + 速率瓷片/曲线 + 会话信息 + 套餐用量
   （数据接线：MainViewModel 的 DashboardData）
   ============================================================ */

/**
 * 速率瓷片：数值用 [animateFloatAsState] 平滑过渡。
 *
 * 连接后速率每秒刷新，直接换字符串会让数字"跳"；用动画插值的数值做格式化，
 * 读数在刷新之间连续滑动，长时间盯着看不会觉得界面在抖。
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
                fontSize = V5Type.sp16,
                fontWeight = FontWeight.Bold,
                color = V5ThemeColors.current.text3,
            )
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = V5Type.sp16, fontWeight = FontWeight.Bold, color = V5ThemeColors.current.text)) {
                        append(FormatUtils.traffic(animatedBps.toLong()))
                    }
                    withStyle(SpanStyle(fontSize = V5Type.sp11, fontWeight = FontWeight.SemiBold, color = V5ThemeColors.current.text)) {
                        // 单位必须是 /s：FormatUtils.traffic() 已经带了 KB/MB/GB 的字节量纲，
                        // 再拼 "bps" 会变成 "17.55MB bps"（量纲与文字都错）。等价的现成写法见 FormatUtils.speed()。
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
    // 有效套餐把「剩余天数」放在到期日之前（复用「我的/套餐」页同款 v5_days_left，免新增 i18n）：
    // 只给到期日期用户还得自己心算，补上剩余天数才一眼可读。
    val expiryText = listOf(daysLeftLabel, expiredLabel).filter { it.isNotEmpty() }.joinToString(" · ")
    // 用量逼近上限（≥90%）时把数字与进度条转成警示红，提前提醒，避免用超/被限速。
    val nearLimit = fraction >= 0.9f
    val usedColor = if (nearLimit) c.danger else c.accent
    V5Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                GradientIcon(IconTone.BLUE, Icons.Outlined.CreditCard)
                Text(
                    data.planName.ifBlank { stringResource(R.string.usage_no_plan) },
                    fontSize = V5Type.sp15,
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
private fun SessionCard(data: DashboardData) {
    val c = V5ThemeColors.current
    val connected = data.isConnected
    // 运行中追加连接时长：实时曲线移除后，这里是首页唯一能体现「已连多久」的信息，
    // 也与原型设计（已运行 00:45:00）一致。未连接/连接中不显示时长。
    // connectedSinceElapsedMs 是「连接时刻」时间戳（elapsedRealtime 毫秒），不是时长本身，
    // 必须先与当前时刻相减；> 0 的判断顺带兜住异常数据，避免显示成设备开机时长。
    val uptime =
        if (connected && data.connectedSinceElapsedMs > 0L) {
            FormatUtils.formatDuration(SystemClock.elapsedRealtime() - data.connectedSinceElapsedMs)
        } else {
            ""
        }
    val statusText =
        if (connected) {
            listOf(stringResource(R.string.session_running), uptime).filter { it.isNotEmpty() }.joinToString(" · ")
        } else {
            stringResource(R.string.session_not_running)
        }
    // 会话信息：页面上唯一的实体卡（第三级"实体卡"层的代表）。内部账本行自带浅底，
    // 外层保留一张白卡把"这一段是会话信息"框起来，与上方无卡片的舞台区、下方扁平行拉开层次。
    V5Card {
        Column(verticalArrangement = Arrangement.spacedBy(V5Spacing.dp12)) {
            // 卡内不再重复「会话信息」标题：卡片上方的 SectionTitle 已经承担了这段的命名，
            // 卡里再写一遍就是同一句话说两次（观感上像标题的残影）。这里只留状态文字，
            // 靠右对齐顶在第一行，卡片的"标题行"由状态本身充当。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    statusText,
                    fontSize = V5Type.sp11_5,
                    fontFamily = FontFamily.Monospace,
                    color = c.text3,
                )
            }
            // 四项一行一项（整改要求 5）：形式照「内网IP / 内存占用」的账本行（左标签 + 右等宽值），
            // 效果照「当前IP / 本次用量」的瓷片（同色系底色）。此前 IP 与用量挤在两个并排瓷片里，
            // 窄屏下 IPv6 这类长值必然被截断——一行一项才显示得开。
            // 这些账本行自带浅色底与圆角，**不再外套白色 V5Card/V5CardFlat**：
            // 卡里再套一层白底会形成"卡中卡"，同一块信息被两层边界圈住，层级反而含糊
            // （frontend-design 点名的 SaaS 卡套件观感）。
            V5Ledger(
                listOf(
                    LedgerData(
                        stringResource(R.string.session_current_ip),
                        data.currentIp,
                        icon = Icons.Outlined.Public,
                        tone = TileTone.BLUE,
                    ),
                    LedgerData(
                        stringResource(R.string.session_lan_ip),
                        data.lanIp,
                        icon = Icons.Outlined.Memory,
                        color = if (connected) c.text else c.text3,
                        tone = TileTone.CYAN,
                    ),
                    LedgerData(
                        stringResource(R.string.session_used),
                        FormatUtils.traffic(data.sessionDownloadBytes + data.sessionUploadBytes),
                        icon = Icons.Outlined.BarChart,
                        tone = TileTone.ORANGE,
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
    onProxyModeClick: () -> Unit = {},
) {
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
    // 的 isConnecting 分支会停隧道并复位）。绝不能复用它下面的"套餐 / 通知权限 / VPN 授权"前置：
    // 连接中再弹一次 VPN 授权，用户一拒绝就会被置成未连接、而隧道可能已在途
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

    V5PageScaffold(tab = NavTab.HOME, breathing = connected, connected = connected, onNavSelect = onNavSelect) {
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
            // —— 第一级：舞台区。连接钮是首页的主角，**不给它套卡片**。
            // 卡片边界会把主角降格成"又一个白块"，与下面真正的信息卡抢层级；
            // 去掉后连接钮直接坐在氛围底上，只有它和状态胶囊占据这块空间。
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.v5Enter(0).fillMaxWidth(),
            ) {
                // 卡片内不再放「未受保护 / 等待连接」那一组状态：顶栏已经有唯一的连接状态，
                // 两者重复（整改要求 1：两个都取消掉）。
                HeroConnectButton(
                    connected = connected,
                    connecting = connecting,
                    onClick = handleToggle,
                )
                // 节点（含延迟）在按钮**下方**（整改要求 2：按钮在前、节点状态在后）。
                // 圆钮向下偏移 10dp，光弧紧贴外缘、粒子再向外飞约 18dp，因此这里留 32dp
                // 作为硬约束而非审美取值——余量不足会重现"按钮挡住节点"的历史缺陷。
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
            // —— 第二级：速率瓷片（扁平的并排小块，不套卡片）
            Row(horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp10), modifier = Modifier.v5Enter(1)) {
                SpeedTile(TileTone.BLUE, stringResource(R.string.v5_down_speed), data.downloadSpeedBps, Icons.Outlined.ArrowDownward, connected, Modifier.weight(1f))
                SpeedTile(TileTone.ORANGE, stringResource(R.string.v5_up_speed), data.uploadSpeedBps, Icons.Outlined.ArrowUpward, connected, Modifier.weight(1f))
            }
            // —— 代理模式（出口策略：规则/全局/直连）：v5 改造时入口丢失、功能整体不可达，
            //    这里在首页接回入口，复用既有 ProxyModeSheet 与 MainViewModel.setProxyMode。
            //    它是最轻的一级（扁平行），夹在瓷片与实体卡之间，承担"过渡层"。
            V5CardFlat(Modifier.v5Enter(2)) {
                V5RowItem(
                    title = stringResource(R.string.action_proxy_mode),
                    value = proxyModeLabelRes(data.proxyMode)?.let { stringResource(it) },
                    chevron = true,
                    onClick = onProxyModeClick,
                )
            }
            // —— 第三级：实体卡。用 SectionTitle 起头，把"这一段是什么"讲清楚。
            Column(Modifier.v5Enter(3), verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                SectionTitle(stringResource(R.string.session_title))
                SessionCard(data)
            }
            Column(Modifier.v5Enter(4), verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                SectionTitle(stringResource(R.string.usage_title))
                PlanUsageCard(data, onRenew)
            }
        }
    }
}
