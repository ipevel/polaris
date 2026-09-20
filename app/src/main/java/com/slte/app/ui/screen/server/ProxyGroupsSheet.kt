package com.slte.app.ui.screen.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteShapes
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
        shape = SlteShapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.cardElevation,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(Dimens.gap.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = SlteType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(proxyGroupTypeLabelRes(group.type)),
                        style = SlteType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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

            Text(
                text = stringResource(R.string.proxy_group_now, group.now ?: Constants.PLACEHOLDER_DASH),
                style = SlteType.label,
                color = SlteColors.current.accentInteractive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.gap.lg)
                    .padding(bottom = Dimens.gap.sm),
            )

            if (expanded) {
                group.members.forEach { member ->
                    val selected = member.name == group.now
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = group.selectable) {
                                if (!selected) onSelect(group.name, member.name)
                            }.padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = member.name,
                            style = SlteType.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
                        member.delay?.let { delay ->
                            Text(
                                text =
                                if (delay >= Constants.DELAY_TIMEOUT) {
                                    stringResource(R.string.server_timeout)
                                } else {
                                    stringResource(R.string.format_delay_ms, delay)
                                },
                                style = SlteType.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(Dimens.gap.sm))
                        }
                        if (selected) {
                            Icon(
                                imageVector = SlteIcons.Check,
                                contentDescription = null,
                                tint = SlteColors.current.accentInteractive,
                                modifier = Modifier.size(Dimens.icon.sm),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
            }
        }
    }
}

internal fun proxyGroupTypeLabelRes(type: String): Int = when (type.lowercase()) {
    "selector" -> R.string.proxy_group_type_select
    "urltest" -> R.string.proxy_group_type_auto
    "fallback" -> R.string.proxy_group_type_fallback
    "loadbalance" -> R.string.proxy_group_type_balance
    "relay" -> R.string.proxy_group_type_relay
    else -> R.string.proxy_group_type_unknown
}
