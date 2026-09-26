// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.V5CardFlat
import kotlin.math.cos
import kotlin.math.sin

/** 应用标识卡（v5）：静态极星标识 + 应用名 + 一句话说明。 */
@Composable
internal fun AboutIdentityCard(
    appName: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutBrandMark(markSize = 68.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = appName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = c.text3,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 极星标识：轨道环 + 四角星，纯 Compose 绘制（不引入图片资源），取色走 v5 令牌。 */
@Composable
internal fun AboutBrandMark(
    markSize: Dp,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    Canvas(modifier = modifier.size(markSize)) {
        drawPolarisMark(accent = c.accent, ring = c.text3)
    }
}

internal fun DrawScope.drawPolarisMark(
    accent: Color,
    ring: Color,
) {
    val radius = size.minDimension / 2f
    val center = center
    drawCircle(
        color = ring.copy(alpha = 0.45f),
        radius = radius * 0.9f,
        center = center,
        style = Stroke(width = radius * 0.07f),
    )
    rotate(degrees = -30f, pivot = center) {
        drawOval(
            color = ring.copy(alpha = 0.3f),
            topLeft = Offset(center.x - radius, center.y - radius * 0.44f),
            size = Size(radius * 2f, radius * 0.88f),
            style = Stroke(width = radius * 0.06f),
        )
    }
    drawPath(path = polarisStarPath(center = center, radius = radius * 0.44f), color = accent)
    drawPath(
        path =
        polarisStarPath(
            center = Offset(center.x + radius * 0.6f, center.y - radius * 0.56f),
            radius = radius * 0.14f,
        ),
        color = accent.copy(alpha = 0.7f),
    )
}

internal fun polarisStarPath(
    center: Offset,
    radius: Float,
): Path {
    val path = Path()
    val inner = radius * 0.3f
    repeat(8) { index ->
        val r = if (index % 2 == 0) radius else inner
        val angle = Math.toRadians(index * 45.0 - 90.0)
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
