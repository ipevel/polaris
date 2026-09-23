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

private val BrandGreenLight = Color(0xFF4BCB1C)
private val BrandGreenDark = Color(0xFF6DC26D)

private val BrandBlueDark = Color(0xFF60A5FA)

val LightExtendedColors =
    ExtendedColors(
        accentInteractive = md_light_primary,
        accentInteractiveBg = Color(0x1A1474C6),
        textSelectionBg = Color(0x661474C6),
        brandGold = Color(0xFFD97706),
        brandGoldBg = Color(0xFFFEF3C7),
        statusSuccess = BrandGreenLight,
        statusSuccessBg = Color(0x1A4BCB1C),
        statusWarning = Color(0xFFFFAB40),
        statusWarningBg = Color(0x1AFFAB40),
        statusDanger = md_light_error,
        statusDangerBg = md_light_errorContainer,
        statusNeutral = Color(0xFF999999),
        statusNeutralBg = Color(0x14999999),
        statusInfo = Color(0xFF2196F3),
        statusSlow = Color(0xFFFFC107),
    )

val DarkExtendedColors =
    ExtendedColors(
        accentInteractive = BrandBlueDark,
        accentInteractiveBg = Color(0x1A60A5FA),
        textSelectionBg = Color(0x6660A5FA),
        brandGold = Color(0xFFFBBF24),
        brandGoldBg = Color(0x1FFBBF24),
        statusSuccess = BrandGreenDark,
        statusSuccessBg = Color(0x1A6DC26D),
        statusWarning = Color(0xFFFFCC80),
        statusWarningBg = Color(0x1AFFCC80),
        statusDanger = md_dark_error,
        statusDangerBg = md_dark_errorContainer,
        statusNeutral = md_dark_onSurfaceVariant,
        statusNeutralBg = Color(0x14A8A8A8),
        statusInfo = Color(0xFF64B5F6),
        statusSlow = Color(0xFFFFD54F),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
