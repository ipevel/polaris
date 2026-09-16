package com.slte.app.ui.screen.main

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.ui.component.FlagPlaceholder
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import com.slte.app.utils.copyToClipboard

@Composable
fun InfoListCard(
    daysUntilExpired: Int?,
    serverName: String,
    proxyMode: String,
    currentIp: String,
    onServerClick: () -> Unit,
    onProxyClick: () -> Unit,
    modifier: Modifier = Modifier,
    ipCountryCode: String? = null,
) {
    val context = LocalContext.current
    val toast = rememberToast()
    SlteCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            InfoRow(
                icon = SlteIcons.Expiry,
                label = stringResource(R.string.dashboard_expiry),
                value =
                if (daysUntilExpired != null) {
                    pluralStringResource(R.plurals.dashboard_days, daysUntilExpired, daysUntilExpired)
                } else {
                    stringResource(R.string.plan_no_expiry)
                },
                onClick = null,
            )
            InfoRow(
                icon = SlteIcons.Server,
                label = stringResource(R.string.action_server),
                value = serverName,
                onClick = onServerClick,
            )
            InfoRow(
                icon = SlteIcons.ProxyMode,
                label = stringResource(R.string.action_proxy_mode),

                value = proxyModeLabelRes(proxyMode)?.let { stringResource(it) } ?: proxyMode,
                onClick = onProxyClick,
            )
            InfoRow(
                icon = SlteIcons.CurrentIp,
                label = stringResource(R.string.dashboard_current_ip),
                value = FormatUtils.compactIp(currentIp),
                leadingValue =
                ipCountryCode?.let { code ->
                    {
                        FlagPlaceholder(
                            countryCode = code,
                            size = Dimens.icon.sm,
                        )
                        Spacer(modifier = Modifier.width(Dimens.dashboardChevronGap))
                    }
                },
                onClick = null,
                isMonospace = true,
                onLongClick = {
                    copyToClipboard(context, "exit_ip", currentIp)
                    toast.show(R.string.dashboard_ip_copied)
                },
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    leadingValue: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isMonospace: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(Dimens.size.row)
            .let {
                if (onClick != null || onLongClick != null) {
                    it.combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick?.invoke()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick?.invoke()
                        },
                    )
                } else {
                    it
                }
            }.padding(horizontal = Dimens.gap.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlteIconBadge(icon = icon)

        Spacer(modifier = Modifier.width(Dimens.gap.md))

        Text(
            text = label,

            style = SlteType.body.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        leadingValue?.invoke()

        Text(
            text = value,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isMonospace) FontWeight.Normal else FontWeight.Medium,
            style = if (isMonospace) SlteType.bodySmall else SlteType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = Dimens.dashboardListValueMaxWidth),
        )

        if (onClick != null) {
            Spacer(modifier = Modifier.width(Dimens.dashboardChevronGap))
            Icon(
                imageVector = SlteIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.icon.md),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
