// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

enum class SlteButtonStyle {

    Primary,

    Secondary,

    Neutral,

    Tonal,

    Medium,

    Danger,
}

@Composable
fun SlteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SlteButtonStyle = SlteButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp? = null,
    height: Dp? = null,
    aurora: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val presetHeight =
        when (style) {
            SlteButtonStyle.Medium -> Dimens.size.buttonMd
            SlteButtonStyle.Tonal -> Dimens.size.row
            else -> Dimens.size.button
        }
    val effHeight = height ?: presetHeight
    // Aurora 模式：primary→tertiary 垂直渐变 + 同色光晕
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val auroraModifier =
        if (aurora && enabled) {
            modifier
                .shadow(
                    elevation = 8.dp,
                    shape = SlteShapes.medium,
                    ambientColor = primary.copy(alpha = 0.4f),
                    spotColor = primary.copy(alpha = 0.6f),
                )
                .background(
                    brush = Brush.verticalGradient(listOf(primary, tertiary)),
                    shape = SlteShapes.medium,
                )
        } else {
            modifier
        }
    val effContainer =
        if (aurora && enabled) {
            Color.Transparent
        } else if (containerColor != null) {
            containerColor
        } else {
            when (style) {
                SlteButtonStyle.Secondary, SlteButtonStyle.Neutral -> Color.Transparent
                SlteButtonStyle.Tonal -> MaterialTheme.colorScheme.surface
                SlteButtonStyle.Danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
        }
    val effContent =
        contentColor ?: when (style) {
            SlteButtonStyle.Secondary, SlteButtonStyle.Tonal -> MaterialTheme.colorScheme.primary
            SlteButtonStyle.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            SlteButtonStyle.Danger -> MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.onPrimary
        }
    val effBorder =
        borderColor?.let { BorderStroke(borderWidth ?: Dimens.strokeMedium, it) }
            ?: when (style) {
                SlteButtonStyle.Secondary ->
                    BorderStroke(Dimens.strokeMedium, MaterialTheme.colorScheme.primary)
                SlteButtonStyle.Neutral ->
                    BorderStroke(Dimens.dividerThickness, MaterialTheme.colorScheme.outline)
                else -> null
            }
    val action = {
        if (enabled && !loading) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    }
    val textStyle =
        if (style == SlteButtonStyle.Medium) {
            SlteType.body.copy(fontWeight = FontWeight.Medium)
        } else {
            SlteType.field
        }
    val contentPadding =
        if (style == SlteButtonStyle.Medium) {
            PaddingValues(horizontal = Dimens.gap.lg)
        } else {
            ButtonDefaults.ContentPadding
        }
    val content: @Composable () -> Unit = {
        if (loading) {
            LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
        } else {
            Text(text = text, style = textStyle)
        }
    }

    if (effBorder != null) {
        OutlinedButton(
            onClick = action,
            enabled = enabled,
            modifier = auroraModifier.height(effHeight),
            shape = SlteShapes.medium,
            contentPadding = contentPadding,
            colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = effContainer,
                contentColor = effContent,
            ),
            border = effBorder,
        ) { content() }
    } else {
        Button(
            onClick = action,
            enabled = enabled,
            modifier = auroraModifier.height(effHeight),
            shape = SlteShapes.medium,
            contentPadding = contentPadding,
            colors =
            ButtonDefaults.buttonColors(
                containerColor = effContainer,
                contentColor = effContent,
            ),
        ) { content() }
    }
}
