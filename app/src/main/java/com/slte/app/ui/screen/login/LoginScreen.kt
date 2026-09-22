// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.AnimatedSticker
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SltePasswordInput
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.Stickers

@Composable
fun LoginScreen(
    onForgotPassword: () -> Unit = {},
    onCreateAccount: (emailVerify: Boolean, inviteForce: Boolean) -> Unit = { _, _ -> },
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val form =
        when (val s = state) {
            is LoginUiState.Form -> s
            is LoginUiState.LoggingIn -> s.form
            is LoginUiState.CheckingRegisterConfig -> s.form
            is LoginUiState.LoginSuccess -> s.form
            is LoginUiState.RegisterConfigReady -> s.form
            is LoginUiState.ConfirmPanelUrl -> s.form
            is LoginUiState.Error -> s.form
        }

    val isLoading = state is LoginUiState.LoggingIn
    val isCheckingRegisterConfig = state is LoginUiState.CheckingRegisterConfig
    val errorMessageRes = (state as? LoginUiState.Error)?.messageRes

    LaunchedEffect(state is LoginUiState.LoginSuccess) {
        if (state is LoginUiState.LoginSuccess) {
            viewModel.onNavigatedToLoginSuccess()
        }
    }

    LaunchedEffect(state is LoginUiState.RegisterConfigReady) {
        val s = state
        if (s is LoginUiState.RegisterConfigReady) {
            viewModel.onNavigatedToRegister()
            onCreateAccount(s.config.emailVerifyEnabled, s.config.inviteForceEnabled)
        }
    }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = Dimens.gap.xxl,
                vertical = Dimens.gap.xxl,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimens.gap.xxl))

            AnimatedSticker(
                assetPath = Stickers.LOGIN,
                modifier = Modifier.size(Dimens.logoSize),
            )

            Spacer(modifier = Modifier.height(Dimens.gap.xl))

            Text(
                text = stringResource(R.string.login_welcome),
                style = SlteType.pageTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.xxl))

            SlteInput(
                value = form.account,
                onValueChange = viewModel::onAccountChange,
                placeholder = stringResource(R.string.login_account_hint),
                icon = SlteIcons.Account,
                keyboardType = KeyboardType.Email,
                enabled = !isLoading,
                bordered = false,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            SltePasswordInput(
                value = form.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = stringResource(R.string.login_password_hint),
                icon = SlteIcons.Password,
                imeAction = ImeAction.Next,
                enabled = !isLoading,
                bordered = false,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            SlteInput(
                value = form.panelUrl,
                onValueChange = viewModel::onPanelUrlChange,
                placeholder = stringResource(R.string.login_url_hint),
                icon = SlteIcons.Language,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                enabled = !isLoading,
                bordered = false,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.lg))
            SlteButton(
                text = stringResource(R.string.login_button),
                onClick = viewModel::login,
                modifier = Modifier.fillMaxWidth(),
                style = SlteButtonStyle.Primary,
                height = Dimens.size.row,
                loading = isLoading,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RememberMeRow(
                    checked = form.rememberMe,
                    enabled = !isLoading,
                    label = stringResource(R.string.login_remember_me),
                    onToggle = viewModel::toggleRememberMe,
                )
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onForgotPassword()
                }) {
                    Text(stringResource(R.string.login_forgot_password))
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap.md))

            SlteButton(
                text = stringResource(R.string.register_title),
                onClick = viewModel::checkRegisterConfig,
                modifier = Modifier.fillMaxWidth(),
                style = SlteButtonStyle.Secondary,
                height = Dimens.size.row,
                enabled = !isCheckingRegisterConfig,
            )
        }
    }

    LoadingOverlay(
        visible = isLoading || isCheckingRegisterConfig,
        onDismiss = viewModel::cancelLoading,
    )

    ToastTip(
        message = errorMessageRes?.let { stringResource(it) },
        onDismiss = viewModel::dismissError,
    )

    // 面板地址确认对话框
    val confirmState = state as? LoginUiState.ConfirmPanelUrl
    if (confirmState != null) {
        PanelUrlConfirmDialog(
            url = confirmState.normalizedUrl,
            isPrivateHost = confirmState.isPrivateHost,
            onConfirm = viewModel::confirmPanelUrl,
            onDismiss = viewModel::cancelConfirmPanelUrl,
        )
    }
}

@Composable
private fun PanelUrlConfirmDialog(
    url: String,
    isPrivateHost: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isPrivateHost) {
                    stringResource(R.string.panel_url_confirm_private_title)
                } else {
                    stringResource(R.string.panel_url_confirm_title)
                },
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.panel_url_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (isPrivateHost) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        stringResource(R.string.panel_url_confirm_private_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.panel_url_confirm_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.panel_url_confirm_cancel))
            }
        },
    )
}

@Composable
internal fun RememberMeRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        ),
    ) {
        Box(
            modifier = Modifier.size(Dimens.size.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
