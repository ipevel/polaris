// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.slteAuroraBackground
import com.slte.app.utils.Dimens
import kotlin.math.cos
import kotlin.math.sin

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

    // 字段级错误：ToastTip 会在提示后立刻清掉 VM 里的错误态，这里先留存一份供输入框内联展示，
    // 用户开始修改该字段时再清除（纯展示层状态，不介入 ViewModel）。
    var fieldErrorRes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(errorMessageRes) {
        errorMessageRes?.let { fieldErrorRes = authFieldErrorRes(it) }
    }
    val accountError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_email_required }
            ?.let { stringResource(it) }
    val passwordError =
        fieldErrorRes
            ?.takeIf { it == R.string.error_password_required }
            ?.let { stringResource(it) }
    val panelUrlError =
        fieldErrorRes
            ?.takeIf { it == R.string.login_url_required || it == R.string.login_url_invalid }
            ?.let { stringResource(it) }

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
                        text = stringResource(R.string.login_welcome),
                        style = SlteType.heading,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(Dimens.gap.lg))

                    AuthField(
                        label = stringResource(R.string.login_account_hint),
                        error = accountError,
                    ) {
                        SlteInput(
                            value = form.account,
                            onValueChange = {
                                if (fieldErrorRes == R.string.error_email_required) fieldErrorRes = null
                                viewModel.onAccountChange(it)
                            },
                            placeholder = "",
                            icon = SlteIcons.Account,
                            keyboardType = KeyboardType.Email,
                            enabled = !isLoading,
                        )
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
                            imeAction = ImeAction.Next,
                            enabled = !isLoading,
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.gap.md))

                    AuthField(
                        label = stringResource(R.string.login_url_hint),
                        error = panelUrlError,
                    ) {
                        SlteInput(
                            value = form.panelUrl,
                            onValueChange = {
                                if (
                                    fieldErrorRes == R.string.login_url_required ||
                                    fieldErrorRes == R.string.login_url_invalid
                                ) {
                                    fieldErrorRes = null
                                }
                                viewModel.onPanelUrlChange(it)
                            },
                            placeholder = "",
                            icon = SlteIcons.Language,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            enabled = !isLoading,
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.gap.lg))
                    SlteButton(
                        text = stringResource(R.string.login_button),
                        onClick = viewModel::login,
                        modifier = Modifier.fillMaxWidth(),
                        style = SlteButtonStyle.Primary,
                        height = Dimens.size.row,
                        loading = isLoading,
                    )

                    Spacer(modifier = Modifier.height(Dimens.gap.sm))

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

                    Spacer(modifier = Modifier.height(Dimens.gap.sm))

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

            Spacer(modifier = Modifier.height(Dimens.gap.xl))
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
                    style = SlteType.body,
                )
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                Text(
                    url,
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (isPrivateHost) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        stringResource(R.string.panel_url_confirm_private_warning),
                        style = SlteType.bodySmall,
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
            style = SlteType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 认证三页共用的品牌区（标识 + 应用名 + 一句话说明）。
 * 放在本文件是因为文件集只覆盖这三个 Screen，Register/Forgot 以同模块 internal 复用，避免三份重复绘制代码。
 */
@Composable
internal fun AuthBrandHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(markSize = 64.dp)
        Spacer(modifier = Modifier.height(Dimens.gap.md))
        Text(
            text = stringResource(R.string.app_name),
            style = SlteType.display,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = stringResource(R.string.about_app_desc),
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 极星标识：轨道环 + 四角星，纯 Compose 绘制。 */
@Composable
private fun BrandMark(
    markSize: Dp,
    modifier: Modifier = Modifier,
) {
    val accent = SlteColors.current.accentInteractive
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(markSize)) {
        val radius = size.minDimension / 2f
        val center = center
        drawCircle(
            color = ring.copy(alpha = 0.45f),
            radius = radius * 0.9f,
            center = center,
            style = Stroke(width = radius * 0.07f),
        )
        rotate(degrees = -30f, pivot = center) {
            drawOval(
                color = ring.copy(alpha = 0.3f),
                topLeft = Offset(center.x - radius, center.y - radius * 0.44f),
                size = Size(radius * 2f, radius * 0.88f),
                style = Stroke(width = radius * 0.06f),
            )
        }
        drawPath(path = polarisStarPath(center = center, radius = radius * 0.44f), color = accent)
        drawPath(
            path =
            polarisStarPath(
                center = Offset(center.x + radius * 0.6f, center.y - radius * 0.56f),
                radius = radius * 0.14f,
            ),
            color = accent.copy(alpha = 0.7f),
        )
    }
}

private fun polarisStarPath(
    center: Offset,
    radius: Float,
): Path {
    val path = Path()
    val inner = radius * 0.3f
    repeat(8) { index ->
        val r = if (index % 2 == 0) radius else inner
        val angle = Math.toRadians(index * 45.0 - 90.0)
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 字段标签：小字 + onSurfaceVariant，避免把校验文案当占位符用。 */
@Composable
internal fun AuthFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = SlteType.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * 字段容器：错误态时在输入框描边之上再画一圈 error 色描边。
 * SlteInput 不支持 error 参数（且不在本文件集），故用 drawWithContent 在子内容之后补画，保证描边可见。
 */
@Composable
internal fun AuthFieldBox(
    hasError: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val ringModifier =
        if (hasError) {
            Modifier.drawWithContent {
                drawContent()
                val stroke = Dimens.strokeMedium.toPx()
                drawRoundRect(
                    color = errorColor,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(SlteRadii.inner.toPx()),
                    style = Stroke(width = stroke),
                )
            }
        } else {
            Modifier
        }
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .then(ringModifier),
    ) {
        content()
    }
}

@Composable
internal fun AuthFieldError(
    text: String?,
    modifier: Modifier = Modifier,
) {
    if (text != null) {
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = text,
            style = SlteType.label,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(start = Dimens.gap.xs),
        )
    }
}

@Composable
internal fun AuthField(
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AuthFieldLabel(text = label)
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        AuthFieldBox(hasError = error != null) { content() }
        AuthFieldError(text = error)
    }
}

/** 校验类错误文案 → 可内联到具体输入框；其余（网络/业务错误）仍只走 Toast。 */
internal fun authFieldErrorRes(errorRes: Int?): Int? = when (errorRes) {
    R.string.error_email_required,
    R.string.error_password_required,
    R.string.error_new_password_required,
    R.string.error_code_required,
    R.string.error_invite_required,
    R.string.login_url_required,
    R.string.login_url_invalid,
    -> errorRes
    else -> null
}

/**
 * 「发送验证码」槽位：倒计时阶段换成同尺寸的等宽数字条（秒数是数值，走 mono），
 * 仍在倒计时中不可点，与原先 disabled 按钮的行为一致。Register/Forgot 共用。
 */
@Composable
internal fun AuthSendCodeButton(
    isCountingDown: Boolean,
    countdownLabel: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isCountingDown) {
        Box(
            modifier =
            modifier
                .width(Dimens.sendCodeButtonWidth)
                .height(Dimens.size.row)
                .clip(SlteShapes.medium)
                .background(SlteColors.current.accentInteractiveBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = countdownLabel,
                style = SlteType.value,
                color = SlteColors.current.accentInteractive,
            )
        }
    } else {
        SlteButton(
            text = stringResource(R.string.register_send_code),
            onClick = onClick,
            modifier = modifier.width(Dimens.sendCodeButtonWidth),
            style = SlteButtonStyle.Tonal,
            loading = loading,
        )
    }
}
