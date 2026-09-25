// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.slte.app.R
import com.slte.app.ui.component.SlteRow
import com.slte.app.ui.component.SlteSwitch

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueMono: Boolean = false,
    subtitle: String? = null,
    topDivider: Boolean = false,
    onClick: () -> Unit,
) = SlteRow(
    icon = icon,
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    value = value,
    valueMono = valueMono,
    chevron = true,
    topDivider = topDivider,
    onClick = onClick,
)

@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    topDivider: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateDesc = if (checked) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off)
    SlteRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
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
        topDivider = topDivider,
        // 同步中（enabled=false）行不可点，与关闭态开关一致：既不乐观切换也不触发写请求。
        onClick =
        if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
    )
}
