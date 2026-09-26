// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
// 自 polaris-ui-v5-code 原型工程移植（VmShell 设计语言通用组件）。

package com.slte.app.ui.v5

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import com.slte.app.ui.theme.V5ThemeColors

/* ============================================================
   v5 通用组件（VmShell 设计语言：白卡 + 马卡龙瓷片 + 渐变方块图标
   + 悬浮胶囊导航 + 大圆连接钮 + iOS 绿开关）
   ============================================================ */

/** 渐变方块图标色调（渐变固定，不随主题）。 */
enum class IconTone { BLUE, PINK, ORANGE, GREEN, PURPLE, CYAN }

fun iconBrush(tone: IconTone): Brush = when (tone) {
    IconTone.BLUE -> Brush.linearGradient(listOf(Color(0xFF6AA5FF), Color(0xFF2F6BF6)))
    IconTone.PINK -> Brush.linearGradient(listOf(Color(0xFFF480C8), Color(0xFFC93BAE)))
    IconTone.ORANGE -> Brush.linearGradient(listOf(Color(0xFFFBC24B), Color(0xFFF0812B)))
    IconTone.GREEN -> Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF14A366)))
    IconTone.PURPLE -> Brush.linearGradient(listOf(Color(0xFFA98BFA), Color(0xFF7A4FF0)))
    IconTone.CYAN -> Brush.linearGradient(listOf(Color(0xFF4CC7F0), Color(0xFF0E9DC5)))
}

/** 马卡龙瓷片色调（语义固定：蓝=额度/下行，橙=已用/上行，绿=就绪/延迟，紫=配置，粉=福利，青=线路）。 */
enum class TileTone { BLUE, ORANGE, GREEN, PURPLE, PINK, CYAN }

fun V5Colors.tile(tone: TileTone): TileColors = when (tone) {
    TileTone.BLUE -> tileBlue
    TileTone.ORANGE -> tileOrange
    TileTone.GREEN -> tileGreen
    TileTone.PURPLE -> tilePurple
    TileTone.PINK -> tilePink
    TileTone.CYAN -> tileCyan
}

/** 徽标胶囊色调。 */
enum class ChipTone { OK, WARN, DANGER, NEUTRAL, ACCENT }

fun V5Colors.chip(tone: ChipTone): Pair<Color, Color> = when (tone) {
    ChipTone.OK -> ok to okBg
    ChipTone.WARN -> up to upBg
    ChipTone.DANGER -> danger to dangerBg
    ChipTone.NEUTRAL -> neutral to neutralBg
    ChipTone.ACCENT -> accent to accentBg
}

/** 页面氛围底：浅紫灰底 + 两处径向光晕（颜色由主题单源下发）。 */
@Composable
fun Modifier.v5Aurora(): Modifier {
    val c = V5ThemeColors.current
    return drawBehind {
        drawRect(c.bg)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c.auroraGlow1, Color.Transparent),
                center = Offset(size.width * 0.16f, -size.height * 0.06f),
                radius = size.width * 0.78f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(c.auroraGlow2, Color.Transparent),
                center = Offset(size.width * 0.88f, 0f),
                radius = size.width * 0.68f,
            ),
        )
    }
}

/** 卡片投影：亮色柔和投影、暗色收敛（色值随主题单源）。 */
@Composable
fun Modifier.v5CardShadow(shape: Shape): Modifier {
    val sc = LocalV5Colors.current
    return shadow(6.dp, shape, clip = false, ambientColor = sc.cardShadow, spotColor = sc.cardShadow)
}

/**
 * v5 统一的无涟漪点击（返回 `Modifier`，用法 `.then(noRippleClickable(onClick))`）。
 *
 * 对 `ui.screen.*` 下的 v5 化页面开放（internal）：节点/公告等页面的行容器不再走 v4 的
 * `clickable(indication = null, interactionSource = remember { ... })` 手写形式，避免同一套
 * 交互在仓库里出现两处实现、改一处漏一处。
 */
@Composable
internal fun noRippleClickable(onClick: (() -> Unit)?): Modifier = if (onClick != null) {
    // remember 隔离：composition 中直接创建 MutableInteractionSource 会被 lint 拦截
    val interactionSource = remember { MutableInteractionSource() }
    Modifier.clickable(interactionSource, null, onClick = onClick)
} else {
    Modifier
}

