// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.ui.theme.V5Radius
import com.slte.app.ui.theme.V5Type

/**
 * Polaris UI v6（iOS 简约风）设计令牌。
 *
 * 由 v5（VmShell 风）整体换皮而来：**数据类形状与字段顺序保持不变**，
 * 只是把色值换成 iOS 语义（#F2F2F7 分组底、#007AFF 蓝、#34C759 绿、#FF3B30 红）。
 * 字段名沿用 v5 命名（accent/ok/danger…），所有调用点零改动即可换肤。
 *
 * 主题沿用 v4 以来的铁律：darkTheme 只允许从 Theme 参数这一个信号源传入，
 * 任何组件不得自行调用 isSystemInDarkTheme() 判断明暗（否则「App 内选择 != 系统」
 * 时会复现白字压白底的整屏不可见事故）。
 *
 * v6 刻意删掉的东西：
 * - 氛围光晕（auroraGlow1/2 置透明）：iOS 分组列表不需要空间光，底色就是纯色。
 * - 渐变主色（accentGrad/okGrad 走近似纯色）：iOS 语言是扁平实色，渐变只留在
 *   个别需要"能量感"的地方（如连接钮在旧版里的做法），v6 连接钮改为白底色环。
 * - 马卡龙瓷片：tile* 改为 iOS 设置图标式的"淡色底 + 实色图标"（tint），
 *   语义映射不变（蓝=额度/下行，橙=已用/上行，绿=就绪，紫=配置，粉=福利，青=线路）。
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
    // 按压水波纹（淡色，iOS 观感 + 安卓触摸反馈）
    val pressed: Color,
    // 文字
    val text: Color,
    val text2: Color,
    val text3: Color,
    // 双主角：蓝 = 主操作/连接前，绿 = 已连接/成功
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
    // 文字 ink 角色（沿用 v5 的可达性结论：亮色用压深墨色保证 ≥4.5:1）
    val okInk: Color,
    val upInk: Color,
    val dangerText: Color,
    val neutralInk: Color,
    val accentInk: Color,
    // 图标 tint（iOS 式淡色底 + 实色图标，不随主题换义）
    val tileBlue: TileColors,
    val tileOrange: TileColors,
    val tileGreen: TileColors,
    val tilePurple: TileColors,
    val tilePink: TileColors,
    val tileCyan: TileColors,
    // 页面氛围光晕（v6 置透明：iOS 不需要）
    val auroraGlow1: Color,
    val auroraGlow2: Color,
    // 卡片投影（iOS 式极淡，亮色 едва 可见、暗色收敛）
    val cardShadow: Color,
)

val LightV5Colors = V5Colors(
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF2F2F7),
    surface3 = Color(0xFFE5E5EA),
    hairline = Color(0x1F3C3C43),
    hairline2 = Color(0x1F3C3C43),
    pressed = Color(0x1A000000),
    text = Color(0xFF1C1C1E),
    text2 = Color(0xFF3A3A3C),
    text3 = Color(0xFF8E8E93),
    accent = Color(0xFF007AFF),
    accent2 = Color(0xFF0A84FF),
    // iOS 是扁平实色语言：渐变字段保留类型兼容，取近似纯色。
    accentGrad = Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF0A84FF))),
    accentBg = Color(0x1F007AFF),
    ok = Color(0xFF34C759),
    ok2 = Color(0xFF30D158),
    okGrad = Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF30D158))),
    okBg = Color(0x1F34C759),
    up = Color(0xFFFF9F0A),
    upBg = Color(0x1FFF9F0A),
    danger = Color(0xFFFF3B30),
    dangerBg = Color(0x1FFF3B30),
    dangerInk = Color(0xFFFFFFFF),
    neutral = Color(0xFF8E8E93),
    neutralBg = Color(0x1F8E8E93),
    // 亮色墨色：对各自淡底 ≥4.5:1（v5 的真机实测结论在 v6 同样成立，色相未变）。
    okInk = Color(0xFF248A3D),
    upInk = Color(0xFFB25E00),
    dangerText = Color(0xFFD70015),
    neutralInk = Color(0xFF6D6D72),
    accentInk = Color(0xFF007AFF),
    // iOS tint：淡底 + 实色图标。
    tileBlue = TileColors(Color(0x1F007AFF), Color(0xFF007AFF)),
    tileOrange = TileColors(Color(0x1FFF9F0A), Color(0xFFE8890B)),
    tileGreen = TileColors(Color(0x1F34C759), Color(0xFF248A3D)),
    tilePurple = TileColors(Color(0x1FAF52DE), Color(0xFFAF52DE)),
    tilePink = TileColors(Color(0x1FFF2D55), Color(0xFFFF2D55)),
    tileCyan = TileColors(Color(0x1F32ADE6), Color(0xFF0A84FF)),
    // v6 无氛围光晕：置透明后 v5Aurora 退化为纯色底，调用点无需改动。
    auroraGlow1 = Color.Transparent,
    auroraGlow2 = Color.Transparent,
    cardShadow = Color(0x14000000),
)

val DarkV5Colors = V5Colors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    surface3 = Color(0xFF3A3A3C),
    hairline = Color(0x4D545458),
    hairline2 = Color(0x4D545458),
    pressed = Color(0x1AFFFFFF),
    text = Color(0xFFFFFFFF),
    text2 = Color(0xFFAEAEB2),
    text3 = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF),
    accent2 = Color(0xFF409CFF),
    accentGrad = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF409CFF))),
    accentBg = Color(0x290A84FF),
    ok = Color(0xFF30D158),
    ok2 = Color(0xFF34C759),
    okGrad = Brush.linearGradient(listOf(Color(0xFF30D158), Color(0xFF34C759))),
    okBg = Color(0x2930D158),
    up = Color(0xFFFF9F0A),
    upBg = Color(0x26FF9F0A),
    danger = Color(0xFFFF453A),
    dangerBg = Color(0x26FF453A),
    dangerInk = Color(0xFF2B0B08),
    neutral = Color(0xFF8E8E93),
    neutralBg = Color(0x248E8E93),
    // 暗色墨色直接沿用语义实色（暗底对比度天然达标，v5 结论不变）。
    okInk = Color(0xFF30D158),
    upInk = Color(0xFFFF9F0A),
    dangerText = Color(0xFFFF6961),
    neutralInk = Color(0xFFAEAEB2),
    accentInk = Color(0xFF0A84FF),
    tileBlue = TileColors(Color(0x290A84FF), Color(0xFF409CFF)),
    tileOrange = TileColors(Color(0x26FF9F0A), Color(0xFFFFB340)),
    tileGreen = TileColors(Color(0x2430D158), Color(0xFF30D158)),
    tilePurple = TileColors(Color(0x2BBF5AF2), Color(0xFFBF5AF2)),
    tilePink = TileColors(Color(0x24FF375F), Color(0xFFFF6482)),
    tileCyan = TileColors(Color(0x2164D2FF), Color(0xFF64D2FF)),
    auroraGlow1 = Color.Transparent,
    auroraGlow2 = Color.Transparent,
    cardShadow = Color(0x00000000),
)

val LocalV5Colors = staticCompositionLocalOf { LightV5Colors }

object V5ThemeColors {
    val current: V5Colors
        @Composable get() = LocalV5Colors.current
}

/**
 * v6 底部面板的公共形状与标题字号。
 *
 * iOS sheet 语言是「小圆角 + 顶部抓手」，不再是 v5 的 26dp 大圆角。
 */
val V5SheetShape = RoundedCornerShape(topStart = V5Radius.r16, topEnd = V5Radius.r16)

/** 见 [V5SheetShape]：iOS sheet 标题 17sp 半粗居中。 */
val V5SheetTitleStyle = TextStyle(fontSize = V5Type.sp17, fontWeight = FontWeight.SemiBold)

/*
 * v5 的 `@Composable fun V5Theme(darkTheme, content)` 独立主题入口已在 v5 删除，
 * v6 同样不恢复：实际的主题所有者是 Theme.kt 的 `SlteTheme`，
 * 后者负责按单一信号源下发 `LocalV5Colors`。v6 取色仍由
 * `LocalV5Colors` + `V5ThemeColors.current` 提供。
 */
