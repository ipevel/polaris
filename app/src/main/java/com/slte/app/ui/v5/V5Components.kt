// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 v5 原型工程移植（VmShell 设计语言通用组件；原型脚手架已归档清理）。
// v6（iOS 简约风）整体换皮：所有组件签名保持不变，只改内部实现。

package com.slte.app.ui.v5

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.theme.LocalV5Colors
import com.slte.app.ui.theme.TileColors
import com.slte.app.ui.theme.V5Colors
import com.slte.app.ui.theme.V5Radius
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type

/* ============================================================
   v6 通用组件（iOS 简约风：分组底 + 白卡 + tint 图标 + 大标题
   + 标准 Tab 栏 + 白底色环连接钮 + iOS 绿开关）
   签名与 v5 完全一致，调用点无需改动。
   ============================================================ */

/** 图标 tint 色调（iOS 淡底 + 实色图标，不随主题换义）。 */
enum class IconTone { BLUE, PINK, ORANGE, GREEN, PURPLE, CYAN }

/** 保留：历史调用兼容（v6 图标走 tint，不再用渐变）。 */
fun iconBrush(tone: IconTone): Brush = when (tone) {
    IconTone.BLUE -> Brush.linearGradient(listOf(Color(0xFF6AA5FF), Color(0xFF2F6BF6)))
    IconTone.PINK -> Brush.linearGradient(listOf(Color(0xFFF480C8), Color(0xFFC93BAE)))
    IconTone.ORANGE -> Brush.linearGradient(listOf(Color(0xFFFBC24B), Color(0xFFF0812B)))
    IconTone.GREEN -> Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF14A366)))
    IconTone.PURPLE -> Brush.linearGradient(listOf(Color(0xFFA98BFA), Color(0xFF7A4FF0)))
    IconTone.CYAN -> Brush.linearGradient(listOf(Color(0xFF4CC7F0), Color(0xFF0E9DC5)))
}

/** tint 色调（语义固定：蓝=额度/下行，橙=已用/上行，绿=就绪/延迟，紫=配置，粉=福利，青=线路）。 */
enum class TileTone { BLUE, ORANGE, GREEN, PURPLE, PINK, CYAN }

fun V5Colors.tile(tone: TileTone): TileColors = when (tone) {
    TileTone.BLUE -> tileBlue
    TileTone.ORANGE -> tileOrange
    TileTone.GREEN -> tileGreen
    TileTone.PURPLE -> tilePurple
    TileTone.PINK -> tilePink
    TileTone.CYAN -> tileCyan
}

private fun IconTone.toTileTone(): TileTone = TileTone.valueOf(name)

/** 徽标胶囊色调。 */
enum class ChipTone { OK, WARN, DANGER, NEUTRAL, ACCENT }

/**
 * 把半透明色 [fg] 预先压到不透明底色 [bg] 上（sRGB 直通道 alpha 合成）。
 * （v5 遗留：算法与 Skia 合成一致，真机像素验证过，v6 沿用。）
 */
private fun flattenOver(fg: Color, bg: Color): Color {
    val a = fg.alpha
    return Color(
        red = fg.red * a + bg.red * (1f - a),
        green = fg.green * a + bg.green * (1f - a),
        blue = fg.blue * a + bg.blue * (1f - a),
        alpha = 1f,
    )
}

/** 芯片的「墨色 → 底色」（v5 的可达性结论在 v6 同样成立，色相未变）。 */
fun V5Colors.chip(tone: ChipTone): Pair<Color, Color> = when (tone) {
    ChipTone.OK -> okInk to flattenOver(okBg, surface)
    ChipTone.WARN -> upInk to flattenOver(upBg, surface)
    ChipTone.DANGER -> dangerText to flattenOver(dangerBg, surface)
    ChipTone.NEUTRAL -> neutralInk to flattenOver(neutralBg, surface)
    ChipTone.ACCENT -> accentInk to flattenOver(accentBg, surface)
}

/**
 * 页面底：v6 为纯色分组底（iOS 无氛围光晕）。
 *
 * 主题已把 auroraGlow1/2 置透明，这里直接铺底色；[breathing]/[connected]
 * 参数保留兼容，v6 不再做呼吸/变色（"已连接"由连接钮色环与状态文字表达）。
 */
@Composable
fun Modifier.v5Aurora(breathing: Boolean = false, connected: Boolean = false): Modifier = background(V5ThemeColors.current.bg)

