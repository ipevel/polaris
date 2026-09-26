// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.forgot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.screen.login.AuthBrandHeader
import com.slte.app.ui.screen.login.AuthField
import com.slte.app.ui.screen.login.AuthFieldBox
import com.slte.app.ui.screen.login.AuthFieldError
import com.slte.app.ui.screen.login.AuthFieldLabel
import com.slte.app.ui.screen.login.AuthSendCodeButton
import com.slte.app.ui.screen.login.V5TextAction
import com.slte.app.ui.screen.login.authFieldErrorRes
import com.slte.app.ui.screen.login.rememberCompactAuthLayout
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5PasswordInput
import com.slte.app.ui.v5.v5Aurora
import com.slte.app.utils.Dimens

/**
 * 忘记密码页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteCard` + `SlteInput` + `SlteButton` 版本；入口与行为逐项对齐
 * （见交付报告的「忘记密码页入口对账清单」）：账号、验证码（含倒计时/重发）、新密码、
 * 重置密码、返回登录、字段内联校验、重置成功回调、全屏 Loading 遮罩、轻提示。
 */
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    onResetSuccess: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val form =
        when (val s = state) {
            is ForgotPasswordUiState.Form -> s
            is ForgotPasswordUiState.SendingCode -> s.form
            is ForgotPasswordUiState.Countdown -> s.form
            is ForgotPasswordUiState.Resetting -> s.form
            is ForgotPasswordUiState.ResetSuccess -> s.form
            is ForgotPasswordUiState.Error -> s.form
        }

    val isSendingCode = state is ForgotPasswordUiState.SendingCode
    val isResetting = state is ForgotPasswordUiState.Resetting
    val isCountingDown = state is ForgotPasswordUiState.Countdown
    val countdownSeconds = (state as? ForgotPasswordUiState.Countdown)?.seconds ?: 0
    val errorMessageRes = (state as? ForgotPasswordUiState.Error)?.messageRes
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
            ?.takeIf { it == R.string.error_new_password_required }
            ?.let { stringResource(it) }

    LaunchedEffect(state is ForgotPasswordUiState.ResetSuccess) {
        if (state is ForgotPasswordUiState.ResetSuccess) {
            onResetSuccess()
        }
    }

    val compact = rememberCompactAuthLayout()
    val c = V5ThemeColors.current

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .v5Aurora(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = if (compact) 8.dp else 22.dp,
                )
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(if (compact) 4.dp else 14.dp))

            AuthBrandHeader()

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 22.dp))

            V5Card(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = Dimens.maxContentWidth),
                contentPadding = PaddingValues(if (compact) 14.dp else 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.forgot_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )

                Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

                AuthField(
                    label = stringResource(R.string.login_account_hint),
                    error = emailError,
                ) {
                    V5Input(
                        value = form.email,
                        onValueChange = {
                            if (fieldErrorRes == R.string.error_email_required) fieldErrorRes = null
                            viewModel.onEmailChange(it)
                        },
                        placeholder = "",
                        icon = SlteIcons.Account,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        enabled = !isResetting,
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                // 验证码：错误文案整行放在输入框下方，按钮才能与输入框底对齐
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AuthFieldLabel(text = stringResource(R.string.error_code_required))
                        Spacer(modifier = Modifier.height(6.dp))
                        AuthFieldBox(hasError = codeError != null) {
                            V5Input(
                                value = form.verificationCode,
                                onValueChange = {
                                    if (fieldErrorRes == R.string.error_code_required) fieldErrorRes = null
                                    viewModel.onCodeChange(it)
                                },
                                placeholder = "",
                                icon = SlteIcons.VerificationCode,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                                enabled = !isResetting,
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

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                AuthField(
                    label = stringResource(R.string.login_password_hint),
                    error = passwordError,
                ) {
                    V5PasswordInput(
                        value = form.newPassword,
                        onValueChange = {
                            if (fieldErrorRes == R.string.error_new_password_required) fieldErrorRes = null
                            viewModel.onNewPasswordChange(it)
                        },
                        placeholder = "",
                        icon = SlteIcons.Password,
                        imeAction = ImeAction.Done,
                        enabled = !isResetting,
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 14.dp else 20.dp))

                V5Button(
                    text = stringResource(R.string.forgot_reset_button),
                    onClick = viewModel::resetPassword,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.PRIMARY,
                    onClickEnabled = !isResetting,
                    loading = isResetting,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    V5TextAction(text = stringResource(R.string.register_back_to_login)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBackToLogin()
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 22.dp))
        }
    }

    LoadingOverlay(visible = isResetting, onDismiss = viewModel::cancelLoading)

    ToastTip(
        message = errorMessageRes?.let { stringResource(it) },
        onDismiss = viewModel::dismissError,
    )
}
