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
    // 文字 ink 角色（第 13 轮真机复核新增）
    //
    // 为什么必须单开一档：ok/up/danger/neutral/accent 是**语义实色**，同一个值同时承担
    // 「图形色」与「文字 ink」两个角色，而两者阈值不同——图形只要 3:1（WCAG 1.4.11），
    // 文字要 4.5:1（1.4.3）。一个值满足不了两种阈值，于是亮色主题下 5 个芯片、6 个瓷片
    // 的墨色全部低于 AA（真机实测 2.26~4.27:1）。
    //
    // 且**调底色救不了**：#16AC6C 自身亮度 L=0.303，与任何底色的对比度上限只有 2.97:1；
    // #E8802A（L=0.327）上限 2.78:1 —— 只能压深墨色。
    //
    // 亮色填达标值、暗色沿用既已达标的实色（暗色瓷片 5.93~7.84:1，无需改动）。
    // 图形角色（连接钮光环、瓷片呼吸点、开关轨道）继续用 ok/up/danger/accent，不受影响。
    val okInk: Color,
    val upInk: Color,
    val dangerText: Color,
    val neutralInk: Color,
    val accentInk: Color,
    // 马卡龙瓷片（颜色语义固定，不随主题换义）
    val tileBlue: TileColors,
    val tileOrange: TileColors,
    val tileGreen: TileColors,
    val tilePurple: TileColors,
    val tilePink: TileColors,
    val tileCyan: TileColors,
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
    // 三档灰度是"文字阶梯"，但 text3 承担的是**可读的次要信息**（日期、明细标签、字段说明），
    // 不是装饰性的禁用态。原值 #8E95A8 在白卡上只有 2.99:1，低于 WCAG AA 小字 4.5:1——
    // 第 12 轮的视觉复核在订单/套餐/公告多页都量到了同一处偏低。这里提到 #6B7385：
    // 白卡上 4.76:1 达标，同时仍明显弱于 text2，层级没有被压平。
    text3 = Color(0xFF6B7385),
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
    // 文字 ink：真机实测达标值（对各自芯片/瓷片底色 4.52~4.57:1）
    okInk = Color(0xFF107C4E), // 原 ok #16AC6C 对芯片底 2.57:1
    upInk = Color(0xFFA15E1E), // 原 up #EE8A2C 对芯片底 2.26:1
    dangerText = Color(0xFFC13D41), // 原 danger #E5484D 对芯片底 3.40:1、对页面底 3.36:1
    neutralInk = Color(0xFF676E7C), // 原 neutral #6B7280 对芯片底 4.27:1
    accentInk = Color(0xFF2C64E5), // 原 accent #2F6BF6 对芯片底 4.04:1
    // 瓷片墨色：原值与语义实色同值，实测全部低于 AA（BLUE 4.07 / ORANGE 2.50 / GREEN 3.11
    // / PURPLE 4.09 / PINK 3.22 / CYAN 2.77）。瓷片底色不动，只压深墨色以保住马卡龙观感。
    // 改动面确认：tile.ink 全仓仅 4 处消费（V5Components.kt:473/474 图标与标签、:1020/1024 V5Ledger）。
    tileBlue = TileColors(Color(0xFFEAF1FF), Color(0xFF2C64E7)),
    tileOrange = TileColors(Color(0xFFFEF1E2), Color(0xFFA65C1E)),
    tileGreen = TileColors(Color(0xFFE5F8EE), Color(0xFF107F50)),
    tilePurple = TileColors(Color(0xFFF1ECFE), Color(0xFF7A4EE2)),
    tilePink = TileColors(Color(0xFFFCEBF5), Color(0xFFB14194)),
    tileCyan = TileColors(Color(0xFFE4F6FB), Color(0xFF0D7897)),
    // 氛围光晕：原值 0x13/0x0B 在 1080p 屏上几乎不可见（整屏读起来就是一块灰白），
    // 品牌色因此进不到空间感里、页面显"平"。提到 0x2E/0x1C 后光晕仍属"氛围"量级
    // （不干扰白卡上的文字对比度），但能撑起顶部蓝、右上粉紫两处色彩落点。
    auroraGlow1 = Color(0x2E6366F1),
    auroraGlow2 = Color(0x1CEC4899),
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
    // 暗色同理：#727A90 在卡面 #171A25 上是 4.05:1（临界偏低），提到 #8A93A8 → 5.63:1。
    text3 = Color(0xFF8A93A8),
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
    // 暗色主题原本全部达标（瓷片 5.93~7.84:1、ok 6.19:1、danger 5.09:1），
    // ink 直接沿用语义实色 —— 暗色观感零变化。
    okInk = Color(0xFF3DCC8E),
    upInk = Color(0xFFF5A25B),
    dangerText = Color(0xFFF4776D),
    neutralInk = Color(0xFF8A9CB4),
    accentInk = Color(0xFF6E97FF),
    tileBlue = TileColors(Color(0x266E97FF), Color(0xFF93B4FF)),
    tileOrange = TileColors(Color(0x26F5A25B), Color(0xFFF7B177)),
    tileGreen = TileColors(Color(0x243DCC8E), Color(0xFF5ED9A6)),
    tilePurple = TileColors(Color(0x2B966EF5), Color(0xFFB49AF8)),
    tilePink = TileColors(Color(0x24E966C4), Color(0xFFF093D4)),
    tileCyan = TileColors(Color(0x2138BDF8), Color(0xFF6FD0F2)),
    // 暗色底本来就深，光晕只需比亮色略强一点点就能被看见（见亮色处注释）。
    auroraGlow1 = Color(0x3D6366F1),
    auroraGlow2 = Color(0x24EC4899),
    cardShadow = Color(0x14000000),
)

