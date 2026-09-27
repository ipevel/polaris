// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5Radius
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type

/**
 * v5 状态三件套（空 / 错 / 加载）。
 *
 * 为什么单独做一套：v4 的 `EmptyState`/`ErrorState`/`LoadingBox` 用的是 v4 语言
 * （8dp 圆角白卡、Material 实心按钮、`SlteType` 字号阶梯），把它们塞进 v5 页面就会
 * 在同一个屏幕上出现两套设计语言——这正是本轮"全部 V5"要消除的东西。
 *
 * 统一约定：22dp 圆角卡片（[V5CardFlat]）+ v5 字号/取色 + TONAL 按钮，与节点页
 * 「分组加载中」的既有写法同构。
 */

private val StateTileSize = 56.dp

private val StateTextMaxWidth = 240.dp

/** 空态：图标色块 + 标题 + 可选说明 + 可选操作。 */
@Composable
fun V5EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = V5Spacing.dp26, horizontal = V5Spacing.dp16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StateTile(SlteIcons.Folder, c.accent.copy(alpha = 0.14f), c.accent)
            Spacer(Modifier.height(V5Spacing.dp14))
            Text(
                text = title,
                fontSize = V5Type.sp13_5,
                fontWeight = FontWeight.Medium,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Spacer(Modifier.height(V5Spacing.dp6))
                Text(
                    text = description,
                    fontSize = V5Type.sp11_5,
                    color = c.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = StateTextMaxWidth),
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(V5Spacing.dp18))
                V5Button(
                    text = actionText,
                    style = ButtonStyle.TONAL,
                    // 状态卡片上的按钮统一小号（38dp 高、12dp 圆角）：正文按钮 46dp 在空/错态里
                    // 会显得比它承载的文案还重，页面主体（居中图标 + 一行字）被按钮抢走焦点。
                    small = true,
                    onClick = onAction,
                )
            }
        }
    }
}

/** 错误态：与空态同构，仅配色换成危险色（避免状态切换时视觉跳动）。 */
@Composable
fun V5ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = stringResource(R.string.v5_state_retry),
    detail: String? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = V5Spacing.dp26, horizontal = V5Spacing.dp16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StateTile(SlteIcons.OrderAbnormal, c.danger.copy(alpha = 0.14f), c.danger)
            Spacer(Modifier.height(V5Spacing.dp14))
            Text(
                text = message,
                fontSize = V5Type.sp13_5,
                fontWeight = FontWeight.Medium,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = StateTextMaxWidth),
            )
            // 具体原因（HTTP 状态码 / 服务端 message）：通用文案说不出问题在哪，
            // 诊断成本极高，所以错误态必须能承载一行细节。
            if (detail != null) {
                Spacer(Modifier.height(V5Spacing.dp6))
                Text(
                    text = detail,
                    fontSize = V5Type.sp11_5,
                    color = c.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = StateTextMaxWidth),
                )
            }
            Spacer(Modifier.height(V5Spacing.dp18))
            V5Button(
                text = retryText,
                style = ButtonStyle.TONAL,
                // 与空态按钮同规格：三态之间切换时按钮尺寸/圆角不跳变。
                small = true,
                onClick = onRetry,
            )
            // 次操作（如「导出诊断」）用 GHOST：错误卡片的主行动永远是重试，
            // 导出不能被画成同等重量的第二个主按钮。
            if (secondaryText != null && onSecondary != null) {
                Spacer(Modifier.height(V5Spacing.dp8))
                V5Button(
                    text = secondaryText,
                    style = ButtonStyle.GHOST,
                    small = true,
                    onClick = onSecondary,
                )
            }
        }
    }
}

/** 加载态：v5 卡片 + 转圈 + 文案（与节点页「正在加载策略组…」同构）。 */
@Composable
fun V5LoadingState(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.v5_state_loading),
) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = V5Spacing.dp26),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(V5Spacing.dp22), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = c.accent,
                    strokeWidth = V5Spacing.dp2,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(V5Spacing.dp12))
            Text(text = text, fontSize = V5Type.sp12_5, color = c.text3)
        }
    }
}

/**
 * 不滚动内容的状态脚手架：把空/错/加载三态卡片在视口里居中，同时**保持可滚动**。
 *
 * 必须有这一层：[V5PullRefresh] 靠子节点的嵌套滚动接收下拉手势，直接放一个 `Box`
 * 会让空态/错态页面下拉失效。此前公告/工单/订单/套餐四页各抄了一份私有实现
 * （2026-09-27 去重为共享实现）。
 */
@Composable
fun V5StateScrollable(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StateTile(
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    Box(
        modifier =
        Modifier
            .size(StateTileSize)
            .background(container, RoundedCornerShape(V5Radius.r18)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = content,
        )
    }
}
