// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.data.local.ThemeMode
import com.slte.app.ui.v5.SheetOption
import com.slte.app.ui.v5.V5Sheet

/** 外观三态：与 LanguageMode 同构，供「其他设置」里的「外观」行展开选择。 */
enum class AppearanceMode(
    val mode: ThemeMode,
    val labelRes: Int,
) {
    SYSTEM(ThemeMode.SYSTEM, R.string.topbar_auto_mode),
    LIGHT(ThemeMode.LIGHT, R.string.topbar_light_mode),
    DARK(ThemeMode.DARK, R.string.topbar_dark_mode),
    ;

    companion object {

        fun fromThemeMode(mode: ThemeMode): AppearanceMode = entries.firstOrNull { it.mode == mode } ?: SYSTEM
    }
}

@Composable
internal fun AppearanceModeSheet(
    currentMode: AppearanceMode,
    onDismiss: () -> Unit,
    onSelect: (AppearanceMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    V5Sheet(
        title = stringResource(R.string.settings_appearance),
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
                    selected = selected,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(mode)
                    },
                )
            }
        }
    }
}
