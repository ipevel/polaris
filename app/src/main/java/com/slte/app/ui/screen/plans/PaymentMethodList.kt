// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.domain.model.PaymentMethod
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.RadioDot
import com.slte.app.ui.v5.noRippleClickable

/**
 * 支付方式选择（v5）：两列网格，选中项蓝底蓝圈。
 *
 * 行为与 v4 一致（点击选中 + 触感反馈 + 两列网格 + 单选圈）；外观换成 v5 语言：
 * 14dp 圆角、选中 = `accent` 底 + 白字、未选 = `surface2` 底 + 主字色。
 * 单选圈直接用 v5 现成的 [RadioDot]（v4 这里是手绘 Canvas，与 v5 组件重复）。
 */
@Composable
internal fun PaymentMethodList(
    methods: List<PaymentMethod>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        methods.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { method ->
                    PaymentMethodCell(
                        method = method,
                        selected = selectedId == method.id,
                        onClick = { onSelect(method.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun PaymentMethodCell(
    method: PaymentMethod,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
        modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) c.accent else c.surface2)
            .then(
                noRippleClickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(on = selected)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = method.name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
