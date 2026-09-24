// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SlteTypography =
    Typography(
        displayLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        labelLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )

object SlteType {
    val caption =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        )
    val label =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        )
    val bodySmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.3.sp,
        )
    val body =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        )
    val field =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        )
    val title =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        )
    val heading =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        )
    val display =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        )

    val pageTitle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        )

    /** 区块标题：11sp + 大字距，与页标题明确分层（原实现页标题与区块标题同为 16sp 撞级）。 */
    val sectionLabel =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.1.sp,
        )

    /** 卡标题：卡片内的主标题。 */
    val cardTitle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.2.sp,
        )

    /**
     * 数据值：所有数值（速率/流量/时长/金额/百分比/IP）统一走等宽 + tabular 数字，
     * 避免同列数字宽度抖动、小数点不对齐（原实现只有 IP 用了 Monospace）。
     */
    val value =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = "tnum",
        )

    val valueSmall =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.1).sp,
            fontFeatureSettings = "tnum",
        )
}

object TextSizes {
    val flagFontSize = 12.sp
    val htmlBaseFontSize = 14.sp
}
