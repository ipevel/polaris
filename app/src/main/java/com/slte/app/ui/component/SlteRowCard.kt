// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 清单行（不带卡片外壳）。用于把多行放进**同一张卡**里，行间用 1dp 发丝线分隔，
 * 替代原先「一张卡只装一行、多行就堆成一片卡片」的做法（列表页卡片边界只有 1.25:1，几乎分不清）。
 *
 * 用法：`SlteCard { SlteRow(...); SlteRow(topDivider = true, ...) }`
 */
@Composable
fun SlteRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueMono: Boolean = false,
    iconTint: Color? = null,
    iconOn: Boolean = false,
    danger: Boolean = false,
    chevron: Boolean = false,
    topDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tone = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val secondary = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val tint =
        iconTint
            ?: when {
                danger -> MaterialTheme.colorScheme.error
                iconOn -> SlteColors.current.accentInteractive
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
    val tileBg =
        when {
            danger -> MaterialTheme.colorScheme.errorContainer
            iconOn -> SlteColors.current.accentInteractiveBg
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    val haptic = LocalHapticFeedback.current
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.size.touchTarget)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )

    @Composable
    fun Content() {
        Row(
            modifier = rowModifier.padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconBadgeSize)
                    .background(tileBg, RoundedCornerShape(SlteRadii.inner)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.icon.md),
                    tint = tint,
                )
            }
            Spacer(modifier = Modifier.width(Dimens.gap.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SlteType.body.copy(fontWeight = FontWeight.Medium),
                    color = tone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.size(Dimens.gap.xs))
                    Text(
                        text = subtitle,
                        style = SlteType.label,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Text(
                    text = value,
                    style = if (valueMono) SlteType.valueSmall else SlteType.body.copy(fontWeight = FontWeight.Medium),
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
            if (chevron) {
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
                Icon(
                    imageVector = SlteIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.icon.md),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Content()
    }
}

/** 单行自带卡片外壳的形态（用于「我的」页里独立成行的项）。 */
@Composable
fun SlteRowCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SlteCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        SlteRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            value = value,
            iconTint = iconTint,
            chevron = chevron,
            trailing = trailing,
        )
    }
}
