package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.slte.app.R
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInputSize
import com.slte.app.ui.component.SltePasswordInput
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.utils.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordSheet(
    state: ChangePasswordState.Editing,
    onOldPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    SlteSheet(
        title = stringResource(R.string.settings_change_password),
        onDismiss = { if (!state.submitting) onDismiss() },
    ) {
        SltePasswordInput(
            value = state.form.oldPassword,
            onValueChange = onOldPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_old_hint),
            icon = SlteIcons.Password,
            imeAction = ImeAction.Next,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        SltePasswordInput(
            value = state.form.newPassword,
            onValueChange = onNewPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_new_hint),
            icon = SlteIcons.Password,
            imeAction = ImeAction.Next,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        SltePasswordInput(
            value = state.form.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_confirm_hint),
            icon = SlteIcons.Password,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.lg))

        SlteButton(
            text = stringResource(R.string.settings_change_pwd_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Primary,
            loading = state.submitting,
        )
    }
}
