// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.utils.Dimens

@Composable
fun SlteSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val trackOff = scheme.outlineVariant
    val trackOn = scheme.primaryContainer
    val thumbOff = scheme.surface
    val thumbOn = scheme.primary
    val stateDesc = if (checked) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off)

    val offset = if (checked) Dimens.switchTrackWidth - Dimens.switchThumbSize - Dimens.switchThumbPadding * 2 else 0.dp

    val interactiveModifier =
        if (onCheckedChange == null) {
            Modifier.clearAndSetSemantics {}
        } else {
            Modifier
                .semantics {
                    role = Role.Switch
                    stateDescription = stateDesc
                }.toggleable(value = checked, enabled = enabled, role = Role.Switch) { onCheckedChange(it) }
        }

    Box(
        modifier =
        modifier
            .size(width = Dimens.switchTrackWidth, height = Dimens.switchTouchHeight)
            .then(interactiveModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(width = Dimens.switchTrackWidth, height = Dimens.switchTrackHeight)
                .clip(RoundedCornerShape(Dimens.switchTrackHeight / 2))
                .background(if (checked) trackOn else trackOff)
                .padding(Dimens.switchThumbPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                Modifier
                    .offset(x = offset)
                    .size(Dimens.switchThumbSize)
                    .clip(CircleShape)
                    .background(if (checked) thumbOn else thumbOff),
            )
        }
    }
}
