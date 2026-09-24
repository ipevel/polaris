// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.theme

import androidx.compose.ui.graphics.Color

// 北极星 Polaris 亮色：底 #EEF2F7（比卡面略冷，让白卡浮起来）/ 卡面 #FFFFFF / 强调 #0A6EB4
// 强调色只有一支（cyan）；金色降到 ExtendedColors.brandGold 作为「上行数据」序列色，不再出现在按钮与徽标上；
// tertiary 与 primary 同源，界面里不再出现紫色（原「续费」的 primary→tertiary 紫渐变已移除）。
val md_light_primary = Color(0xFF0A6EB4)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFE3EFF9)
val md_light_onPrimaryContainer = Color(0xFF07304F)
val md_light_secondary = Color(0xFF51617A)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFE7EDF4)
val md_light_onSecondaryContainer = Color(0xFF3C4A60)
val md_light_tertiary = Color(0xFF0A6EB4)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFE3EFF9)
val md_light_onTertiaryContainer = Color(0xFF07304F)
val md_light_error = Color(0xFFC0342B)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFBE9E7)
val md_light_onErrorContainer = Color(0xFF7A1A14)
val md_light_background = Color(0xFFEEF2F7)
val md_light_onBackground = Color(0xFF0C1524)
val md_light_surface = Color(0xFFFFFFFF)
val md_light_onSurface = Color(0xFF0C1524)
val md_light_surfaceVariant = Color(0xFFF3F7FB)
val md_light_onSurfaceVariant = Color(0xFF51617A)
val md_light_outline = Color(0xFFDCE4ED)
val md_light_outlineVariant = Color(0xFFE9EFF6)

// 北极星 Polaris 暗色：底 #070A12 / 卡面 #111B2A / 强调 #59CDF2
// 表面分三级抬升（底 → 卡面 #111B2A → 卡内槽 #1A2638 → 轨道 #22303F），文字对比度全部达标
val md_dark_primary = Color(0xFF59CDF2)
val md_dark_onPrimary = Color(0xFF04202D)
val md_dark_primaryContainer = Color(0xFF1A3050)
val md_dark_onPrimaryContainer = Color(0xFFC7D6E8)
val md_dark_secondary = Color(0xFF8A9CB4)
val md_dark_onSecondary = Color(0xFF0B1420)
val md_dark_secondaryContainer = Color(0xFF24344A)
val md_dark_onSecondaryContainer = Color(0xFFC7D6E8)
val md_dark_tertiary = Color(0xFF59CDF2)
val md_dark_onTertiary = Color(0xFF04202D)
val md_dark_tertiaryContainer = Color(0xFF1A3050)
val md_dark_onTertiaryContainer = Color(0xFFC7D6E8)
val md_dark_error = Color(0xFFF4776D)
val md_dark_onError = Color(0xFF2B0B08)
val md_dark_errorContainer = Color(0xFF3A1614)
val md_dark_onErrorContainer = Color(0xFFF4776D)
val md_dark_background = Color(0xFF070A12)
val md_dark_onBackground = Color(0xFFEDF3FB)
val md_dark_surface = Color(0xFF111B2A)
val md_dark_onSurface = Color(0xFFEDF3FB)
val md_dark_surfaceVariant = Color(0xFF1A2638)
val md_dark_onSurfaceVariant = Color(0xFFA2B3C8)
val md_dark_outline = Color(0xFF2A3A50)
val md_dark_outlineVariant = Color(0xFF22303F)
