// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors

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
            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StateTile(SlteIcons.Folder, c.accent.copy(alpha = 0.14f), c.accent)
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = c.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = StateTextMaxWidth),
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
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
) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StateTile(SlteIcons.OrderAbnormal, c.danger.copy(alpha = 0.14f), c.danger)
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = StateTextMaxWidth),
            )
            Spacer(Modifier.height(18.dp))
            V5Button(
                text = retryText,
                style = ButtonStyle.TONAL,
                // 与空态按钮同规格：三态之间切换时按钮尺寸/圆角不跳变。
                small = true,
                onClick = onRetry,
            )
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = c.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(text = text, fontSize = 12.5.sp, color = c.text3)
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
            .background(container, RoundedCornerShape(18.dp)),
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
