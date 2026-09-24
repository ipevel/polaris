// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.copyToClipboard

@Composable
fun InviteCodeCard(
    codes: List<InviteCodeInfo>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    context: android.content.Context,
) {
    val haptic = LocalHapticFeedback.current
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.invite_code_title),
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onGenerate()
                    },
                    enabled = !isGenerating,
                ) {
                    Icon(
                        imageVector = SlteIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.inviteCodeCopyIconSize),
                    )
                    Spacer(modifier = Modifier.width(Dimens.gap.xs))
                    Text(stringResource(R.string.invite_code_generate), style = SlteType.bodySmall)
                }
            }

            if (codes.isEmpty()) {
                Text(
                    text = stringResource(R.string.invite_code_empty),
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.gap.md),
                )
            } else {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                codes.forEach { code ->
                    InviteCodeItem(code = code, context = context)
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                }
            }
        }
    }
}

@Composable
private fun InviteCodeItem(
    code: InviteCodeInfo,
    context: android.content.Context,
) {
    val haptic = LocalHapticFeedback.current
    val toast = rememberToast()
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(Dimens.inviteCodeItemHeight)
            .clip(RoundedCornerShape(SlteRadii.inner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.inviteCodeItemPaddingH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = code.code,
            style = SlteType.value,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.invite_code_pv, code.pv),
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Dimens.gap.sm))
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                copyToClipboard(context, "invite_code", code.code)
                toast.show(R.string.invite_code_copied)
            },
            modifier = Modifier.size(Dimens.inviteCodeCopyBtnSize),
        ) {
            Icon(
                imageVector = SlteIcons.Copy,
                contentDescription = stringResource(R.string.invite_code_copy),
                modifier = Modifier.size(Dimens.inviteCodeCopyIconSize),
                tint = SlteColors.current.accentInteractive,
            )
        }
    }
}
