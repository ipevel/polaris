// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 三段式代理模式选择器（规则 / 全局 / 直连），卡内槽色分段控件。
 * 选中段用强调色淡底 + 强调色文字，未选中保持弱化文字；不再用实心强调色块压过主操作。
 */
@Composable
fun ProxyModeCard(
    proxyMode: String,
    onSelectMode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedShape = RoundedCornerShape(SlteRadii.inner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(selectedShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Dimens.gap.xs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PROXY_MODE_OPTIONS.forEach { option ->
            val selected = proxyMode == option.mode
            Text(
                text = stringResource(option.labelRes),
                style = SlteType.label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    SlteColors.current.accentInteractive
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(selectedShape)
                    .background(
                        if (selected) {
                            SlteColors.current.accentInteractiveBg
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { onSelectMode(option.mode) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}
