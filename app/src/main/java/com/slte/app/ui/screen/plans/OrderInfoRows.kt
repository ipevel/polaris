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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/** 订单信息行：左标签 / 右数值。金额一律走等宽数值样式，避免小数点错位。 */
@Composable
internal fun OrderInfoRow(
    label: String,
    value: String,
    isValueEmphasize: Boolean = false,
    valueMono: Boolean = true,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gap.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(Dimens.gap.sm))
        Text(
            text = value,
            style = if (valueMono) SlteType.valueSmall else SlteType.bodySmall,
            color =
            if (isValueEmphasize) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
internal fun OrderInfoDivider() {
    HorizontalDivider(
        thickness = Dimens.dividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
internal fun PriceRow(
    label: String,
    value: String,
    isBold: Boolean = false,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gap.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = SlteType.valueSmall,
            color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
