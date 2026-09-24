// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.utils.Dimens

// Aurora 卡片渐变：暗色 surface→bg-2，亮色 surface→surface-2
private val AuroraCardGradientDark = listOf(Color(0xFF182241), Color(0xFF131C30))
private val AuroraCardGradientLight = listOf(Color(0xFFFFFFFF), Color(0xFFF5F7FA))

// 卡片顶部高光线：暗色 10% 白，亮色 8% 近黑（提亮以保可见性）
private val AuroraHairlineDark = Color(0x1AFFFFFF)
private val AuroraHairlineLight = Color(0x140F172A)

@Composable
fun SlteCard(
    modifier: Modifier = Modifier,
    shape: Shape = SlteShapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val gradientColors = if (dark) AuroraCardGradientDark else AuroraCardGradientLight
    val hairline = if (dark) AuroraHairlineDark else AuroraHairlineLight
    val cardModifier = modifier
        .background(Brush.verticalGradient(gradientColors), shape)
        .drawWithContent {
            drawContent()
            val brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, hairline, Color.Transparent),
            )
            drawRect(
                brush = brush,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, 1.dp.toPx()),
            )
        }
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)

    if (onClick == null) {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content,
        )
    } else {
        val haptic = LocalHapticFeedback.current
        Card(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content,
        )
    }
}
