// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5PasswordInput
import com.slte.app.ui.v5.V5Sheet
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
    // 弹层在 `SettingsPageContent` 里挂载，和 `hiltViewModel()` 取到的是同一个 SettingsViewModel
    // （同一个 ViewModelStoreOwner + 同一个 key），所以这里能直接消费一次性错误信号。
    val viewModel: SettingsViewModel = hiltViewModel()

    // `errorMessageRes` 是一次性信号：`consumeChangePasswordError()` 之后立刻置空。
    // 先锁存到本地 state 再消费，否则消费引发的重组会把刚渲染出来的错误文案一起抹掉；
    // 锁存用的是 `remember` 而非 `rememberSaveable`，旋转屏幕重建组合后错误不会二次弹出。
    var shownError by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.errorMessageRes) {
        val res = state.errorMessageRes
        if (res != null) {
            shownError = res
            viewModel.consumeChangePasswordError()
        }
    }
    // 用户开始改输入即视为已读，撤掉提示（ViewModel 侧 `updateEditing` 同样会置空 errorMessageRes）。
    LaunchedEffect(state.form) { shownError = null }

    V5Sheet(
        title = stringResource(R.string.settings_change_password),
        onDismiss = { if (!state.submitting) onDismiss() },
    ) {
        V5PasswordInput(
            value = state.form.oldPassword,
            onValueChange = onOldPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_old_hint),
            icon = SlteIcons.Password,
            imeAction = ImeAction.Next,
            enabled = !state.submitting,
            small = true,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        V5PasswordInput(
            value = state.form.newPassword,
            onValueChange = onNewPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_new_hint),
            icon = SlteIcons.Password,
            imeAction = ImeAction.Next,
            enabled = !state.submitting,
            small = true,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        V5PasswordInput(
            value = state.form.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = stringResource(R.string.settings_change_pwd_confirm_hint),
            icon = SlteIcons.Password,
            enabled = !state.submitting,
            small = true,
        )

        shownError?.let { res ->
            Spacer(modifier = Modifier.height(Dimens.gap.md))
            Text(
                text = stringResource(res),
                style = SlteType.bodySmall,
                color = V5ThemeColors.current.danger,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gap.lg))

        V5Button(
            text = stringResource(R.string.settings_change_pwd_submit),
            style = ButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            loading = state.submitting,
            onClick = onSubmit,
        )
    }
}
