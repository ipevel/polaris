// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.slte.app.R
import com.slte.app.ui.component.SlteRowCard
import com.slte.app.ui.component.SlteSwitch

@Composable
internal fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) = SlteRowCard(
    icon = icon,
    title = title,
    value = value,
    chevron = true,
    onClick = onClick,
)

@Composable
internal fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val stateDesc = if (checked) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off)
    SlteRowCard(
        icon = icon,
        title = title,

        modifier =
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            stateDescription = stateDesc
        },
        trailing = {
            SlteSwitch(
                checked = checked,
                enabled = enabled,
            )
        },
        onClick =
        if (enabled) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
        } else {
            null
        },
    )
}
