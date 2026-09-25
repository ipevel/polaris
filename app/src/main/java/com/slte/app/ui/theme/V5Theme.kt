// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Polaris UI v5（VmShell 风）设计令牌。
 *
 * 主题沿用 v4 以来的铁律：darkTheme 只允许从 Theme 参数这一个信号源传入，
 * 任何组件不得自行调用 isSystemInDarkTheme() 判断明暗（否则「App 内选择 != 系统」
 * 时会复现白字压白底的整屏不可见事故）。
 */

data class TileColors(val bg: Color, val ink: Color)

data class V5Colors(
    // 表面阶梯
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val hairline2: Color,
    // 文字
    val text: Color,
    val text2: Color,
    val text3: Color,
    // 双主角：蓝 = 连接前/主操作，绿 = 已连接/成功
    val accent: Color,
    val accent2: Color,
    val accentGrad: Brush,
    val accentBg: Color,
    val ok: Color,
    val ok2: Color,
    val okGrad: Brush,
    val okBg: Color,
    // 状态
    val up: Color,
    val upBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val dangerInk: Color,
    val neutral: Color,
    val neutralBg: Color,
    // 马卡龙瓷片（颜色语义固定，不随主题换义）
    val tileBlue: TileColors,
    val tileOrange: TileColors,
    val tileGreen: TileColors,
    val tilePurple: TileColors,
    val tilePink: TileColors,
    val tileCyan: TileColors,
    // 悬浮胶囊导航
    val navBg: Color,
    val navOn: Color,
    // 页面氛围光晕
    val auroraGlow1: Color,
    val auroraGlow2: Color,
    // 卡片投影（亮色明显、暗色收敛）
    val cardShadow: Color,
)

val LightV5Colors = V5Colors(
    bg = Color(0xFFF1F0F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF6F5FA),
    surface3 = Color(0xFFECEBF2),
    hairline = Color(0xFFE8E6EF),
    hairline2 = Color(0xFFEFEDF5),
    text = Color(0xFF191C26),
    text2 = Color(0xFF575E72),
    text3 = Color(0xFF8E95A8),
    accent = Color(0xFF2F6BF6),
    accent2 = Color(0xFF5E8DFF),
    accentGrad = Brush.linearGradient(listOf(Color(0xFF5E8DFF), Color(0xFF2B5FF0))),
    accentBg = Color(0x1A2F6BF6),
    ok = Color(0xFF16AC6C),
    ok2 = Color(0xFF3FD994),
    okGrad = Brush.linearGradient(listOf(Color(0xFF43D69A), Color(0xFF12A868))),
    okBg = Color(0x2116AC6C),
    up = Color(0xFFEE8A2C),
    upBg = Color(0x21EE8A2C),
    danger = Color(0xFFE5484D),
    dangerBg = Color(0x1CE5484D),
    dangerInk = Color(0xFFFFFFFF),
    neutral = Color(0xFF6B7280),
    neutralBg = Color(0x1A6B7280),
    tileBlue = TileColors(Color(0xFFEAF1FF), Color(0xFF2F6BF6)),
    tileOrange = TileColors(Color(0xFFFEF1E2), Color(0xFFE8802A)),
    tileGreen = TileColors(Color(0xFFE5F8EE), Color(0xFF149E63)),
    tilePurple = TileColors(Color(0xFFF1ECFE), Color(0xFF8253F0)),
    tilePink = TileColors(Color(0xFFFCEBF5), Color(0xFFD84FB4)),
    tileCyan = TileColors(Color(0xFFE4F6FB), Color(0xFF119FC7)),
    navBg = Color(0xE6FFFFFF),
    navOn = Color(0xFFEFF1F7),
    auroraGlow1 = Color(0x136366F1),
    auroraGlow2 = Color(0x0BEC4899),
    cardShadow = Color(0x2E262E50),
)

