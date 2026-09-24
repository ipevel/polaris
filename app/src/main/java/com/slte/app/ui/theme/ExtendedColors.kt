// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ExtendedColors(

    val accentInteractive: Color,
    val accentInteractiveBg: Color,

    val textSelectionBg: Color,

    /** 星辉金：品牌辅助色，用于四 Tab 激活指示等强调点。 */
    val brandGold: Color,
    val brandGoldBg: Color,

    val statusSuccess: Color,
    val statusSuccessBg: Color,
    val statusWarning: Color,
    val statusWarningBg: Color,
    val statusDanger: Color,
    val statusDangerBg: Color,
    val statusNeutral: Color,
    val statusNeutralBg: Color,

    val statusInfo: Color,
    val statusSlow: Color,
)

// Aurora Polaris 品牌辅助色：暗色 cyan #5DD4F5 / gold #FFC857 / green #4ADE80
private val AuroraCyanDark = Color(0xFF5DD4F5)
private val AuroraCyanLight = Color(0xFF0284C7)

val LightExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanLight,
        accentInteractiveBg = Color(0x1A0284C7),
        textSelectionBg = Color(0x660284C7),
        brandGold = Color(0xFFB45309),
        brandGoldBg = Color(0x1AB45309),
        statusSuccess = Color(0xFF059669),
        statusSuccessBg = Color(0x1F059669),
        statusWarning = Color(0xFFB45309),
        statusWarningBg = Color(0x1AB45309),
        statusDanger = md_light_error,
        statusDangerBg = Color(0x1ADC2626),
        statusNeutral = Color(0xFF64748B),
        statusNeutralBg = Color(0x1464748B),
        statusInfo = Color(0xFF0284C7),
        statusSlow = Color(0xFFB45309),
    )

val DarkExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanDark,
        accentInteractiveBg = Color(0x295DD4F5),
        textSelectionBg = Color(0x665DD4F5),
        brandGold = Color(0xFFFFC857),
        brandGoldBg = Color(0x29FFC857),
        statusSuccess = Color(0xFF4ADE80),
        statusSuccessBg = Color(0x294ADE80),
        statusWarning = Color(0xFFFFC857),
        statusWarningBg = Color(0x29FFC857),
        statusDanger = md_dark_error,
        statusDangerBg = Color(0x24F87171),
        statusNeutral = Color(0xFF6B7B99),
        statusNeutralBg = Color(0x146B7B99),
        statusInfo = Color(0xFF5DD4F5),
        statusSlow = Color(0xFFFFC857),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
