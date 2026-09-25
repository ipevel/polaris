// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.slte.app.R
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SlteInputSize
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 添加自定义分流组弹层：名称 + 规则集 URL（https）+ 匹配行为。
 * 规则集由内核按 interval 直接抓取与刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRuleGroupSheet(
    state: CustomGroupState.Editing,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onBehaviorChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SlteSheet(
        title = stringResource(R.string.routing_custom_add),
        onDismiss = { if (!state.submitting) onDismiss() },
    ) {
        SlteInput(
            value = state.form.name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.routing_custom_name_hint),
            imeAction = ImeAction.Next,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        SlteInput(
            value = state.form.url,
            onValueChange = onUrlChange,
            placeholder = stringResource(R.string.routing_custom_url_hint),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        BehaviorRow(
            label = stringResource(R.string.routing_custom_behavior_classical),
            selected = state.form.behavior == "classical",
            enabled = !state.submitting,
            onClick = { onBehaviorChange("classical") },
        )
        BehaviorRow(
            label = stringResource(R.string.routing_custom_behavior_domain),
            selected = state.form.behavior == "domain",
            enabled = !state.submitting,
            onClick = { onBehaviorChange("domain") },
        )
        BehaviorRow(
            label = stringResource(R.string.routing_custom_behavior_ipcidr),
            selected = state.form.behavior == "ipcidr",
            enabled = !state.submitting,
            onClick = { onBehaviorChange("ipcidr") },
        )

        state.errorMessageRes?.let { res ->
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text = stringResource(res),
                style = SlteType.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Dimens.gap.lg),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gap.lg))

        SlteButton(
            text = stringResource(R.string.routing_custom_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Primary,
            loading = state.submitting,
        )
    }
}

@Composable
private fun BehaviorRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Dimens.gap.sm, horizontal = Dimens.gap.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SlteType.body,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = SlteIcons.Check,
                contentDescription = null,
            )
        }
    }
}
