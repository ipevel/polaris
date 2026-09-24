// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/** 说明文案最大宽度：与空态同宽，保证两种状态切换时视觉不跳。 */
private val ErrorMessageMaxWidth = 230.dp

/**
 * 错误态：与空态同构（图标色块 + 说明 + 重试），仅配色换成危险色。
 *
 * 调用方传入的 message 已是本地化的平实文案，这里不再附加技术细节。
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // SlteIcons 未单独导出通用警告图标别名，OrderAbnormal 即 ErrorOutline 矢量。
        SlteStateTile(
            icon = SlteIcons.OrderAbnormal,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.lg))
        Text(
            text = message,
            style = SlteType.body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = ErrorMessageMaxWidth),
        )
        Spacer(modifier = Modifier.height(Dimens.gap.xl))
        SlteButton(
            text = stringResource(R.string.notice_retry),
            onClick = onRetry,
            style = SlteButtonStyle.Neutral,
        )
    }
}
