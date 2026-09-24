// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
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
import com.slte.app.ui.theme.LocalExtendedColors
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.utils.Dimens

/**
 * 卡片：垂直渐变 + 顶部 1dp 发丝线，0 elevation。
 *
 * 渐变与发丝线取自 [LocalExtendedColors]，与 colorScheme 同源。**不要**在此处调用
 * isSystemInDarkTheme()：App 内主题偏好与系统设置可以不一致，自行判断会让卡片在深色
 * colorScheme 下依然画成浅色，与 onSurface 的白字撞在一起。
 */
@Composable
fun SlteCard(
    modifier: Modifier = Modifier,
    shape: Shape = SlteShapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = LocalExtendedColors.current
    val cardModifier = modifier
        .background(Brush.verticalGradient(listOf(extended.cardTop, extended.cardBottom)), shape)
        .drawWithContent {
            drawContent()
            val brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, extended.cardHairline, Color.Transparent),
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
