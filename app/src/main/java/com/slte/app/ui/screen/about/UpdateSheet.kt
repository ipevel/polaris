// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.slte.app.R
import com.slte.app.ui.component.AnimatedSticker
import com.slte.app.ui.component.LocaleAwareAlertDialog
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.Stickers

/**
 * 发现新版本的底部弹窗（非强制）。
 *
 * 「立即更新」已改为「前往 GitHub 下载」：点击后跳到 Release 页由用户自行下载覆盖安装，
 * 因此额外给出「不要先卸载」的提示，避免用户卸载重装导致配置清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateUiState.Available,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onLater: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    SlteSheet(
        onDismiss = onDismiss,
        dismissible = true,
        compact = true,
        title = stringResource(R.string.update_title),
        header = {
            AnimatedSticker(
                assetPath = Stickers.UPDATE,
                modifier = Modifier.size(Dimens.stateStickerSize),
            )
        },
    ) {
        Text(
            text = stringResource(R.string.update_version_label, state.versionName),
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.lg))

        Text(
            text = state.changelogTitle ?: stringResource(R.string.update_changelog_title_default),
            style = SlteType.title,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.sm))

        Text(
            text = state.changelog ?: stringResource(R.string.update_changelog_default),
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.lg))

        Text(
            text = stringResource(R.string.update_keep_config_hint),
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.sm))

        SlteButton(
            text = stringResource(R.string.update_now),
            onClick = onOpenRelease,
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Primary,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.sm))

        SlteButton(
            text = stringResource(R.string.update_later),
            onClick = onLater,
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Neutral,
        )
    }
}

@Composable
fun ForceUpdateDialog(
    onOpenRelease: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LocaleAwareAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        icon = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedSticker(
                    assetPath = Stickers.FORCE_UPDATE,
                    modifier = Modifier.size(Dimens.logoSize),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.update_force_title),
                style = SlteType.heading,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.update_keep_config_hint),
                style = SlteType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            SlteButton(
                text = stringResource(R.string.update_now),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenRelease()
                },
                modifier = Modifier.fillMaxWidth(),
                style = SlteButtonStyle.Primary,
            )
        },
    )
}
