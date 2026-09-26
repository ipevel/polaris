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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5FieldHint
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5PasswordInput
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.ui.v5.v5Aurora
import com.slte.app.utils.Dimens
import kotlin.math.cos
import kotlin.math.sin

/**
 * 登录页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteCard` + `SlteInput` + `SlteButton` 版本；入口与行为逐项对齐
 * （详见交付报告的「登录页入口对账清单」）：账号/密码/面板地址三个字段、登录、记住密码、
 * 忘记密码、注册入口、面板地址确认弹窗（含私网地址警告）、校验错误内联到字段、
 * 成功/注册配置两个 `LaunchedEffect` 收尾、全屏 Loading 遮罩、轻提示。
 *
 * 保留的一处 v4 行为（有意）：错误文案先由 `ToastTip` 弹出，同时在对应输入框内联展示；
 * 用户开始修改该字段时才清除内联提示。
 */
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

    // 小屏 / 大字体（fontScale 1.5）下压缩竖向留白，保证主按钮与「记住密码/忘记密码？」留在首屏
    // 而不是只剩 4px 高、或被挤出屏幕（第 3 轮 N4/N5）。判据见 needsCompactAuthLayout。
    val compact = rememberCompactAuthLayout()

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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    if (compact) 14.dp else 18.dp,
                ),
            ) {
                Text(
                    text = stringResource(R.string.login_welcome),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = V5ThemeColors.current.text,
                )

                Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

                AuthField(
                    label = stringResource(R.string.login_account_hint),
                    error = accountError,
                ) {
                    V5Input(
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
                        imeAction = ImeAction.Next,
                        enabled = !isLoading,
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                AuthField(
                    label = stringResource(R.string.login_url_hint),
                    error = panelUrlError,
                ) {
                    V5Input(
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

                Spacer(modifier = Modifier.height(if (compact) 14.dp else 20.dp))
                V5Button(
                    text = stringResource(R.string.login_button),
                    onClick = viewModel::login,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.PRIMARY,
                    onClickEnabled = !isLoading,
                    loading = isLoading,
                )

                Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

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
                    V5TextAction(
                        text = stringResource(R.string.login_forgot_password),
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onForgotPassword()
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

                V5Button(
                    text = stringResource(R.string.register_title),
                    onClick = viewModel::checkRegisterConfig,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.NEUTRAL,
                    onClickEnabled = !isCheckingRegisterConfig,
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 22.dp))
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

/**
 * 面板地址确认弹窗（v5）。
 *
 * 保留 Material3 `AlertDialog`（返回键关闭、点击外部关闭、无障碍焦点陷阱），只把取色与按钮换成 v5。
 */
@Composable
private fun PanelUrlConfirmDialog(
    url: String,
    isPrivateHost: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = V5ThemeColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text2,
        title = {
            Text(
                text =
                if (isPrivateHost) {
                    stringResource(R.string.panel_url_confirm_private_title)
                } else {
                    stringResource(R.string.panel_url_confirm_title)
                },
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.panel_url_confirm_message), fontSize = 13.5.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(url, fontSize = 12.5.sp, color = c.accent)
                if (isPrivateHost) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.panel_url_confirm_private_warning),
                        fontSize = 12.5.sp,
                        color = c.danger,
                    )
                }
            }
        },
        confirmButton = {
            V5DialogAction(stringResource(R.string.panel_url_confirm_connect), primary = true, onClick = onConfirm)
        },
        dismissButton = {
            V5DialogAction(stringResource(R.string.panel_url_confirm_cancel), primary = false, onClick = onDismiss)
        },
    )
}

/** v5 弹窗动作位（文字按钮，强调色）。 */
@Composable
private fun V5DialogAction(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    TextButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) c.accent else c.text2,
        )
    }
}

/** v5 文字动作（如「忘记密码？」）：无涟漪 + 强调色小字。 */
@Composable
internal fun V5TextAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .then(noRippleClickable(onClick))
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.accent)
    }
}

/**
 * 「记住密码」开关行（v5 开关）。
 *
 * 语义与 v4 一致：整行可点、`Role.Checkbox` 无障碍角色、带触感反馈；只把 Material `Checkbox`
 * 换成 v5 的 [V5Switch]——v5 语言里没有方形复选框，开关是唯一的二态控件。
 */
@Composable
internal fun RememberMeRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val c = V5ThemeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        V5Switch(checked = checked)
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = c.text2,
        )
    }
}

