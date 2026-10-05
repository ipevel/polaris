// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.v5.SheetOption

/**
 * 代理模式面板（v6 iOS 语言）：单选对勾行。
 *
 * 行为不变：点选一行 → 触感反馈 → 回调 onSelect → 关闭面板。
 */
@Composable
fun ProxyModeSheet(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    SlteSheet(
        title = stringResource(R.string.action_proxy_mode),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PROXY_MODE_OPTIONS.forEach { option ->
                val selected = currentMode == option.mode
                SheetOption(
                    title = stringResource(option.labelRes),
                    sub = stringResource(option.descRes),
                    selected = selected,
                    modifier =
                        Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(option.mode)
                            onDismiss()
                        },
                )
            }
        }
    }
}
