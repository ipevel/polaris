// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.ToastTip
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
import com.slte.app.ui.v5.v5Clickable
import com.slte.app.utils.Dimens

/**
 * 注册页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteCard` + `SlteInput` + `SlteButton` 版本；入口与行为逐项对齐
 * （见交付报告的「注册页入口对账清单」）：账号、验证码（可开关）、密码、邀请码（必填/选填两态）、
 * 注册、返回登录、倒计时、字段内联校验、全屏 Loading 遮罩、轻提示。
 */
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
            ?.takeIf {
                it == R.string.error_email_required ||
                    it == R.string.error_email_suffix_not_allowed
            }
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
                .statusBarsPadding()
                .padding(
                    horizontal = 18.dp,
                    vertical = if (compact) 8.dp else 22.dp,
                )
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            V5Card(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = Dimens.maxContentWidth),
                contentPadding = PaddingValues(if (compact) 14.dp else 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.register_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )

                Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

                AuthField(
                    label = stringResource(R.string.login_account_hint),
                    error = emailError,
                ) {
                    // 白名单启用时后缀只能选不能填：左边只收 `@` 前部分，右边从下拉里挑后缀
                    if (form.emailWhitelist.isEnabled) {
                        EmailSuffixInput(
                            localPart = form.email.substringBefore('@'),
                            suffixes = form.emailWhitelist.suffixes,
                            selectedSuffix = form.emailSuffix,
                            onLocalPartChange = {
                                if (fieldErrorRes == R.string.error_email_required ||
                                    fieldErrorRes == R.string.error_email_suffix_not_allowed
                                ) {
                                    fieldErrorRes = null
                                }
                                viewModel.onEmailChange(it)
                            },
                            onSelectSuffix = viewModel::onEmailSuffixChange,
                            enabled = !isRegistering,
                        )
                    } else {
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
                            enabled = !isRegistering,
                        )
                    }
                }

                if (emailVerifyEnabled) {
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

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                AuthField(
                    label = stringResource(R.string.login_password_hint),
                    error = passwordError,
                ) {
                    V5PasswordInput(
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

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                AuthField(
                    label =
                    stringResource(
                        if (inviteForceEnabled) R.string.register_invite_hint else R.string.register_invite_optional,
                    ),
                    error = inviteError,
                ) {
                    V5Input(
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

                Spacer(modifier = Modifier.height(if (compact) 14.dp else 20.dp))

                V5Button(
                    text = stringResource(R.string.register_button),
                    onClick = viewModel::register,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.PRIMARY,
                    onClickEnabled = !isRegistering,
                    loading = isRegistering,
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

    LoadingOverlay(visible = isRegistering, onDismiss = viewModel::cancelLoading)

    ToastTip(
        message = errorMessageRes?.let { stringResource(it) },
        onDismiss = viewModel::dismissError,
    )
}

/**
 * 邮箱输入（白名单启用时）：左边只填 `@` 前面的部分，右边从下拉里选后缀。
 *
 * 后缀只能选不能填，避免"输入完才被后端拒绝"；下拉沿用 v5 既有下拉样式
 * （同 `WithdrawMethodField`），触发条沿用输入框尾部槽位的写法（同密码显隐按钮）。
 */
@Composable
private fun EmailSuffixInput(
    localPart: String,
    suffixes: List<String>,
    selectedSuffix: String?,
    onLocalPartChange: (String) -> Unit,
    onSelectSuffix: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth

        V5Input(
            value = localPart,
            onValueChange = onLocalPartChange,
            placeholder = "",
            icon = SlteIcons.Account,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = enabled,
            trailing = {
                val onOpen: (() -> Unit)? =
                    if (enabled) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            expanded = true
                        }
                    } else {
                        null
                    }
                Row(
                    modifier = Modifier
                        .then(v5Clickable(onClick = onOpen))
                        .padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "@${selectedSuffix.orEmpty()}",
                        fontSize = 14.sp,
                        color = if (enabled) c.accent else c.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Icon(
                        imageVector = if (expanded) SlteIcons.ExpandLess else SlteIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 2.dp),
                        tint = c.text3,
                    )
                }
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth).heightIn(max = 280.dp),
            shape = shape,
            containerColor = c.surface,
            border = BorderStroke(1.dp, c.hairline),
            shadowElevation = 6.dp,
        ) {
            suffixes.forEach { suffix ->
                val isSelected = suffix == selectedSuffix
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            v5Clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectSuffix(suffix)
                                    expanded = false
                                },
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "@$suffix",
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = SlteIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = c.accent,
                        )
                    }
                }
            }
        }
    }
}
