// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.noRippleClickable

/**
 * 提现方式选择（v5）。
 *
 * 保留下来的行为：点击展开下拉、失败时点击即重试、加载中/失败/无可选方式三种提示文案、
 * 选中项打勾、列表限高滚动。
 *
 * 与 v4 的两处差异（均为 v5 语言，不影响行为）：
 * 1. 外壳从 Material `Surface(onClick=...)`（带涟漪 + Material 描边）换成 v5 圆角块 + 无涟漪点击；
 * 2. 下拉菜单仍用 Material3 `DropdownMenu`——它承担锚点定位、点击外部关闭、返回键关闭与
 *    无障碍语义，换成自绘弹层要重写这些；只把容器配色/圆角换成 v5 取值。
 *
 * 已知偏离：v4 用 `slteInputSize.Compact`（`Dimens.size.button` 高）而以 `.height(...)` 固定高度，
 * 这里是 `defaultMinSize`，因此在既有的加载/失败/空文案下高度仍由同一常量兜底。
 */
@Composable
internal fun WithdrawMethodField(
    methods: List<String>,
    selected: String,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val c = V5ThemeColors.current

    val enabled = !isLoading && (failed || methods.isNotEmpty())
    val hint =
        when {
            isLoading -> stringResource(R.string.invite_withdraw_methods_loading)
            failed -> stringResource(R.string.invite_withdraw_methods_failed)
            methods.isEmpty() -> stringResource(R.string.invite_withdraw_methods_empty)
            else -> stringResource(R.string.invite_withdraw_method_hint)
        }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    // 点击语义：失败态 = 重试；正常态 = 展开下拉；不可用态 = 什么都不做。
    // 抽成局部变量是因为 `noRippleClickable` 的形参是可空函数类型，显式变量让推导无歧义。
    val clickAction: (() -> Unit)? = when {
        !enabled -> null
        failed -> onRetry
        else -> ({ expanded = true })
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val anchorWidth = maxWidth
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(shape)
                .background(c.surface2)
                .then(noRippleClickable(clickAction))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SlteIcons.WithdrawMethod,
                contentDescription = stringResource(R.string.invite_withdraw_method),
                modifier = Modifier.size(18.dp),
                tint = if (enabled) c.accent else c.text3,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = selected.ifBlank { hint },
                fontSize = 14.sp,
                color = if (selected.isBlank()) c.text3 else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) SlteIcons.ExpandLess else SlteIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = c.text3,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(anchorWidth).heightIn(max = 280.dp),
            shape = shape,
            containerColor = c.surface,
            border = BorderStroke(1.dp, c.hairline),
            shadowElevation = 6.dp,
        ) {
            methods.forEach { method ->
                val isSelected = method == selected
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            noRippleClickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(method)
                                expanded = false
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = method,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = SlteIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = c.accent,
                        )
                    }
                }
            }
        }
    }
}
