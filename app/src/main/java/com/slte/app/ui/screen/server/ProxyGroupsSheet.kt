// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Constants
import com.slte.app.utils.Dimens

/**
 * 「可分流的选择」：列出内核中全部策略组，可逐组指定出口节点。
 *
 * 直接内嵌在节点列表顶部（LazyColumn item），不再通过弹层入口。
 */

@Composable
internal fun ProxyGroupsHint(
    message: String,
    showLoading: Boolean,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gap.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showLoading) {
                LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
            }
            Text(
                text = message,
                style = SlteType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProxyGroupCard(
    group: KernelProxyGroupInfo,
    isTesting: Boolean,
    onSelect: (String, String) -> Unit,
    onTest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.cardElevation,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Dimens.gap.xl, vertical = Dimens.gap.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = SlteType.title,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BoldNameText(
                        text = group.now?.takeIf { it.isNotBlank() } ?: stringResource(R.string.proxy_group_type_select),
                        style = SlteType.label,
                        fontWeight = FontWeight.Medium,
                        color = SlteColors.current.accentInteractive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                MemberCountBadge(count = group.members.size)

                if (isTesting) {
                    LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.md))
                } else {
                    Icon(
                        imageVector = SlteIcons.SpeedTest,
                        contentDescription = stringResource(R.string.proxy_group_test),
                        tint = SlteColors.current.accentInteractive,
                        modifier =
                        Modifier
                            .size(Dimens.icon.md)
                            .clickable(onClick = onTest),
                    )
                }

                Spacer(modifier = Modifier.width(Dimens.gap.sm))

                Icon(
                    imageVector = if (expanded) SlteIcons.ExpandLess else SlteIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.icon.md),
                )
            }

            if (expanded) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.gap.xl, vertical = Dimens.gap.md),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
                ) {
                    group.members.forEach { member ->
                        MemberCard(
                            member = member,
                            selected = member.name == group.now,
                            selectable = group.selectable,
                            onClick = { onSelect(group.name, member.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gap.lg))
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: KernelProxyMember,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable && !selected, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color =
        if (selected) {
            SlteColors.current.accentInteractiveBg
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoldNameText(
                text = member.name,
                style = SlteType.body,
                fontWeight = FontWeight.Medium,
                color =
                if (selected) {
                    SlteColors.current.accentInteractive
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (member.isGroup) {
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                GroupBadge()
            }
            member.delay?.let { delay ->
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Text(
                    text =
                    if (delay >= Constants.DELAY_TIMEOUT) {
                        stringResource(R.string.server_timeout)
                    } else {
                        stringResource(R.string.format_delay_ms, delay)
                    },
                    style = SlteType.bodySmall,
                    color = latencyColor(delay),
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Icon(
                    imageVector = SlteIcons.Check,
                    contentDescription = null,
                    tint = SlteColors.current.accentInteractive,
                    modifier = Modifier.size(Dimens.icon.sm),
                )
            }
        }
    }
}

@Composable
private fun MemberCountBadge(count: Int) {
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.gap.sm, vertical = Dimens.gap.xs),
    ) {
        Text(
            text = count.toString(),
            style = SlteType.label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Anywhere-style 胶囊形 TagBadge。 */
@Composable
private fun GroupBadge() {
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(50))
            .background(SlteColors.current.statusNeutralBg)
            .padding(horizontal = Dimens.gap.sm, vertical = Dimens.gap.xs),
    ) {
        Text(
            text = stringResource(R.string.proxy_group_badge),
            style = SlteType.label,
            fontWeight = FontWeight.Medium,
            color = SlteColors.current.statusNeutral,
        )
    }
}

/** 解析 Clash 风格 **粗体** 标记并渲染。 */
@Composable
private fun BoldNameText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
    color: androidx.compose.ui.graphics.Color,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
) {
    val boldPattern = remember { Regex("\\*\\*(.+?)\\*\\*") }
    val annotated = remember(text, color) {
        buildAnnotatedString {
            var last = 0
            boldPattern.findAll(text).forEach { match ->
                if (match.range.first > last) {
                    append(text.substring(last, match.range.first))
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                    append(match.groupValues[1])
                }
                last = match.range.last + 1
            }
            if (last < text.length) {
                append(text.substring(last))
            }
        }
    }
    Text(
        text = annotated,
        style = style.copy(fontWeight = fontWeight, color = color),
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

internal fun proxyGroupTypeLabelRes(type: String): Int = when (type.lowercase()) {
    "selector" -> R.string.proxy_group_type_select
    "urltest" -> R.string.proxy_group_type_auto
    "fallback" -> R.string.proxy_group_type_fallback
    "loadbalance" -> R.string.proxy_group_type_balance
    "relay" -> R.string.proxy_group_type_relay
    else -> R.string.proxy_group_type_unknown
}

/** Anywhere 风格延迟分级着色：绿 <300ms / 黄 300-499ms / 红 ≥500ms，暗色主题自动适配 */
@Composable
private fun latencyColor(ms: Int): Color = when {
    ms < 300 -> SlteColors.current.statusSuccess
    ms < 500 -> SlteColors.current.statusSlow
    else -> SlteColors.current.statusDanger
}
