package com.slte.app.ui.screen.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
fun ConnectToggleCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = Dimens.dashboardToggleCardMinHeight,
) {
    SlteCard(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BigToggle(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    onClick = onToggle,
                )
                Spacer(modifier = Modifier.height(Dimens.dashboardToggleGap))
                Text(
                    text =
                    when {
                        isConnecting -> stringResource(R.string.status_connecting)
                        isConnected -> stringResource(R.string.status_connected)
                        else -> stringResource(R.string.status_disconnected)
                    },
                    fontWeight = FontWeight.Medium,
                    style = SlteType.bodySmall,
                    color =
                    when {
                        isConnected -> SlteColors.current.statusSuccess
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun BigToggle(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val connectLabel = stringResource(R.string.dashboard_connect_switch)
    val statusText =
        when {
            isConnecting -> stringResource(R.string.status_connecting)
            isConnected -> stringResource(R.string.status_connected)
            else -> stringResource(R.string.status_disconnected)
        }
    val trackColor =
        when {
            isConnected -> SlteColors.current.statusSuccess
            isConnecting -> SlteColors.current.statusWarning

            else -> SlteColors.current.statusNeutral
        }

    val thumbOffset by animateDpAsState(
        targetValue = if (isConnected) Dimens.dashboardToggleThumbOffset else Dimens.dashboardToggleThumbPadding,
        animationSpec = tween(Dimens.dashboardToggleAnimDurationMs),
        label = "toggle_thumb",
    )

    Box(
        modifier =
        Modifier
            .width(Dimens.dashboardToggleWidth)
            .height(Dimens.dashboardToggleHeight)
            .clip(CircleShape)
            .background(trackColor)
            .semantics {
                contentDescription = connectLabel
                stateDescription = statusText
            }
            .toggleable(value = isConnected, role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Box(
            modifier =
            Modifier
                .size(Dimens.dashboardToggleThumbSize)
                .offset { IntOffset(thumbOffset.roundToPx(), Dimens.dashboardToggleThumbPadding.roundToPx()) }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}
