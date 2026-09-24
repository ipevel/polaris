// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.FlagPlaceholder
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import com.slte.app.utils.copyToClipboard

/**
 * 会话信息卡：把原先「当前 IP / 内网 IP / 本次用量 / 内存占用」四张单值卡合并为一张台账，
 * 左侧标签 + 右侧等宽数值 + 发丝线分行；数值列统一等宽避免同列抖动。
 */
@Composable
internal fun SessionInfoCard(
    currentIp: String,
    lanIp: String,
    sessionUploadBytes: Long,
    sessionDownloadBytes: Long,
    appMemoryUsedMb: Int,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toast = rememberToast()
    val haptic = LocalHapticFeedback.current
    val ipIsReal = isConnected && currentIp.isNotBlank() && currentIp != Constants.PLACEHOLDER_DASH
    val lanIsReal = isConnected && lanIp.isNotBlank() && lanIp != Constants.PLACEHOLDER_DASH
    val sessionBytes = if (isConnected) sessionUploadBytes + sessionDownloadBytes else 0L

    SlteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_session_info),
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TrafficDonut(
                    uploadBytes = sessionUploadBytes,
                    downloadBytes = sessionDownloadBytes,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            SessionLedgerRow(
                label = stringResource(R.string.dashboard_current_ip),
                value = if (ipIsReal) FormatUtils.compactIp(currentIp) else Constants.PLACEHOLDER_DASH,
                muted = !ipIsReal,
                statusDotColor = if (ipIsReal) SlteColors.current.statusSuccess else SlteColors.current.statusDanger,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    copyToClipboard(context, "exit_ip", currentIp)
                    toast.show(R.string.dashboard_ip_copied)
                },
            )
            SessionHairline()
            SessionLedgerRow(
                label = stringResource(R.string.dashboard_lan_ip),
                value = if (lanIsReal) lanIp else Constants.PLACEHOLDER_DASH,
                muted = !lanIsReal,
            )
            SessionHairline()
            SessionLedgerRow(
                label = stringResource(R.string.dashboard_session_usage),
                value = FormatUtils.traffic(sessionBytes),
                muted = !isConnected,
            )
            SessionHairline()
            SessionLedgerRow(
                label = stringResource(R.string.dashboard_memory),
                value = FormatUtils.traffic(appMemoryUsedMb.toLong() * BYTES_PER_MB),
            )
        }
    }
}

/** 台账单行：左标签（可选状态点）+ 右等宽数值，整行可长按触发复制。 */
@Composable
private fun SessionLedgerRow(
    label: String,
    value: String,
    muted: Boolean = false,
    statusDotColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clickModifier =
        if (onLongClick == null) {
            Modifier
        } else {
            Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = Dimens.gap.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            statusDotColor?.let { dotColor ->
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
                Box(
                    modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
        Text(
            text = value,
            style = SlteType.valueSmall,
            color =
            if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 台账行分隔线：1dp 发丝线。 */
@Composable
private fun SessionHairline() {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(Dimens.dividerThickness)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * 独立 IP 卡：首页 v4 已并入 [SessionInfoCard]，保留组件本体供其他入口复用，签名不变。
 */
@Composable
fun CurrentIpCard(
    currentIp: String,
    modifier: Modifier = Modifier,
    ipCountryCode: String? = null,
    titleRes: Int = R.string.dashboard_current_ip,
) {
    val context = LocalContext.current
    val toast = rememberToast()
    val haptic = LocalHapticFeedback.current
    val isOnline = currentIp.isNotBlank() && currentIp != Constants.PLACEHOLDER_DASH

    SlteCard(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyToClipboard(context, "exit_ip", currentIp)
                        toast.show(R.string.dashboard_ip_copied)
                    },
                )
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalArrangement = Arrangement.Top,
        ) {
            // 行1：图标 + 状态点 + 标题 + 旗帜
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SlteIcons.CurrentIp,
                    contentDescription = null,
                    tint = SlteColors.current.accentInteractive,
                    modifier = Modifier.size(Dimens.icon.sm),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) SlteColors.current.statusSuccess else SlteColors.current.statusDanger),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Text(
                    text = stringResource(titleRes),
                    modifier = Modifier.weight(1f, fill = false),
                    style = SlteType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                ipCountryCode?.let { code ->
                    FlagPlaceholder(countryCode = code, size = Dimens.icon.sm)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            // 行2：IP 地址（长按复制）
            Text(
                text = FormatUtils.compactIp(currentIp),
                style = SlteType.value,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val BYTES_PER_MB = 1024L * 1024L