val LocalV5Colors = staticCompositionLocalOf { LightV5Colors }

object V5ThemeColors {
    val current: V5Colors
        @Composable get() = LocalV5Colors.current
}

/**
 * v5 底部面板的公共形状与标题字号。
 *
 * v5 的面板语言是「26dp 顶圆角 + 17sp 粗标题」，与 v4 的 22dp/18sp 半粗不同。放在这里集中定义，
 * 而不是让每个 v5 页面各写一遍 `RoundedCornerShape(topStart = V5Radius.r26, topEnd = V5Radius.r26)`——
 * 否则改一处面板圆角就要全仓搜散落的字面量。
 */
val V5SheetShape = RoundedCornerShape(topStart = V5Radius.r26, topEnd = V5Radius.r26)

/** 见 [V5SheetShape]。 */
val V5SheetTitleStyle = TextStyle(fontSize = V5Type.sp17, fontWeight = FontWeight.Bold)

/*
 * 这里原本还有一个 `@Composable fun V5Theme(darkTheme, content)`，**已删除**。
 *
 * 它是 v5 原型工程留下的独立主题入口，但全仓 **零调用**：实际的主题所有者是 Theme.kt 的
 * `SlteTheme`，后者已经负责下发 `LocalV5Colors`（见 Theme.kt:86）。留着它有三个害处：
 * 1. 它是个"第二个主题入口"，谁误用了就会在 SlteTheme 内部再套一层，出现两套明暗状态；
 * 2. 它会 `SideEffect` 改写 window 的状态栏/导航栏外观与 decorView 底色，
 *    与 SlteTheme 的同名逻辑**互相覆盖**——谁后执行谁说了算，属于难查的闪烁类缺陷；
 * 3. 它把 `darkTheme` 当参数收，等于允许"App 内选择"与"系统明暗"两套信号同时存在，
 *    而本项目从 v4 起就有一条铁律：**darkTheme 只能有一个信号源**（否则会复现
 *    "App 内选亮色 + 系统暗色 = 白字压白底"的整屏不可见事故）。
 *
 * 删掉后 v5 取色仍由 `LocalV5Colors` + `V5ThemeColors.current` 提供，能力没有任何减少。
 */