/** 白色大圆角卡片（22dp 圆角）。 */
@Composable
fun V5Card(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
    Column(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
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
            .v5CardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface),
        content = content,
    )
}

/** 顶栏：大标题 + 返回 + 动作位。 */
@Composable
fun V5TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) V5TopIconButton(Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        // 标题用 weight 吃掉全部剩余宽度、空出的空间由它承担，这样 actions（如首页的
        // 「已连接/未连接」胶囊）保持内在宽度并始终贴右。
        // 注意两点：
        // 1) 不能再保留 Spacer(weight(1f))——两个 weight 子项会均分剩余宽度，长标题
        //    反而更早被省略号截断；
        // 2) 站点名是面板可控的任意长字符串，必须给 Ellipsis，否则默认 Clip 会硬切字，
        //    且非 weighted 的 Text 会把 actions 挤到 0 宽（首页连接状态就此不可见）。
        Box(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = V5ThemeColors.current.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

/** 顶栏圆形动作按钮。 */
@Composable
fun V5TopIconButton(icon: ImageVector, onClick: (() -> Unit)? = null, tint: Color? = null) {
    val c = V5ThemeColors.current
    Box(
        modifier = Modifier
            .size(37.dp)
            .v5CardShadow(RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .then(noRippleClickable(onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(19.dp), tint = tint ?: c.accent)
    }
}

/** 区块标题（11sp 大写字距）。 */
@Composable
fun SectionTitle(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.padding(horizontal = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        color = V5ThemeColors.current.text3,
    )
}

/** 徽标胶囊。 */
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
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = if (large) 12.dp else 10.dp, vertical = if (large) 5.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        icon?.let { Icon(it, null, modifier = Modifier.size(13.dp), tint = ink) }
        Text(
            text,
            fontSize = if (large) 12.sp else 11.5.sp,
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.MonitorHeart, null, modifier = Modifier.size(14.dp), tint = ink)
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = ink)
    }
}

/** 单选圆点（节点选择）。 */
@Composable
fun RadioDot(on: Boolean, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    Box(
        modifier = modifier
            .size(21.dp)
            .clip(CircleShape)
            .then(
                if (on) {
                    Modifier.background(c.accent)
                } else {
                    Modifier.border(1.7.dp, c.text3, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
    }
}

/** 渐变圆角方块图标。 */
@Composable
fun GradientIcon(tone: IconTone, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(iconBrush(tone)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(size * 0.5f), tint = Color.White)
    }
}

/** 马卡龙数据瓷片（字符串值）。 */
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
            fontSize = if (small) 13.5.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = V5ThemeColors.current.text,
        )
    }
}

/** 马卡龙数据瓷片（自定义值槽，可放带单位的富文本）。 */
@Composable
fun MacaronTile(
    tone: TileTone,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    small: Boolean = false,
    value: @Composable () -> Unit,
) {
    val c = V5ThemeColors.current
    val t = c.tile(tone)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(t.bg)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            icon?.let { Icon(it, null, modifier = Modifier.size(13.dp), tint = t.ink) }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, color = t.ink)
        }
        Box(Modifier.padding(top = 2.dp)) { value() }
    }
}

/** 分段选择（胶囊段控）。 */
@Composable
fun SegmentedPill(options: List<String>, active: Int, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    Row(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(c.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, opt ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(37.dp)
                    .clip(RoundedCornerShape(50))
                    .then(if (i == active) Modifier.background(c.accentGrad) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (i == active) Color.White else c.text2,
                )
            }
        }
    }
}

/** 按钮风格。 */
enum class ButtonStyle { PRIMARY, MAGENTA, GREEN, TONAL, NEUTRAL, DANGER, SOLID_DANGER, GHOST }

