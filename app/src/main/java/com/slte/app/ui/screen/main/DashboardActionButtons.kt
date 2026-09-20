package com.slte.app.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
fun DashboardActionButtons(
    onUpdateSubscription: () -> Unit,
    onTraffic: () -> Unit,
    modifier: Modifier = Modifier,
    hasPlan: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
    ) {
        ActionButton(
            icon = SlteIcons.UpdateSubscription,
            text =
            stringResource(
                if (hasPlan) {
                    R.string.dashboard_update_subscription
                } else {
                    R.string.dashboard_subscribe_buy
                },
            ),
            tint = SlteColors.current.accentInteractive,
            bg = SlteColors.current.accentInteractiveBg,
            modifier = Modifier.weight(1f),
            onClick = onUpdateSubscription,
        )
        ActionButton(
            icon = SlteIcons.Orders,
            text = stringResource(R.string.traffic_title),
            tint = SlteColors.current.accentInteractive,
            bg = SlteColors.current.accentInteractiveBg,
            modifier = Modifier.weight(1f),
            onClick = onTraffic,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    tint: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SlteCard(
        modifier = modifier.height(Dimens.dashboardActionBtnHeight),
        onClick = onClick,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.icon.md),
                tint = tint,
            )
            Spacer(modifier = Modifier.width(Dimens.gap.sm))
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                fontWeight = FontWeight.SemiBold,
                style = SlteType.body,
                color = tint,
            )
        }
    }
}