/** 卡片投影：iOS 式极淡（亮色 едва 可见、暗色无投影，色值随主题单源）。 */
@Composable
fun Modifier.v5CardShadow(shape: Shape): Modifier {
    val sc = LocalV5Colors.current
    return shadow(2.dp, shape, clip = false, ambientColor = sc.cardShadow, spotColor = sc.cardShadow)
}

/**
 * v5 统一的行点击（返回 `Modifier`，用法 `.then(v5Clickable(onClick = onClick))`，
 * 或直接尾随 lambda `.then(v5Clickable { ... })`）。
 *
 * 淡色水波纹（安卓触摸反馈）+ iOS 观感；无障碍方案保留：
 * clearAndSetSemantics 单节点、名称/角色/状态落在同一节点，点击动作由
 * `clickable(onClickLabel = ...)` 提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun v5Clickable(
    role: Role? = null,
    toggleState: Boolean? = null,
    selected: Boolean? = null,
    label: String? = null,
    texts: List<String>? = null,
    onClick: (() -> Unit)? = null,
): Modifier {
    val action = onClick
    val currentAction = rememberUpdatedState(action)
    // 子节点语义会被 clearAndSetSemantics 隐藏，所以把行内文字显式挂到本节点上，
    // 保证 `onNodeWithText(...)` 仍能定位（未显式传 texts 时退回 label）。
    val nodeTexts = texts ?: listOfNotNull(label)
    // 只有「有可暴露的名称或状态」时才用 clearAndSetSemantics 收敛成单节点；
    // 否则退回普通 semantics，避免把子节点语义一并清掉。
    val shouldClear = label != null ||
        nodeTexts.isNotEmpty() ||
        toggleState != null ||
        selected != null
    val semanticsBlock: SemanticsPropertyReceiver.() -> Unit = {
        if (role != null) this.role = role
        if (toggleState != null) this.toggleableState = ToggleableState(toggleState)
        if (selected != null) this.selected = selected
        if (label != null) this.contentDescription = label
        if (nodeTexts.isNotEmpty()) {
            this[SemanticsProperties.Text] = nodeTexts.map { AnnotatedString(it) }
        }
    }
    val base = if (shouldClear) {
        Modifier.clearAndSetSemantics(properties = semanticsBlock)
    } else {
        Modifier.semantics(properties = semanticsBlock)
    }
    if (action == null) return base
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = V5ThemeColors.current.pressed
    return base.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = pressed),
            role = role,
            onClickLabel = label,
            onClick = { currentAction.value?.invoke() },
        ),
    )
}

/** iOS 分组卡片（白底，18dp 圆角，极淡投影）。 */
@Composable
fun V5Card(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(V5Spacing.dp16),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
    Column(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(V5Radius.r18))
            .clip(RoundedCornerShape(V5Radius.r18))
            .background(c.surface)
            .padding(contentPadding),
        content = content,
    )
}

/** 无内边距卡片（清单容器，行自带分隔线）。 */
@Composable
fun V5CardFlat(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
    Column(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(V5Radius.r18))
            .clip(RoundedCornerShape(V5Radius.r18))
            .background(c.surface),
        content = content,
    )
}

/** iOS 发丝分隔线（左缩进 16dp，与行内图标右缘对齐）。 */
@Composable
fun V5Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth().padding(start = V5Spacing.dp16),
        thickness = V5Spacing.dp1,
        color = V5ThemeColors.current.hairline2,
    )
}

/**
 * 顶栏：iOS 双模式。
 * - 无返回键（主 Tab 页）→ 34sp 大标题，actions 贴右；
 * - 有返回键（二级页）→ iOS 式"< + 居中 17sp 标题"，actions 贴右（无 actions 时占位保居中）。
 */
@Composable
fun V5TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = V5ThemeColors.current
    if (onBack == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = V5Spacing.dp16, end = V5Spacing.dp16, top = V5Spacing.dp8, bottom = V5Spacing.dp8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
        ) {
            Text(
                title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = V5Spacing.dp16, top = V5Spacing.dp4, bottom = V5Spacing.dp4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V5TopIconButton(Icons.Outlined.ChevronLeft, onBack, contentDescription = stringResource(R.string.back))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    title,
                    fontSize = V5Type.sp17,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 右侧占位 48dp：无 actions 时标题严格居中（与左侧返回键等宽）。
            Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
    }
}

/**
 * 顶栏动作按钮：iOS 风格——无底卡，图标直接品牌蓝。
 *
 * 触摸目标保持 44dp（无障碍最小可点尺寸），视觉只有图标本身。
 * [contentDescription] 供无障碍读屏；调用点必须传入与动作等价的文案。
 */
