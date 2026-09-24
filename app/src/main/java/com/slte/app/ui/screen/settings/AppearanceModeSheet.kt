// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.data.local.ThemeMode
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

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

    SlteSheet(
        title = stringResource(R.string.settings_appearance),
        onDismiss = onDismiss,
    ) {
        AppearanceMode.entries.forEach { mode ->
            val selected = currentMode == mode
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(mode)
                        },
                    ).padding(vertical = Dimens.gap.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors =
                    RadioButtonDefaults.colors(
                        selectedColor = SlteColors.current.accentInteractive,
                    ),
                )
                Text(
                    text = stringResource(mode.labelRes),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    style = SlteType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Dimens.gap.sm),
                )
            }
        }
    }
}
