// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.v5.SheetOption
import com.slte.app.ui.v5.V5Sheet

enum class TunStackMode(
    val value: String,
    val labelRes: Int,
    val descRes: Int,
) {
    SYSTEM("system", R.string.settings_tun_stack_system, R.string.settings_tun_stack_system_desc),
    GVISOR("gvisor", R.string.settings_tun_stack_gvisor, R.string.settings_tun_stack_gvisor_desc),
    MIXED("mixed", R.string.settings_tun_stack_mixed, R.string.settings_tun_stack_mixed_desc),
    ;

    companion object {
        val DEFAULT = SYSTEM

        fun fromValue(value: String): TunStackMode = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TunStackModeSheet(
    currentMode: TunStackMode,
    onDismiss: () -> Unit,
    onSelect: (TunStackMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    V5Sheet(
        title = stringResource(R.string.settings_tun_stack),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { mode ->
                val selected = currentMode == mode
                SheetOption(
                    title = stringResource(mode.labelRes),
                    sub = stringResource(mode.descRes),
                    selected = selected,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(mode)
                    },
                )
            }
        }
    }
}