@Composable
fun V5TopIconButton(
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val c = V5ThemeColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(v5Clickable(role = Role.Button, label = contentDescription, onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(22.dp), tint = tint ?: c.accent)
    }
}

/** iOS 分组头：13sp 灰色（v5 的蓝竖线装饰已移除）。 */
@Composable
fun SectionTitle(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        fontSize = V5Type.sp13,
        color = V5ThemeColors.current.text3,
        modifier = modifier.padding(start = V5Spacing.dp16, top = V5Spacing.dp8, bottom = V5Spacing.dp6),
    )
}

/** 徽标胶囊（iOS tint 底 + 墨色字）。 */
@Composable
fun V5Chip(
    tone: ChipTone,
    text: String,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    icon: ImageVector? = null,
    large: Boolean = false,
) {
    val c = V5ThemeColors.current
    val (ink, bg) = c.chip(tone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(V5Radius.pill))
            .background(bg)
            .padding(horizontal = if (large) V5Spacing.dp12 else V5Spacing.dp10, vertical = if (large) V5Spacing.dp5 else V5Spacing.dp4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp5),
    ) {
        if (dot) Box(Modifier.size(V5Spacing.dp6).clip(CircleShape).background(ink))
        icon?.let { Icon(it, null, modifier = Modifier.size(13.dp), tint = ink) }
        Text(
            text,
            fontSize = if (large) V5Type.sp12 else V5Type.sp11_5,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

/** 在线状态点。 */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).clip(CircleShape).background(V5ThemeColors.current.ok))
}

/** 节点延迟：波形小图标 + 等宽分色数字。 */
@Composable
fun LatencyText(value: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    val (ink, _) = c.chip(tone)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp4)) {
        Icon(Icons.Outlined.MonitorHeart, null, modifier = Modifier.size(V5Spacing.dp14), tint = ink)
        Text(value, fontSize = V5Type.sp12_5, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = ink)
    }
}

/** 选择态：iOS 式蓝色对勾（未选中时占位，保证行高对齐）。 */
@Composable
fun RadioDot(on: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(20.dp), tint = V5ThemeColors.current.accent)
    }
}

/** iOS tint 图标：淡色圆角方块 + 实色图标。 */
@Composable
fun GradientIcon(tone: IconTone, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val t = V5ThemeColors.current.tile(tone.toTileTone())
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(t.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(size * 0.55f), tint = t.ink)
    }
}

/** iOS 数据卡（白底；标签 13sp 灰 + 数值大字）。 */
@Composable
fun MacaronTile(
    tone: TileTone,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    small: Boolean = false,
) {
    MacaronTile(tone, label, modifier, icon, small) {
        Text(
            value,
            fontSize = if (small) V5Type.sp13_5 else 22.sp,
            fontWeight = FontWeight.Bold,
            color = V5ThemeColors.current.text,
        )
    }
}

/** iOS 数据卡（自定义值槽，可放带单位的富文本）。 */
@Composable
fun MacaronTile(
    tone: TileTone,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    small: Boolean = false,
    live: Boolean = false,
    value: @Composable () -> Unit,
) {
    val c = V5ThemeColors.current
    val t = c.tile(tone)
    Column(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(V5Radius.r18))
            .clip(RoundedCornerShape(V5Radius.r18))
            .background(c.surface)
            .padding(horizontal = V5Spacing.dp16, vertical = V5Spacing.dp14),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp6)) {
            icon?.let { Icon(it, null, modifier = Modifier.size(V5Spacing.dp14), tint = t.ink) }
            Text(label, fontSize = V5Type.sp13, color = c.text2)
            if (live) LiveDot()
        }
        Box(Modifier.padding(top = V5Spacing.dp6)) { value() }
    }
}

/** iOS 分段选择：灰轨 + 白色选中段（带投影）。 */
@Composable
fun SegmentedPill(options: List<String>, active: Int, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(V5Radius.r10))
            .background(c.surface3)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, opt ->
            val thumb = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .then(if (i == active) Modifier.v5CardShadow(thumb) else Modifier)
                    .clip(thumb)
                    .then(if (i == active) Modifier.background(c.surface) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt,
                    fontSize = V5Type.sp13,
                    fontWeight = if (i == active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == active) c.text else c.text2,
                )
            }
        }
    }
}