/**
 * v5 按钮。
 *
 * @param onClickEnabled 按钮是否可点。与 [onClick] 分开是有意的：v4 的 `SlteButton(enabled = false)`
 *   语义是「看得见、按不动」（如提现/转赠在金额为空时），而 v5 组件里 [onClick] 传 null 表示
 *   「整块不可点、连按压反馈都没有」。迁移时必须保住前者的观感与语义，因此单列一个开关：
 *   传 false 时组件仍渲染完整外观，只是不挂点击监听，并把前景/底色按 45% 透明弱化。
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
    val radius = if (hero) {
        17
    } else if (small) {
        12
    } else {
        15
    }
    val shape = RoundedCornerShape(radius)
    val height = if (hero) {
        54.dp
    } else if (small) {
        38.dp
    } else {
        46.dp
    }
    val active = onClickEnabled && !loading
    var bg: Brush? = null
    var fg = c.text
    var spot = Color.Transparent
    when (style) {
        ButtonStyle.PRIMARY -> {
            bg = c.accentGrad
            fg = Color.White
            spot = c.accent.copy(alpha = 0.5f)
        }
        ButtonStyle.MAGENTA -> {
            bg = Brush.linearGradient(listOf(Color(0xFFF27BC8), Color(0xFFC93BAE)))
            fg = Color.White
            spot = Color(0x80C93BAE)
        }
        ButtonStyle.GREEN -> {
            bg = c.okGrad
            fg = Color.White
            spot = c.ok.copy(alpha = 0.5f)
        }
        ButtonStyle.TONAL -> fg = c.accent
        ButtonStyle.NEUTRAL -> fg = c.text2
        ButtonStyle.DANGER -> fg = c.danger
        ButtonStyle.SOLID_DANGER -> {
            fg = c.dangerInk
            spot = c.danger.copy(alpha = 0.4f)
        }
        ButtonStyle.GHOST -> fg = c.accent
    }
    val solidBg = when (style) {
        ButtonStyle.TONAL -> c.accentBg
        ButtonStyle.NEUTRAL -> c.surface2
        ButtonStyle.DANGER -> c.dangerBg
        ButtonStyle.SOLID_DANGER -> c.danger
        else -> Color.Transparent
    }
    if (!active) fg = fg.copy(alpha = 0.45f)
    val clickable = noRippleClickable(if (active) onClick else null)
    // 调用方传入的 modifier 必须**最先**应用：里面通常带着 fillMaxWidth / weight 这类尺寸约束，
    // 放到后面会被 shadow/clip/background 的顺序与默认最小尺寸挤掉。
    //
    // 这里曾经漏掉 `modifier`（参数声明了但整个函数体没用它），于是**调用方传的尺寸全部失效**：
    // 真机实测登录页的「登录」按钮只占了内容的固有宽度（bounds 宽 196px ≈ 75dp，而不是满卡宽），
    // 形成"按钮缩在左边一小块"的观感，而且自动化点击按满宽中心去点会直接点空。
    // 单元测试查不出来（截图里按钮"看起来还在"），是雷电模拟器走查 + uiautomator 的
    // clickable 节点 bounds 才把它钉死的。
    val m = modifier.then(
        if (style == ButtonStyle.GHOST) {
            clickable
        } else {
            clickable
                .shadow(if (spot == Color.Transparent || !active) 0.dp else 8.dp, shape, clip = false, ambientColor = spot, spotColor = spot)
                .clip(shape)
                .then(if (bg != null) Modifier.background(bg) else Modifier.background(solidBg))
                .then(if (active) Modifier else Modifier.alpha(DISABLED_BUTTON_ALPHA))
        },
    )
    Row(
        modifier = m
            .defaultMinSize(minHeight = height)
            .padding(horizontal = if (style == ButtonStyle.GHOST) 4.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            // 转圈容器给足尺寸并让指示器吃满：Material3 的默认尺寸策略在**极小尺寸**下会退化成
            // 一两个像素的点（第 10 轮在关于页实测到 5×5px）。这里 18dp + fillMaxSize
            // 能让静态帧至少画出可辨识的一段弧。
            //
            // 注意：认证三页的"提交中"用的是**全屏 LoadingOverlay**（scrim + 居中卡片），
            // 按钮此时被 scrim 盖住，所以按钮内这枚转圈在那些页面的截图里本来就看不出来——
            // 那不算缺陷，加载反馈由覆盖层承担。此处保留转圈是给"就地加载"的按钮
            // （如工单提交、优惠券验证）用的。
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = fg,
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            leadingIcon?.let { Icon(it, null, modifier = Modifier.size(17.dp), tint = fg) }
        }
        Text(
            text,
            fontSize = when {
                hero -> 16.sp
                small -> 13.sp
                else -> 14.sp
            },
            fontWeight = if (style == ButtonStyle.GHOST) FontWeight.SemiBold else FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

/** 不可用按钮的整体透明度（与 v4 `SlteButton(enabled = false)` 的观感对齐）。 */
private const val DISABLED_BUTTON_ALPHA = 0.45f

