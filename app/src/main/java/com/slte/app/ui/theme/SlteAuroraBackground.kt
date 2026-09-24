// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora Polaris 顶部氛围光：底色 + cyan / purple 两处径向光晕。
 *
 * 颜色全部取自 [LocalExtendedColors]，由 SlteTheme 按实际生效的主题下发。
 * **不要**在此处调用 isSystemInDarkTheme()：App 内主题偏好与系统设置可以不一致，
 * 自行判断会出现「深色 colorScheme + 浅色背景」的组合，导致 onSurface 的白字压在浅底上不可见。
 *
 * 使用：在 Scaffold(Modifier.slteAuroraBackground(), containerColor = Color.Transparent)
 * 整页会渲染顶部极光氛围；卡片自身仍用 SlteCard 的垂直渐变 + hairline。
 */
@Composable
fun Modifier.slteAuroraBackground(): Modifier {
    val colors = LocalExtendedColors.current
    return this.drawBehind {
        drawRect(color = colors.auroraBase)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.auroraGlowPrimary, Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * -0.06f),
                radius = size.maxDimension * 0.55f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.auroraGlowSecondary, Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.04f),
                radius = size.maxDimension * 0.5f,
            ),
        )
    }
}