/** 按钮风格（v6 全部走 iOS 扁平实色，不再用渐变）。 */
enum class ButtonStyle { PRIMARY, MAGENTA, GREEN, TONAL, NEUTRAL, DANGER, SOLID_DANGER, GHOST }

/**
 * v6 按钮：iOS 扁平实色。
 *
 * @param onClickEnabled 按钮是否可点（v5 语义保留：false = 看得见、按不动；
 *   禁用态走中性色：底 surface3 + 字 text3）。
 */
@Composable
fun V5Button(
    text: String,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    hero: Boolean = false,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
    onClickEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    val radius = when {
        hero -> 16
        small -> 10
        else -> 12
    }
    val shape = RoundedCornerShape(radius)
    val height = if (hero) {
        54.dp
    } else if (small) {
        36.dp
    } else {
        50.dp
    }
    val active = onClickEnabled && !loading
    // iOS 实色映射
    var bg: Color? = null
    var fg = c.text
    when (style) {
        ButtonStyle.PRIMARY -> {
            bg = c.accent
            fg = Color.White
        }
        ButtonStyle.MAGENTA -> {
            bg = Color(0xFFFF2D55)
            fg = Color.White
        }
        ButtonStyle.GREEN -> {
            bg = c.ok
            fg = Color.White
        }
        ButtonStyle.TONAL -> {
            bg = c.accentBg
            fg = c.accent
        }
        ButtonStyle.NEUTRAL -> {
            bg = c.surface3
            fg = c.text
        }
        ButtonStyle.DANGER -> {
            bg = c.dangerBg
            fg = c.dangerText
        }
        ButtonStyle.SOLID_DANGER -> {
            bg = c.danger
            fg = Color.White
        }
        ButtonStyle.GHOST -> fg = c.accent
    }
    // 禁用态：中性色（底 surface3 + 字 text3），v5 的真机对比度结论沿用。
    val disabled = !active
    if (disabled) {
        bg = c.surface3
        fg = c.text3
    }
    val clickable = v5Clickable(role = Role.Button, label = text, onClick = if (active) onClick else null)
    // 调用方传入的 modifier 必须最先应用（v5 的历史教训：漏掉会导致调用方尺寸失效）。
    val m = modifier.then(
        if (style == ButtonStyle.GHOST) {
            clickable
        } else {
            clickable
                .then(if (!disabled && (style == ButtonStyle.PRIMARY || style == ButtonStyle.GREEN || style == ButtonStyle.SOLID_DANGER)) Modifier.v5CardShadow(shape) else Modifier)
                .clip(shape)
                .background(bg ?: Color.Transparent)
        },
    )
    Row(
        modifier = m
            .defaultMinSize(minHeight = height)
            .padding(horizontal = if (style == ButtonStyle.GHOST) V5Spacing.dp4 else V5Spacing.dp18),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            Box(
                modifier = Modifier.size(V5Spacing.dp18),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = fg,
                    strokeWidth = V5Spacing.dp2,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            leadingIcon?.let { Icon(it, null, modifier = Modifier.size(17.dp), tint = fg) }
        }
        Text(
            text,
            fontSize = when {
                hero -> V5Type.sp16
                small -> V5Type.sp13
                else -> V5Type.sp16
            },
            fontWeight = FontWeight.SemiBold,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

/** iOS 风格开关（on = 绿，51×31）。 */
@Composable
fun V5Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switchKnob")
    Box(
        modifier = modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(V5Radius.pill))
            .background(if (checked) c.ok else c.surface3),
    ) {
        Box(
            Modifier
                .offset(x = 2.dp + x, y = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 底部 Tab 栏位（文案走资源，三语键 tab_home / tab_server / tab_traffic / tab_profile）。
 */
enum class NavTab(val icon: ImageVector, @StringRes val labelRes: Int) {
    HOME(Icons.Outlined.Home, R.string.tab_home),
    NODES(Icons.Outlined.Hub, R.string.tab_server),
    TRAFFIC(Icons.Outlined.BarChart, R.string.tab_traffic),
    ME(Icons.Outlined.Person, R.string.tab_profile),
}

/** iOS 标准 Tab 栏：贴底通栏、顶部发丝线、图标 + 小标签、选中品牌蓝。 */
@Composable
fun V5BottomNavBar(active: NavTab, modifier: Modifier = Modifier, onSelect: (NavTab) -> Unit = {}) {
    val c = V5ThemeColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = V5Spacing.dp1, color = c.hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val on = tab == active
                val tabLabel = stringResource(tab.labelRes)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            v5Clickable(
                                role = Role.Tab,
                                selected = on,
                                label = tabLabel,
                                onClick = { onSelect(tab) },
                            ),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, modifier = Modifier.size(24.dp), tint = if (on) c.accent else c.text3)
                    Text(
                        tabLabel,
                        fontSize = 10.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) c.accent else c.text3,
                    )
                }
            }
        }
    }
}

