package com.slte.app.ui.screen.main

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
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
    SlteCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyToClipboard(context, "exit_ip", currentIp)
                        toast.show(R.string.dashboard_ip_copied)
                    },
                ).padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorfulIconBadge(
                icon = SlteIcons.CurrentIp,
                colors = listOf(Color(0xFF4BCB1C), Color(0xFF00B0A0)),
            )

            Spacer(modifier = Modifier.width(Dimens.gap.md))

            Text(
                text = stringResource(R.string.dashboard_current_ip),
                style = SlteType.body.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            ipCountryCode?.let { code ->
                FlagPlaceholder(
                    countryCode = code,
                    size = Dimens.icon.sm,
                )
                Spacer(modifier = Modifier.width(Dimens.dashboardChevronGap))
            }

            Text(
                text = FormatUtils.compactIp(currentIp),
                fontFamily = FontFamily.Monospace,
                style = SlteType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = Dimens.dashboardListValueMaxWidth),
            )
        }
    }
}
