// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
            // selectableGroup：把整列声明成一个单选组，屏幕阅读器才会播报「N 项中的第 M 项」
            // 并在组内做方向键导航；Role.RadioButton 只给单行角色，管不到组。
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TunStackMode.entries.forEach { mode ->
                val selected = currentMode == mode
                SheetOption(
                    title = stringResource(mode.labelRes),
                    sub = stringResource(mode.descRes),
                    selected = selected,
                    modifier = Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(mode)
                        },
                    ),
                )
            }
        }
    }
}
