// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 加载态：轻量不定进度指示器 + 文案。
 *
 * 原先用 Lottie 旋转动画，需要解码 raw 资源且每帧重绘；换成系统级 indeterminate 指示器后
 * 只在加载期间存活，开销与列表滚动无冲突。
 */
@Composable
fun LoadingBox(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.loading),
) {
    Card(
        modifier = modifier.size(Dimens.loadingBoxSize),
        shape = SlteShapes.large,
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation =
        CardDefaults.cardElevation(
            defaultElevation = Dimens.loadingBoxElevation,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.loadingAnimSize),
                    color = SlteColors.current.accentInteractive,
                    strokeWidth = Dimens.strokeThick,
                )
                Spacer(modifier = Modifier.height(Dimens.loadingTextGap))
                Text(
                    text = message,
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 可点按关闭的遮罩层加载态：保留 scrim、点击关闭与返回键拦截。 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    message: String = stringResource(R.string.loading),
    onDismiss: (() -> Unit)? = null,
) {
    if (visible && onDismiss != null) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(initialAlpha = 0f),
        exit = fadeOut(targetAlpha = 0f),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = Dimens.loadingScrimAlpha))
                .clickable(
                    interactionSource = null,
                    indication = null,
                ) { onDismiss?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            LoadingBox(message = message)
        }
    }
}