/**
 * iOS 式连接钮：白底大圆 + 品牌色细环（已连=绿 / 未连=蓝），圆心电源图标。
 *
 * v5 的波纹粒子在 v6 移除：iOS 语言里"状态"由色环与下方状态文字表达，不靠装饰动画。
 * [connecting] 时文案变「取消」——MainViewModel.toggleConnection 在 isConnecting 分支里执行
 * 取消，所以这是一个真实可点的取消入口（v5 语义保留）。
 * 无障碍名称 [connectLabel] 保留，读屏可播报。
 */
@Composable
fun HeroConnectButton(
    connected: Boolean,
    modifier: Modifier = Modifier,
    connecting: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    val ring = if (connected) c.ok else c.accent
    val connectLabel = when {
        connected -> stringResource(R.string.v5_disconnect)
        connecting -> stringResource(R.string.v5_cancel)
        else -> stringResource(R.string.v5_connect)
    }
    Box(
        modifier = modifier
            .size(168.dp)
            .then(v5Clickable(role = Role.Button, label = connectLabel, onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        // 外层淡色光晕（静态，非动画）
        Box(
            Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(if (connected) c.okBg else c.accentBg),
        )
        // 白底圆 + 色环
        Box(
            Modifier
                .size(140.dp)
                .v5CardShadow(CircleShape)
                .clip(CircleShape)
                .background(c.surface)
                .border(3.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PowerSettingsNew,
                null,
                modifier = Modifier.size(52.dp),
                tint = ring,
            )
        }
    }
}

/** 提示横幅（info/warn/danger，iOS tint 底）。 */
@Composable
fun V5Banner(tone: ChipTone, text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = V5ThemeColors.current
    val (ink, bg) = c.chip(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V5Radius.r14))
            .background(bg)
            .padding(V5Spacing.dp12),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            icon ?: if (tone == ChipTone.DANGER) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            null,
            modifier = Modifier.size(V5Spacing.dp16),
            tint = ink,
        )
        Text(text, fontSize = V5Type.sp12, lineHeight = V5Type.sp18, color = ink)
    }
}

/** iOS 清单行（图标/标题/副标题/值/尾部/箭头/开关）。 */
@Composable
fun V5RowItem(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    sub: String? = null,
    value: String? = null,
    valueMono: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    danger: Boolean = false,
    switchState: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    // 行类控件必须自带可访问名称与文本（v5 真机实测结论，v6 沿用）。
    val a11yTexts = if (onClick != null || switchState != null) listOfNotNull(title, sub, value) else emptyList()
    val a11yLabel = a11yTexts.joinToString(", ").ifEmpty { null }
    Row(
        modifier = modifier
            .then(
                v5Clickable(
                    role = if (switchState != null) Role.Switch else null,
                    toggleState = switchState,
                    label = a11yLabel,
                    texts = a11yTexts,
                    onClick = onClick,
                ),
            )
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = V5Spacing.dp16, vertical = V5Spacing.dp10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp12),
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            danger -> c.dangerBg
                            highlight -> c.accentBg
                            else -> c.surface3
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(17.dp),
                    tint = when {
                        danger -> c.danger
                        highlight -> c.accent
                        else -> c.text2
                    },
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = V5Type.sp16,
                color = if (danger) c.danger else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(it, fontSize = V5Type.sp13, color = c.text3, modifier = Modifier.padding(top = V5Spacing.dp1))
            }
        }
        value?.let {
            Text(
                it,
                fontSize = V5Type.sp15,
                color = c.text2,
                fontFamily = if (valueMono) FontFamily.Monospace else null,
            )
        }
        trailing?.invoke(this)
        if (chevron) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(V5Spacing.dp16), tint = c.text3)
    }
}

/**
 * 账本行数据（label 左 / 等宽值 右）。
 * [tone] 保留字段兼容（v6 渲染为普通行，不再套马卡龙底）。
 */
data class LedgerData(
    val label: String,
    val value: String,
    val color: Color? = null,
    val icon: ImageVector? = null,
    val tone: TileTone? = null,
)

