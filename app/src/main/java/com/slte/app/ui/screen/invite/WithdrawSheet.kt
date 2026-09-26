// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input

/** 提现面板（v5）：提现方式下拉 + 收款账号输入 + 确认。 */
@Composable
fun WithdrawSheet(
    methodsState: WithdrawMethodsState,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onRetryMethods: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var selectedMethod by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val methods = (methodsState as? WithdrawMethodsState.Ready)?.methods.orEmpty()
    val isLoadingMethods = methodsState is WithdrawMethodsState.Loading
    val methodsFailed = methodsState is WithdrawMethodsState.Failed

    LaunchedEffect(methods) {
        if (selectedMethod !in methods) {
            selectedMethod = methods.firstOrNull().orEmpty()
        }
    }

    SlteSheet(
        title = stringResource(R.string.invite_withdraw_title),
        subtitle = stringResource(R.string.invite_withdraw_subtitle),
        onDismiss = onDismiss,
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        WithdrawMethodField(
            methods = methods,
            selected = selectedMethod,
            isLoading = isLoadingMethods,
            failed = methodsFailed,
            onRetry = onRetryMethods,
            onSelect = { method ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedMethod = method
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        V5Input(
            value = account,
            onValueChange = { input ->
                account = input.filter { it.isLetterOrDigit() || it in "@.-_+" }.take(100)
            },
            placeholder = stringResource(R.string.invite_withdraw_account_hint),
            icon = SlteIcons.AtSign,
            iconDesc = stringResource(R.string.invite_withdraw_account_label),
            small = true,
        )

        Spacer(modifier = Modifier.height(20.dp))

        V5Button(
            text = stringResource(R.string.invite_withdraw_confirm),
            onClick = { onConfirm(selectedMethod, account) },
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.PRIMARY,
            onClickEnabled = selectedMethod.isNotBlank() && account.isNotBlank(),
            loading = isSubmitting,
        )
    }
}
