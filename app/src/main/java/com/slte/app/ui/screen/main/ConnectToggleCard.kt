package com.slte.app.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
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
    val haptic = LocalHapticFeedback.current
    val powerBg =
        when {
            isConnected -> Brush.linearGradient(
                listOf(Color(0xFF4BCB1C), Color(0xFF2E7D32)),
            )
            else -> Color(0xFF757575)
        }
    val statusText =
        when {
            isConnecting -> stringResource(R.string.status_connecting)
            isConnected -> stringResource(R.string.status_connected)
            else -> stringResource(R.string.status_disconnected)
        }
    val statusColor =
        when {
            isConnected -> SlteColors.current.statusSuccess
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    SlteCard(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = RoundedCornerShape(24.dp),
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
                Box(
                    modifier =
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(powerBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SlteIcons.Power,
                        contentDescription = statusText,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.dashboardToggleGap))
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Medium,
                    style = SlteType.body,
                    color = statusColor,
                )
            }
        }
    }
}
