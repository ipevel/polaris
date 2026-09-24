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
import com.slte.app.R
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SlteInputSize
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.utils.Dimens

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

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        SlteInput(
            value = account,
            onValueChange = { input ->
                account = input.filter { it.isLetterOrDigit() || it in "@.-_+" }.take(100)
            },
            placeholder = stringResource(R.string.invite_withdraw_account_hint),
            icon = SlteIcons.AtSign,
            iconDesc = stringResource(R.string.invite_withdraw_account_label),
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.xl))

        SlteButton(
            text = stringResource(R.string.invite_withdraw_confirm),
            onClick = { onConfirm(selectedMethod, account) },
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Primary,
            enabled = selectedMethod.isNotBlank() && account.isNotBlank(),
            loading = isSubmitting,
        )
    }
}
