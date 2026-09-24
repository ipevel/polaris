// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/** 状态图标色块边长：56dp 既托得住 24dp 图标，也不会像原先 80dp 贴纸那样成为页面主角。 */
private val StateTileSize = 56.dp

/** 描述文案最大宽度：超过 230dp 的说明文字一行读不完，反而更难扫。 */
private val StateDescriptionMaxWidth = 230.dp

/**
 * 空态：静态图标色块 + 标题 + 可选描述 + 可选操作。
 *
 * 原先用 80dp 无限循环的 Lottie 贴纸，在列表页会一直动，与 v4 收敛的视觉语言冲突。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SlteStateTile(
            icon = SlteIcons.Folder,
            container = SlteColors.current.accentInteractiveBg,
            content = SlteColors.current.accentInteractive,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.lg))
        Text(
            text = title,
            style = SlteType.body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text = description,
                style = SlteType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = StateDescriptionMaxWidth),
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(Dimens.gap.xl))
            SlteButton(
                text = actionText,
                onClick = onAction,
                style = SlteButtonStyle.Neutral,
            )
        }
    }
}

/** 状态图标色块：静态矢量 + 圆角底色，供空态与错误态复用（两者仅配色不同）。 */
@Composable
internal fun SlteStateTile(
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    Box(
        modifier =
        Modifier
            .size(StateTileSize)
            .background(container, RoundedCornerShape(SlteRadii.card)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.icon.lg),
            tint = content,
        )
    }
}
