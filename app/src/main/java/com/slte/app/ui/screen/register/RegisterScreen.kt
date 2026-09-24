// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SltePasswordInput
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.screen.login.AuthBrandHeader
import com.slte.app.ui.screen.login.AuthField
import com.slte.app.ui.screen.login.AuthFieldBox
import com.slte.app.ui.screen.login.AuthFieldError
import com.slte.app.ui.screen.login.AuthFieldLabel
import com.slte.app.ui.screen.login.AuthSendCodeButton
import com.slte.app.ui.screen.login.authFieldErrorRes
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.slteAuroraBackground
import com.slte.app.utils.Dimens

@Composable
fun RegisterScreen(
    emailVerifyEnabled: Boolean,
    inviteForceEnabled: Boolean,
    onBackToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    LaunchedEffect(emailVerifyEnabled, inviteForceEnabled) {
        viewModel.initConfig(emailVerifyEnabled, inviteForceEnabled)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val form =
        when (val s = state) {
            is RegisterUiState.Form -> s
            is RegisterUiState.SendingCode -> s.form
            is RegisterUiState.Countdown -> s.form
            is RegisterUiState.Registering -> s.form
            is RegisterUiState.RegisterSuccess -> s.form
            is RegisterUiState.Error -> s.form
        }

    val isSendingCode = state is RegisterUiState.SendingCode
    val isRegistering = state is RegisterUiState.Registering
    val countdownSeconds = (state as? RegisterUiState.Countdown)?.seconds ?: 0
    val errorMessageRes = (state as? RegisterUiState.Error)?.messageRes
    val isCountingDown = state is RegisterUiState.Countdown
    val isLoading = isSendingCode

    // 见 LoginScreen：ToastTip 会立刻清掉 VM 错误态，故先留存一份用于字段内联提示
    var fieldErrorRes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(errorMessageRes) {
        errorMessageRes?.let { fieldErrorRes = authFieldErrorRes(it) }
    }
    val emailError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_email_required }
            ?.let { stringResource(it) }
    val codeError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_code_required }
            ?.let { stringResource(it) }
    val passwordError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_password_required }
            ?.let { stringResource(it) }
    val inviteError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_invite_required }
            ?.let { stringResource(it) }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .slteAuroraBackground(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.xl)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimens.gap.lg))

            AuthBrandHeader()

            Spacer(modifier = Modifier.height(Dimens.gap.xl))

            SlteCard(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = Dimens.maxContentWidth),
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Dimens.gap.lg),
                ) {
                    Text(
                        text = stringResource(R.string.register_title),
                        style = SlteType.heading,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(Dimens.gap.lg))

                    AuthField(
                        label = stringResource(R.string.login_account_hint),
                        error = emailError,
                    ) {
                        SlteInput(
                            value = form.email,
                            onValueChange = {
                                if (fieldErrorRes == R.string.error_email_required) fieldErrorRes = null
                                viewModel.onEmailChange(it)
                            },
                            placeholder = "",
                            icon = SlteIcons.Account,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            enabled = !isRegistering,
                        )
                    }

                    if (emailVerifyEnabled) {
                        Spacer(modifier = Modifier.height(Dimens.gap.md))

                        // 验证码：错误文案整行放在输入框下方，按钮才能与输入框底对齐
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gap.md),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AuthFieldLabel(text = stringResource(R.string.error_code_required))
                                Spacer(modifier = Modifier.height(Dimens.gap.xs))
                                AuthFieldBox(hasError = codeError != null) {
                                    SlteInput(
                                        value = form.verificationCode,
                                        onValueChange = {
                                            if (fieldErrorRes == R.string.error_code_required) fieldErrorRes = null
                                            viewModel.onCodeChange(it)
                                        },
                                        placeholder = "",
                                        icon = SlteIcons.VerificationCode,
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next,
                                        enabled = !isRegistering,
                                    )
                                }
                            }
                            AuthSendCodeButton(
                                isCountingDown = isCountingDown,
                                countdownLabel = stringResource(R.string.format_countdown_s, countdownSeconds),
                                loading = isLoading,
                                onClick = viewModel::sendVerificationCode,
                            )
                        }
                        AuthFieldError(text = codeError)
                    }

                    Spacer(modifier = Modifier.height(Dimens.gap.md))

                    AuthField(
                        label = stringResource(R.string.login_password_hint),
                        error = passwordError,
                    ) {
                        SltePasswordInput(
                            value = form.password,
                            onValueChange = {
                                if (fieldErrorRes == R.string.error_password_required) fieldErrorRes = null
                                viewModel.onPasswordChange(it)
                            },
                            placeholder = "",
                            icon = SlteIcons.Password,
                            imeAction = ImeAction.Done,
                            enabled = !isRegistering,
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.gap.md))

                    AuthField(
                        label =
                        stringResource(
                            if (inviteForceEnabled) R.string.register_invite_hint else R.string.register_invite_optional,
                        ),
                        error = inviteError,
                    ) {
                        SlteInput(
                            value = form.inviteCode,
                            onValueChange = {
                                if (fieldErrorRes == R.string.error_invite_required) fieldErrorRes = null
                                viewModel.onInviteCodeChange(it)
                            },
                            placeholder = "",
                            icon = SlteIcons.InviteCode,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                            enabled = !isRegistering,
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.gap.lg))

                    SlteButton(
                        text = stringResource(R.string.register_button),
                        onClick = viewModel::register,
                        modifier = Modifier.fillMaxWidth(),
                        style = SlteButtonStyle.Primary,
                        height = Dimens.size.row,
                        loading = isRegistering,
                    )

                    Spacer(modifier = Modifier.height(Dimens.gap.sm))

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBackToLogin()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.register_back_to_login))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.xl))
        }
    }

    LoadingOverlay(visible = isRegistering, onDismiss = viewModel::cancelLoading)

    ToastTip(
        message = errorMessageRes?.let { stringResource(it) },
        onDismiss = viewModel::dismissError,
    )
}
