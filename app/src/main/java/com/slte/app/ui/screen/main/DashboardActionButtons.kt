package com.slte.app.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
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
    onServer: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    hasPlan: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                containerColor = SlteColors.current.accentInteractiveBg,
                modifier = Modifier.weight(1f),
                onClick = onUpdateSubscription,
            )
            ActionButton(
                icon = SlteIcons.Orders,
                text = stringResource(R.string.traffic_title),
                tint = SlteColors.current.statusSuccess,
                containerColor = SlteColors.current.statusSuccessBg,
                modifier = Modifier.weight(1f),
                onClick = onTraffic,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
        ) {
            ActionButton(
                icon = SlteIcons.Server,
                text = stringResource(R.string.proxy_groups_title),
                tint = SlteColors.current.statusWarning,
                containerColor = SlteColors.current.statusWarningBg,
                modifier = Modifier.weight(1f),
                onClick = onServer,
            )
            ActionButton(
                icon = SlteIcons.Profile,
                text = stringResource(R.string.profile_title),
                tint = SlteColors.current.statusNeutral,
                containerColor = SlteColors.current.statusNeutralBg,
                modifier = Modifier.weight(1f),
                onClick = onProfile,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SlteCard(
        modifier = modifier.height(Dimens.dashboardActionBtnHeight),
        shape = RoundedCornerShape(24.dp),
        containerColor = containerColor,
        onClick = onClick,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.icon.lg),
                tint = tint,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
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
