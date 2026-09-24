// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.domain.model.PaymentMethod
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
internal fun PaymentMethodList(
    methods: List<PaymentMethod>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
    ) {
        methods.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.material3.Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.height(Dimens.paymentMethodCellHeight),
        shape = RoundedCornerShape(SlteRadii.inner),
        color =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.gap.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.paymentMethodDotAlpha)
                }
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(Dimens.radioDotSize),
            ) {
                val radiusOuter = Dimens.radioDotInnerSize.toPx()
                val radiusInner = Dimens.radioDotGap.toPx()
                drawCircle(
                    color = dotColor,
                    radius = radiusOuter,
                    style =
                    androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (selected) Dimens.strokeThick.toPx() else Dimens.strokeMedium.toPx(),
                    ),
                )
                if (selected) {
                    drawCircle(
                        color = dotColor,
                        radius = radiusInner,
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimens.gap.md))
            Text(
                text = method.name,
                style = SlteType.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
