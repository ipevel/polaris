package com.slte.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.utils.Dimens
import com.slte.app.ui.theme.TextSizes

/**
 * 两个并排操作按钮：订阅（购买或更新）/ 邀请返利。
 */
@Composable
fun DashboardActionButtons(
    onUpdateSubscription: () -> Unit,
    hasPlan: Boolean = false,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing)
    ) {
        ActionButton(
            icon = Icons.Rounded.Refresh,
            text = stringResource(
                if (hasPlan) R.string.dashboard_update_subscription
                else R.string.dashboard_subscribe_buy
            ),
            tint = SlteColors.current.iconBlue,
            bg = SlteColors.current.iconBlueBg,
            modifier = Modifier.weight(1f),
            onClick = onUpdateSubscription
        )
        ActionButton(
            icon = Icons.Rounded.GroupAdd,
            text = stringResource(R.string.invite_title),
            tint = SlteColors.current.iconBlue,
            bg = SlteColors.current.iconBlueBg,
            modifier = Modifier.weight(1f),
            onClick = onInvite
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
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.height(Dimens.dashboardActionBtnHeight),
        shape = SlteShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.dashboardActionIconSize),
                tint = tint
            )
            Spacer(modifier = Modifier.width(Dimens.dashboardActionIconGap))
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = TextSizes.dashboardActionBtn,
                color = tint
            )
        }
    }
}