/**
 * 认证页（登录/注册/忘记密码）在小屏或大字体下是否需要压缩竖向留白。
 *
 * 判据：把可用高度按 fontScale 归一化成"标准字体的等效高度"，低于
 * [AUTH_COMFORTABLE_HEIGHT_DP] 即认为首屏装不下"品牌区 + 表单主操作"。
 * 字体放大对竖向占用的影响近似线性（字号↑、行高与控件最小高度同比↑），所以直接除以 fontScale。
 *
 * 背景（第 3 轮 N4/N5，雷电模拟器真机实测）：
 * - 360×640dp 下「记住密码 / 忘记密码？」的 bounds 高度只剩 **4px**，贴在屏幕下沿；
 * - `fontScale = 1.5` 时「登录」按钮只剩顶部一条边在屏内。
 * 两者都能滚动触及，但**首屏无法直接完成登录动作**——这个函数就是用来判断要不要收窄留白的。
 *
 * 纯函数，便于单测固定判据（见 LoginScreenLayoutTest）。
 */
internal fun needsCompactAuthLayout(
    availableHeightDp: Int,
    fontScale: Float,
): Boolean {
    if (availableHeightDp <= 0) return false
    return availableHeightDp / fontScale.coerceAtLeast(1f) < AUTH_COMFORTABLE_HEIGHT_DP
}

/** 首屏能从容放下品牌区 + 登录表单的等效高度阈值（标准字体下）。 */
internal const val AUTH_COMFORTABLE_HEIGHT_DP = 700

/** 读取当前是否需要压缩认证页留白（三个认证页共用）。 */
@Composable
internal fun rememberCompactAuthLayout(): Boolean {
    val config = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    return needsCompactAuthLayout(config.screenHeightDp, fontScale)
}

/**
 * 认证三页共用的品牌区（标识 + 应用名 + 一句话说明）。
 * 放在本文件是因为文件集只覆盖这三个 Screen，Register/Forgot 以同模块 internal 复用，避免三份重复绘制代码。
 *
 * **标识与应用名在任何情况下都保留**（用户明确要求这张品牌图不能被取消）：小屏/大字体下
 * 只把标识收小、说明文字限一行，而不是隐藏（第 3 轮 N4/N5）。
 */
@Composable
internal fun AuthBrandHeader(modifier: Modifier = Modifier) {
    val compact = rememberCompactAuthLayout()
    val c = V5ThemeColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(markSize = if (compact) 48.dp else 64.dp)
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_app_desc),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = c.text3,
            textAlign = TextAlign.Center,
            // 压缩态限一行（文案本身仍在，不删），省下的高度留给表单主操作
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 极星标识：轨道环 + 四角星，纯 Compose 绘制，取色走 v5 令牌。 */
@Composable
private fun BrandMark(
    markSize: Dp,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    Canvas(modifier = modifier.size(markSize)) {
        drawPolarisMark(accent = c.accent, ring = c.text3)
    }
}

/** 极星标识绘制（与关于页共用同一份路径逻辑，取色由调用方决定）。 */
internal fun DrawScope.drawPolarisMark(
    accent: Color,
    ring: Color,
) {
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
    drawPath(path = authPolarisStarPath(center = center, radius = radius * 0.44f), color = accent)
    drawPath(
        path =
        authPolarisStarPath(
            center = Offset(center.x + radius * 0.6f, center.y - radius * 0.56f),
            radius = radius * 0.14f,
        ),
        color = accent.copy(alpha = 0.7f),
    )
}

internal fun authPolarisStarPath(
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

/** 字段标签：v5 小字灰。 */
@Composable
internal fun AuthFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    V5FieldHint(text = text, modifier = modifier)
}

/**
 * 字段容器（v5）：错误态时在输入框之外再描一圈危险色。
 *
 * 与 v4 的实现同构（`drawWithContent` 在子内容之后补画描边，保证描边压在输入框之上可见），
 * 只把取色换成 v5 `danger`、圆角对齐 `V5Input` 的 16dp。
 */
@Composable
internal fun AuthFieldBox(
    hasError: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val errorColor = V5ThemeColors.current.danger
    val ringModifier =
        if (hasError) {
            Modifier.drawWithContent {
                drawContent()
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = errorColor,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
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

/** 字段级错误文案（v5 危险色小字）。 */
@Composable
internal fun AuthFieldError(
    text: String?,
    modifier: Modifier = Modifier,
) {
    if (text != null) {
        Spacer(modifier = Modifier.height(5.dp))
        V5FieldHint(text = text, modifier = modifier.padding(start = 4.dp), danger = true)
    }
}

/** 字段组合：标签 + 输入框（可带错误描边）+ 错误文案。 */
@Composable
internal fun AuthField(
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AuthFieldLabel(text = label)
        Spacer(modifier = Modifier.height(6.dp))
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
 * 「发送验证码」槽位（v5）：倒计时阶段换成同尺寸的等宽数字条（秒数是数值，走 mono），
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
    val c = V5ThemeColors.current
    if (isCountingDown) {
        Box(
            modifier =
            modifier
                .widthIn(min = 108.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accentBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = countdownLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.accent,
            )
        }
    } else {
        V5Button(
            text = stringResource(R.string.register_send_code),
            onClick = onClick,
            modifier = modifier,
            style = ButtonStyle.TONAL,
            small = true,
            loading = loading,
        )
    }
}
