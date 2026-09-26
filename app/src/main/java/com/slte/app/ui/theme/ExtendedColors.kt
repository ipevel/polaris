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

// v5（VmShell）令牌映射：ExtendedColors 的字段语义保留，色值全部对齐
// V5Theme 的调色板（双主角蓝绿 + 马卡龙瓷片），保证尚未重构为 v5 布局的
// 页面（购买流程、工单详情等）与 v5 原生页面视觉一致。
private val AuroraCyanDark = Color(0xFF6E97FF)
private val AuroraCyanLight = Color(0xFF2F6BF6)

val LightExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanLight,
        accentInteractiveBg = Color(0x1A2F6BF6),
        textSelectionBg = Color(0x662F6BF6),
        brandGold = Color(0xFFEE8A2C),
        brandGoldBg = Color(0x21EE8A2C),
        auroraBase = Color(0xFFF1F0F6),
        auroraGlowPrimary = Color(0x136366F1),
        auroraGlowSecondary = Color(0x0BEC4899),
        cardTop = Color(0xFFFFFFFF),
        cardBottom = Color(0xFFFFFFFF),
        cardHairline = Color(0xFFE8E6EF),
        statusSuccess = Color(0xFF16AC6C),
        statusSuccessBg = Color(0x2116AC6C),
        statusWarning = Color(0xFFEE8A2C),
        statusWarningBg = Color(0x21EE8A2C),
        // 原本引用 Color.kt 的 md_light_error；该文件是 v4 遗留（48 个令牌里 46 个已无引用），
        // 已整体删除，这里把用到的值就地内联。取值保持不变，纯搬家。
        statusDanger = Color(0xFFC0342B),
        statusDangerBg = Color(0x1CE5484D),
        statusNeutral = Color(0xFF6B7280),
        statusNeutralBg = Color(0x1A6B7280),
        statusInfo = Color(0xFF2F6BF6),
        statusSlow = Color(0xFFEE8A2C),
    )

val DarkExtendedColors =
    ExtendedColors(
        accentInteractive = AuroraCyanDark,
        accentInteractiveBg = Color(0x296E97FF),
        textSelectionBg = Color(0x666E97FF),
        brandGold = Color(0xFFF5A25B),
        brandGoldBg = Color(0x26F5A25B),
        auroraBase = Color(0xFF0B0D15),
        auroraGlowPrimary = Color(0x1F6366F1),
        auroraGlowSecondary = Color(0x0FEC4899),
        cardTop = Color(0xFF171A25),
        cardBottom = Color(0xFF171A25),
        cardHairline = Color(0x14FFFFFF),
        statusSuccess = Color(0xFF3DCC8E),
        statusSuccessBg = Color(0x293DCC8E),
        statusWarning = Color(0xFFF5A25B),
        statusWarningBg = Color(0x26F5A25B),
        // 同上：原 md_dark_error（其值与 v5 的 danger #F4776D 相同）
        statusDanger = Color(0xFFF4776D),
        statusDangerBg = Color(0x26F4776D),
        statusNeutral = Color(0xFF8A9CB4),
        statusNeutralBg = Color(0x248A9CB4),
        statusInfo = Color(0xFF6E97FF),
        statusSlow = Color(0xFFF5A25B),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