/** iOS 风格开关（on = 绿）。 */
@Composable
fun V5Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switchKnob")
    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) c.ok else c.surface3),
    ) {
        Box(
            Modifier
                .offset(x = 3.5.dp + x, y = 3.5.dp)
                .size(21.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 底部悬浮胶囊导航。
 *
 * 文案走资源而不是硬编码：v5 组件里曾写死简体中文，导致 en / zh-Hant 下整条导航仍显示中文
 * （键 tab_home / tab_server / tab_traffic / tab_profile 三语早已存在，见 ResourceLocaleParityTest）。
 */
enum class NavTab(val icon: ImageVector, @StringRes val labelRes: Int) {
    HOME(Icons.Outlined.Home, R.string.tab_home),
    NODES(Icons.Outlined.Hub, R.string.tab_server),
    TRAFFIC(Icons.Outlined.BarChart, R.string.tab_traffic),
    ME(Icons.Outlined.Person, R.string.tab_profile),
}

@Composable
fun FloatingPillNav(active: NavTab, modifier: Modifier = Modifier, onSelect: (NavTab) -> Unit = {}) {
    val c = V5ThemeColors.current
    Row(
        modifier = modifier
            .v5CardShadow(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(c.navBg)
            .padding(7.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            val on = tab == active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(c.navOn) else Modifier)
                    .then(noRippleClickable { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, null, modifier = Modifier.size(21.dp), tint = if (on) c.accent else c.text3)
                Text(
                    stringResource(tab.labelRes),
                    fontSize = 10.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    color = if (on) c.accent else c.text3,
                )
            }
        }
    }
}

/**
 * 大圆连接钮：未连=蓝渐变+闪电，已连=绿渐变+电源+光晕呼吸环。
 *
 * [connecting] 时文案变「取消」——MainViewModel.toggleConnection 在 isConnecting 分支里执行
 * 取消（停隧道 + 复位），所以这不是"置灰的等待按钮"，而是一个真实可点的取消入口。
 */
@Composable
fun HeroConnectButton(
    connected: Boolean,
    modifier: Modifier = Modifier,
    connecting: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    val grad = if (connected) c.okGrad else c.accentGrad
    val halo = if (connected) c.okBg else c.accentBg
    val ring = if (connected) c.ok else c.accent
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "pulseT",
    )
    Box(
        modifier = modifier
            .size(176.dp)
            .then(noRippleClickable(onClick))
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(halo, radius = r + 13.dp.toPx())
                drawCircle(
                    ring,
                    radius = r + 13.dp.toPx() + t * 10.dp.toPx(),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                    alpha = (1f - t) * 0.5f,
                )
            }
            .clip(CircleShape)
            .background(grad),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                if (connected) Icons.Outlined.PowerSettingsNew else Icons.Filled.Bolt,
                null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
            Text(
                when {
                    connected -> stringResource(R.string.v5_disconnect)
                    connecting -> stringResource(R.string.v5_cancel)
                    else -> stringResource(R.string.v5_connect)
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                color = Color.White,
            )
        }
    }
}

/** 提示横幅（info/warn/danger）。 */
@Composable
fun V5Banner(tone: ChipTone, text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = V5ThemeColors.current
    val (ink, bg) = c.chip(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            icon ?: if (tone == ChipTone.DANGER) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            null,
            modifier = Modifier.size(16.dp),
            tint = ink,
        )
        Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = ink)
    }
}

