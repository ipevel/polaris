// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora Polaris 顶部氛围光：与 home-dark/light.html mockup 对齐。
 *
 * 暗色：cyan 10% (左上) + purple 8% (右上) 叠在 #070B16 上
 * 亮色：cyan 8% + purple 6% 叠在 #F5F6F8 上（更克制，避免在白底上过亮）
 *
 * 使用：在 Scaffold(Modifier.slteAuroraBackground(), containerColor = Color.Transparent)
 * 整页会渲染顶部极光氛围；卡片自身仍用 SlteCard 的垂直渐变 + hairline。
 */
@Composable
fun Modifier.slteAuroraBackground(): Modifier {
    val dark = isSystemInDarkTheme()
    val baseBg = if (dark) Color(0xFF070B16) else Color(0xFFF5F6F8)
    val cyanGlow = if (dark) Color(0x1A5DD4F5) else Color(0x140284C7)
    val purpleGlow = if (dark) Color(0x14C084FC) else Color(0x0F7C3AED)
    return this.drawBehind {
        drawRect(color = baseBg)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(cyanGlow, Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * -0.06f),
                radius = size.maxDimension * 0.55f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(purpleGlow, Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.04f),
                radius = size.maxDimension * 0.5f,
            ),
        )
    }
}
