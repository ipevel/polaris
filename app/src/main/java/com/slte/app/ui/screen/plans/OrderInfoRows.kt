// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.ui.theme.V5ThemeColors

/** 订单信息行：左标签 / 右数值。金额一律等宽（同列小数点不错位）。 */
@Composable
internal fun OrderInfoRow(
    label: String,
    value: String,
    isValueEmphasize: Boolean = false,
    valueMono: Boolean = true,
) {
    val c = V5ThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = c.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            fontSize = if (valueMono) 13.5.sp else 13.sp,
            fontWeight = if (isValueEmphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (isValueEmphasize) c.text else c.text2,
            maxLines = 1,
        )
    }
}

/** 订单信息行之间的发丝线（v5 令牌）。 */
@Composable
internal fun OrderInfoDivider() {
    HorizontalDivider(thickness = 1.dp, color = V5ThemeColors.current.hairline2)
}

/** 价格行：左标签 / 右等宽数值，`isBold` 用于最终价。 */
@Composable
internal fun PriceRow(
    label: String,
    value: String,
    isBold: Boolean = false,
) {
    val c = V5ThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = c.text2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isBold) c.accent else c.text,
        )
    }
}