/** 清单行（图标/标题/副标题/值/尾部/箭头）。 */
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
    onClick: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(horizontal = 15.dp, vertical = 10.dp)
            .then(noRippleClickable(onClick)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (highlight) c.accentBg else c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(19.dp),
                    tint = if (danger) {
                        c.danger
                    } else if (highlight) {
                        c.accent
                    } else {
                        c.text2
                    },
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (danger) c.danger else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(it, fontSize = 11.5.sp, color = c.text3, modifier = Modifier.padding(top = 1.dp))
            }
        }
        value?.let {
            Text(
                it,
                fontSize = 13.sp,
                color = c.text2,
                fontFamily = if (valueMono) FontFamily.Monospace else null,
            )
        }
        trailing?.invoke(this)
        if (chevron) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp), tint = c.text3)
    }
}

/**
 * 账本行数据（label 左 / 等宽值 右）。
 *
 * [tone] 非空时整行加马卡龙底色（圆角 + 内外边距同步放大）——用于首页会话信息：
 * 形式沿用账本行（左标签 / 右等宽值），效果沿用瓷片（[MacaronTile] 的同色系底色与墨色）。
 */
data class LedgerData(
    val label: String,
    val value: String,
    val color: Color? = null,
    val icon: ImageVector? = null,
    val tone: TileTone? = null,
)

@Composable
fun V5Ledger(rows: List<LedgerData>, modifier: Modifier = Modifier) {
    val c = V5ThemeColors.current
    // 带底色时行与行之间要留缝，否则两行底色连成一片、看不出是两行
    val toned = rows.any { it.tone != null }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (toned) 8.dp else 0.dp)) {
        rows.forEachIndexed { i, r ->
            // 分隔线只在"相邻两行都没有底色"时画；带底色的行靠留白区分
            if (i > 0 && r.tone == null && rows[i - 1].tone == null) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
            val t = r.tone?.let { c.tile(it) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (t != null) Modifier.clip(RoundedCornerShape(12.dp)).background(t.bg) else Modifier)
                    .defaultMinSize(minHeight = if (t != null) 52.dp else 46.dp)
                    .padding(horizontal = if (t != null) 14.dp else 15.dp, vertical = if (t != null) 10.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 标签行**不能**带 weight：Row 先量非加权子项，若标签是加权项，超长值
                // （真机实测：IPv6 地址 39 字符）会先把剩余宽度吃光，标签被压成一个字一行
                // （"当前IP"竖排）——正是用户说的"显示不开"。现在反过来：标签占固定列宽
                // （最短 64dp、最长 140dp 用省略号收口），值取剩余宽度、可折行、右对齐。
                Row(
                    Modifier
                        .defaultMinSize(minWidth = 64.dp)
                        .widthIn(max = 140.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    r.icon?.let { Icon(it, null, modifier = Modifier.size(16.dp), tint = t?.ink ?: c.text2) }
                    Text(
                        r.label,
                        fontSize = 12.5.sp,
                        color = t?.ink ?: c.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    r.value,
                    fontSize = if (t != null) 15.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = r.color ?: c.text,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 细进度条（默认蓝渐变主进度）。 */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, brush: Brush? = null) {
    val c = V5ThemeColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(c.surface3),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(brush ?: c.accentGrad),
        )
    }
}

/** 底部面板（scrim + 26dp 顶圆角白面板）。 */
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
                .then(noRippleClickable {}),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 28.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.surface3),
            )
            stickerIcon?.let {
                Icon(
                    it,
                    null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .size(40.dp),
                    tint = c.accent,
                )
            }
            Text(
                title,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            sub?.let {
                Text(
                    it,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 7.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = c.text3,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** 面板单选项行：选中 = 蓝圈 + 蓝描边 + 右侧蓝徽章。 */
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accentBg else c.surface2)
            .then(if (selected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(19.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(c.accent)
                    } else {
                        Modifier.border(1.7.dp, c.text3, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(11.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            sub?.let { Text(it, fontSize = 11.5.sp, color = c.text3, modifier = Modifier.padding(top = 1.dp)) }
        }
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, null, modifier = Modifier.size(11.dp), tint = Color.White)
            }
        }
    }
}

/** Polaris 星标品牌图形（轨道环 + 四角星 + 橙色伴星）。 */
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
        drawCircle(Color(0xFFF0812B), radius = 2.8f * s, center = Offset(52.5f * s, 21f * s))
    }
}

/** 字母 P 头像。 */
@Composable
fun AvatarP(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(Color(0xFF6AA5FF), Color(0xFF2B5FF0)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("P", color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
    }
}
