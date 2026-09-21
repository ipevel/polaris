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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.FlagPlaceholder
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils
import com.slte.app.utils.copyToClipboard

@Composable
fun CurrentIpCard(
    currentIp: String,
    modifier: Modifier = Modifier,
    ipCountryCode: String? = null,
) {
    val context = LocalContext.current
    val toast = rememberToast()
    val haptic = LocalHapticFeedback.current
    val isOnline = currentIp.isNotBlank() && currentIp != Constants.PLACEHOLDER_DASH

    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.cardElevation,
    ) {
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
            verticalArrangement = Arrangement.Center,
        ) {
            // 行1：状态点 + 标题 + 旗帜
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) SlteColors.current.statusSuccess else SlteColors.current.statusDanger),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Text(
                    text = stringResource(R.string.dashboard_current_ip),
                    style = SlteType.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                ipCountryCode?.let { code ->
                    FlagPlaceholder(countryCode = code, size = Dimens.icon.sm)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.xs))

            // 行2：IP 地址（长按复制）
            Text(
                text = FormatUtils.compactIp(currentIp),
                fontFamily = FontFamily.Monospace,
                style = SlteType.body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
