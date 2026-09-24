// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.slte.app.R
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/**
 * 套餐用量卡（首页第三层）：标题 + 状态徽标，已用 · 总额 + 百分比，进度条，到期文案 + 操作按钮。
 * v4 起动作按钮降级为强调色淡底（不再是全页最抢眼的实心按钮），状态徽标也去掉光晕。
 */
@Composable
fun UsageCard(
    planName: String,
    usedBytes: Long,
    totalBytes: Long,
    isValid: Boolean,
    hasPlan: Boolean,
    daysUntilExpired: Int?,
    modifier: Modifier = Modifier,
    expiredAtDate: String? = null,
    actionText: String = stringResource(R.string.plan_renew_button),
    actionEnabled: Boolean = hasPlan,
    onAction: () -> Unit = {},
) {
    val percent =
        if (totalBytes > 0L) {
            ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

    var animTarget by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animTarget,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress_anim",
    )
    LaunchedEffect(percent) {
        animTarget = percent / 100f
    }

    SlteCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = planName.ifBlank { stringResource(R.string.dashboard_usage_title) },
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                UsageBadge(isValid = isValid, hasPlan = hasPlan)
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            val usedPrefix = stringResource(R.string.plan_used_prefix)
            val totalPrefix = stringResource(R.string.plan_total_prefix)
            val separator = stringResource(R.string.plan_separator)
            val usageAccent = SlteColors.current.accentInteractive
            // 数值部分单独走等宽，避免「已用 / 总额」两列数字宽度抖动
            val monoValue = SpanStyle(fontFamily = FontFamily.Monospace)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                    buildAnnotatedString {
                        append("$usedPrefix ")
                        withStyle(
                            monoValue.copy(color = usageAccent, fontWeight = FontWeight.SemiBold),
                        ) {
                            append(FormatUtils.traffic(usedBytes))
                        }
                        append(" $separator ")
                        append("$totalPrefix ")
                        withStyle(monoValue) {
                            append(FormatUtils.traffic(totalBytes))
                        }
                    },
                    style = SlteType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.plan_percent, percent),
                    style = SlteType.valueSmall,
                    color =
                    if (hasPlan && !isValid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        usageAccent
                    },
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.dashboardUsageBarHeight)
                    .clip(RoundedCornerShape(SlteRadii.pill)),
                color =
                if (hasPlan && !isValid) {
                    MaterialTheme.colorScheme.error
                } else {
                    usageAccent
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasPlan) {
                        Text(
                            text =
                            when {
                                daysUntilExpired == null -> stringResource(R.string.plan_no_expiry)
                                daysUntilExpired > 0 ->
                                    pluralStringResource(
                                        R.plurals.plan_expire_days_short,
                                        daysUntilExpired,
                                        daysUntilExpired,
                                    )
                                else -> stringResource(R.string.plan_expired_today)
                            },
                            fontWeight = FontWeight.Light,
                            style = SlteType.label,
                            color =
                            if (isValid) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.plan_empty),
                            fontWeight = FontWeight.Light,
                            style = SlteType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SlteButton(
                    text = actionText,
                    onClick = onAction,
                    style = SlteButtonStyle.Medium,
                    enabled = actionEnabled,
                    containerColor = SlteColors.current.accentInteractiveBg,
                    contentColor = usageAccent,
                )
            }
        }
    }
}

@Composable
private fun UsageBadge(
    isValid: Boolean,
    hasPlan: Boolean = true,
) {
    val bg =
        when {
            !hasPlan -> MaterialTheme.colorScheme.surfaceVariant
            isValid -> SlteColors.current.statusSuccessBg
            else -> SlteColors.current.statusDangerBg
        }
    val fg =
        when {
            !hasPlan -> MaterialTheme.colorScheme.onSurfaceVariant
            isValid -> SlteColors.current.statusSuccess
            else -> SlteColors.current.statusDanger
        }

    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(SlteRadii.pill))
            .background(bg)
            .padding(horizontal = Dimens.gap.md, vertical = Dimens.planStatusPaddingV),
    ) {
        Text(
            text =
            when {
                !hasPlan -> stringResource(R.string.dashboard_no_plan_badge)
                isValid -> stringResource(R.string.dashboard_usage_valid)
                else -> stringResource(R.string.plan_status_expired)
            },
            fontWeight = FontWeight.SemiBold,
            style = SlteType.caption,
            color = fg,
        )
    }
}
