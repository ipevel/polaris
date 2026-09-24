// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.notice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.domain.model.Notice
import com.slte.app.ui.component.RichText
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 徽标统一高度：24dp = 16dp 行高 + 上下各 4dp 内边距，胶囊形。 */
private val ChipMinHeight = 24.dp

/**
 * 公告行（不带卡片外壳）：由调用方放进同一张 [com.slte.app.ui.component.SlteCard]，
 * 行间以 1dp 发丝线分隔（`topDivider = true`）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeRow(
    notice: Notice,
    topDivider: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(
                    horizontal = Dimens.gap.lg,
                    vertical = Dimens.gap.md,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice.title,
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Icon(
                    imageVector = SlteIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.icon.md),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (notice.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.noticeTagSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gap.xs),
                ) {
                    notice.tags.forEach { tag ->
                        NoticeTag(text = tag)
                    }
                }
            }

            val plainBody =
                remember(notice.body) {
                    notice.body.replace(Regex("<[^>]*>"), "").trim()
                }
            if (plainBody.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                Text(
                    text = plainBody,
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = Dimens.noticeBodyMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text = FormatUtils.formatDate(notice.createdAt),
                style = SlteType.valueSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 公告标签：胶囊形强调色徽标（原为 12dp 圆角，与「胶囊」语言不符）。 */
@Composable
internal fun NoticeTag(text: String) {
    Surface(
        shape = RoundedCornerShape(SlteRadii.pill),
        color = SlteColors.current.accentInteractiveBg,
    ) {
        Text(
            text = text,
            style = SlteType.caption,
            color = SlteColors.current.accentInteractive,
            modifier =
            Modifier
                .defaultMinSize(minHeight = ChipMinHeight)
                .padding(
                    horizontal = Dimens.noticeTagPaddingH,
                    vertical = Dimens.gap.xs,
                ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeDetailSheet(
    notice: Notice,
    onDismiss: () -> Unit,
) {
    SlteSheet(
        onDismiss = onDismiss,
        title = notice.title,
    ) {
        if (notice.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.noticeTagSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap.xs),
            ) {
                notice.tags.forEach { tag -> NoticeTag(text = tag) }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = FormatUtils.formatDate(notice.createdAt),
            style = SlteType.valueSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        RichText(
            text = notice.body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