val DarkV5Colors = V5Colors(
    bg = Color(0xFF0B0D15),
    surface = Color(0xFF171A25),
    surface2 = Color(0xFF1F2331),
    surface3 = Color(0xFF292E40),
    hairline = Color(0x14FFFFFF),
    hairline2 = Color(0x0DFFFFFF),
    text = Color(0xFFEDF0F8),
    text2 = Color(0xFFA7AEC2),
    text3 = Color(0xFF727A90),
    accent = Color(0xFF6E97FF),
    accent2 = Color(0xFF8FB2FF),
    accentGrad = Brush.linearGradient(listOf(Color(0xFF5E85F8), Color(0xFF3E5FE0))),
    accentBg = Color(0x296E97FF),
    ok = Color(0xFF3DCC8E),
    ok2 = Color(0xFF5BE0A8),
    okGrad = Brush.linearGradient(listOf(Color(0xFF2FB37C), Color(0xFF158A57))),
    okBg = Color(0x293DCC8E),
    up = Color(0xFFF5A25B),
    upBg = Color(0x26F5A25B),
    danger = Color(0xFFF4776D),
    dangerBg = Color(0x26F4776D),
    dangerInk = Color(0xFF2B0B08),
    neutral = Color(0xFF8A9CB4),
    neutralBg = Color(0x248A9CB4),
    tileBlue = TileColors(Color(0x266E97FF), Color(0xFF93B4FF)),
    tileOrange = TileColors(Color(0x26F5A25B), Color(0xFFF7B177)),
    tileGreen = TileColors(Color(0x243DCC8E), Color(0xFF5ED9A6)),
    tilePurple = TileColors(Color(0x2B966EF5), Color(0xFFB49AF8)),
    tilePink = TileColors(Color(0x24E966C4), Color(0xFFF093D4)),
    tileCyan = TileColors(Color(0x2138BDF8), Color(0xFF6FD0F2)),
    navBg = Color(0xEB171A25),
    navOn = Color(0xFF2A3042),
    auroraGlow1 = Color(0x1F6366F1),
    auroraGlow2 = Color(0x0FEC4899),
    cardShadow = Color(0x14000000),
)

val LocalV5Colors = staticCompositionLocalOf { LightV5Colors }

object V5ThemeColors {
    val current: V5Colors
        @Composable get() = LocalV5Colors.current
}

private val LightV5Scheme = lightColorScheme(
    primary = Color(0xFF2F6BF6),
    onPrimary = Color.White,
    background = Color(0xFFF1F0F6),
    onBackground = Color(0xFF191C26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C26),
    surfaceVariant = Color(0xFFF6F5FA),
    onSurfaceVariant = Color(0xFF575E72),
    outline = Color(0xFFE8E6EF),
    outlineVariant = Color(0xFFEFEDF5),
    error = Color(0xFFE5484D),
    secondary = Color(0xFF16AC6C),
    tertiary = Color(0xFFEE8A2C),
)

private val DarkV5Scheme = darkColorScheme(
    primary = Color(0xFF6E97FF),
    onPrimary = Color.White,
    background = Color(0xFF0B0D15),
    onBackground = Color(0xFFEDF0F8),
    surface = Color(0xFF171A25),
    onSurface = Color(0xFFEDF0F8),
    surfaceVariant = Color(0xFF1F2331),
    onSurfaceVariant = Color(0xFFA7AEC2),
    outline = Color(0x14FFFFFF),
    outlineVariant = Color(0x0DFFFFFF),
    error = Color(0xFFF4776D),
    secondary = Color(0xFF3DCC8E),
    tertiary = Color(0xFFF5A25B),
)

@Composable
fun V5Theme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkV5Colors else LightV5Colors
    val scheme = if (darkTheme) DarkV5Scheme else LightV5Scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.decorView.setBackgroundColor(colors.bg.toArgb())
        }
    }

    CompositionLocalProvider(LocalV5Colors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
