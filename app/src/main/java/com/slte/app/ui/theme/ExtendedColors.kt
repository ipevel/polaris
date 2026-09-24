// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ExtendedColors(

    val accentInteractive: Color,
    val accentInteractiveBg: Color,

    val textSelectionBg: Color,

    /** 星辉金：只作「上行数据」序列色与警示色使用，不再出现在按钮/徽标上（v4 收敛强调色）。 */
    val brandGold: Color,
    val brandGoldBg: Color,

    /**
     * 页面极光氛围：底色 + 两处径向光晕。
     *
     * 这三个值必须由 SlteTheme 按**实际生效的主题**下发。此前 SlteAuroraBackground 自行调用
     * isSystemInDarkTheme()，在「App 内选深色 + 系统为浅色」时会画出浅色底，而 colorScheme 已是
     * 深色（onSurface 为白字），导致整屏文字不可见。
     */
    val auroraBase: Color,
    val auroraGlowPrimary: Color,
    val auroraGlowSecondary: Color,

    /** 卡片垂直渐变与顶部发丝线；同理必须随主题单源，不得自行判断明暗。 */
    val cardTop: Color,
    val cardBottom: Color,
    val cardHairline: Color,

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

// 北极星 Polaris 亮色：强调 cyan #0A6EB4 / 上行金 #B0740C / 成功 #0E7C5A
private val AuroraCyanDark = Color(0xFF59CDF2)
private val AuroraCyanLight = Color(0xFF0A6EB4)

val LightExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanLight,
        accentInteractiveBg = Color(0x1A0A6EB4),
        textSelectionBg = Color(0x660A6EB4),
        brandGold = Color(0xFFB0740C),
        brandGoldBg = Color(0x1AB0740C),
        auroraBase = Color(0xFFEEF2F7),
        auroraGlowPrimary = Color(0x1A0A6EB4),
        auroraGlowSecondary = Color(0x0E6040BE),
        cardTop = Color(0xFFFFFFFF),
        cardBottom = Color(0xFFF7FAFC),
        cardHairline = Color(0x140C1524),
        statusSuccess = Color(0xFF0E7C5A),
        statusSuccessBg = Color(0x1F0E7C5A),
        statusWarning = Color(0xFFB0740C),
        statusWarningBg = Color(0x1AB0740C),
        statusDanger = md_light_error,
        statusDangerBg = Color(0x1AC0342B),
        statusNeutral = Color(0xFF64748B),
        statusNeutralBg = Color(0x1464748B),
        statusInfo = Color(0xFF0A6EB4),
        statusSlow = Color(0xFFB0740C),
    )

val DarkExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanDark,
        accentInteractiveBg = Color(0x2459CDF2),
        textSelectionBg = Color(0x6659CDF2),
        brandGold = Color(0xFFF2BC63),
        brandGoldBg = Color(0x24F2BC63),
        auroraBase = Color(0xFF070A12),
        auroraGlowPrimary = Color(0x1C59CDF2),
        auroraGlowSecondary = Color(0x12906EF0),
        cardTop = Color(0xFF111B2A),
        cardBottom = Color(0xFF0D1522),
        cardHairline = Color(0x1AFFFFFF),
        statusSuccess = Color(0xFF4CD394),
        statusSuccessBg = Color(0x244CD394),
        statusWarning = Color(0xFFF2BC63),
        statusWarningBg = Color(0x24F2BC63),
        statusDanger = md_dark_error,
        statusDangerBg = Color(0x24F4776D),
        statusNeutral = Color(0xFF8A9CB4),
        statusNeutralBg = Color(0x148A9CB4),
        statusInfo = Color(0xFF59CDF2),
        statusSlow = Color(0xFFF2BC63),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