/** iOS 账本行：左标签灰 / 右值深色等宽，行间发丝线。 */
@Composable
fun V5Ledger(rows: List<LedgerData>, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(thickness = V5Spacing.dp1, color = c.hairline2, modifier = Modifier.padding(start = V5Spacing.dp16))
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = V5Spacing.dp16, vertical = V5Spacing.dp10),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp12),
            ) {
                // 标签固定列宽（v5 的 IPv6 长值压标签竖排教训保留：标签列限宽、值取剩余宽度右对齐）。
                Row(
                    Modifier
                        .defaultMinSize(minWidth = 64.dp)
                        .widthIn(max = 140.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    r.icon?.let { Icon(it, null, modifier = Modifier.size(V5Spacing.dp16), tint = c.text2) }
                    Text(
                        r.label,
                        fontSize = V5Type.sp15,
                        color = c.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    r.value,
                    fontSize = V5Type.sp15,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = r.color ?: c.text,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** iOS 细进度条（蓝填充 + 灰轨）。 */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, brush: Brush? = null) {
    val c = V5ThemeColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(V5Radius.pill))
            .background(c.surface3),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(V5Radius.pill))
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(c.accent)),
        )
    }
}

/** iOS 底部面板（抓手 + 居中标题 + 小圆角）。 */
@Composable
fun SheetOverlay(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    stickerIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0x7A0A0C16))
                .then(v5Clickable {}),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = V5Radius.r16, topEnd = V5Radius.r16))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(start = V5Spacing.dp22, end = V5Spacing.dp22, top = V5Spacing.dp10, bottom = 28.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(V5Radius.pill))
                    .background(c.surface3),
            )
            stickerIcon?.let {
                Icon(
                    it,
                    null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = V5Spacing.dp8)
                        .size(40.dp),
                    tint = c.accent,
                )
            }
            Text(
                title,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = V5Spacing.dp6),
                fontSize = V5Type.sp17,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            sub?.let {
                Text(
                    it,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 7.dp),
                    fontSize = V5Type.sp13,
                    lineHeight = V5Type.sp18,
                    color = c.text3,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(V5Spacing.dp16))
            content()
        }
    }
}

/** 面板单选项行：iOS 式右侧蓝色对勾。 */
@Composable
fun SheetOption(
    title: String,
    sub: String? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(V5Radius.r12))
            .background(if (selected) c.accentBg else c.surface)
            .padding(horizontal = V5Spacing.dp16, vertical = V5Spacing.dp12)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = V5Type.sp16, color = c.text)
            sub?.let { Text(it, fontSize = V5Type.sp13, color = c.text3, modifier = Modifier.padding(top = V5Spacing.dp1)) }
        }
        if (selected) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(V5Spacing.dp20), tint = c.accent)
    }
}

/** Polaris 星标品牌图形（轨道环 + 四角星 + 橙色伴星，v5 沿用）。 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width / 64f
        val accent = c.accent
        drawCircle(accent.copy(alpha = 0.30f), radius = 26f * s, style = Stroke(1.5f * s))
        rotate(degrees = -26f, pivot = Offset(32f * s, 32f * s)) {
            drawOval(
                accent.copy(alpha = 0.55f),
                topLeft = Offset(6f * s, 21.5f * s),
                size = Size(52f * s, 21f * s),
                style = Stroke(1.4f * s),
            )
        }
        rotate(degrees = 38f, pivot = Offset(32f * s, 32f * s)) {
            drawOval(
                accent.copy(alpha = 0.22f),
                topLeft = Offset(6f * s, 21.5f * s),
                size = Size(52f * s, 21f * s),
                style = Stroke(1.2f * s),
            )
        }
        val star = Path().apply {
            moveTo(32f * s, 12.5f * s)
            lineTo(35.9f * s, 25.7f * s)
            lineTo(49.1f * s, 29.6f * s)
            lineTo(35.9f * s, 33.5f * s)
            lineTo(32f * s, 46.7f * s)
            lineTo(28.1f * s, 33.5f * s)
            lineTo(14.9f * s, 29.6f * s)
            lineTo(28.1f * s, 25.7f * s)
            close()
        }
        drawPath(star, accent)
        drawCircle(Color(0xFFFF9F0A), radius = 2.8f * s, center = Offset(52.5f * s, 21f * s))
    }
}

/** 字母 P 头像（v5 沿用，渐变收敛为品牌蓝系）。 */
@Composable
fun AvatarP(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF007AFF)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("P", color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
    }
}
