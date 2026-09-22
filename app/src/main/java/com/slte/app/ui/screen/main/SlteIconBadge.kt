// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.slte.app.ui.theme.SlteColors
import com.slte.app.utils.Dimens

@Composable
fun SlteIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = SlteColors.current.accentInteractive,
    background: Color = SlteColors.current.accentInteractiveBg,
    size: androidx.compose.ui.unit.Dp = Dimens.iconBadgeSize,
) {
    Box(
        modifier =
        modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.iconBadgeRadius))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(Dimens.icon.md),
            tint = tint,
        )
    }
}

/** Anywhere 风格彩色渐变图标徽标：渐变圆角底 + 白色图标。 */
@Composable
fun ColorfulIconBadge(
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: androidx.compose.ui.unit.Dp = Dimens.iconBadgeSize,
) {
    Box(
        modifier =
        modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.iconBadgeRadius))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(Dimens.icon.md),
            tint = Color.White,
        )
    }
}
