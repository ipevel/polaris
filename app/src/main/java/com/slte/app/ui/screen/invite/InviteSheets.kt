// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5ReadOnlyField

/**
 * 转赠佣金面板（v5）。
 *
 * 面板外壳仍是 [SlteSheet]（Material3 `ModalBottomSheet`，要它的滚动 / IME 避让 / 无障碍语义），
 * 只把形状与标题字号显式传成 v5 取值；内容全部换成 v5 组件。
 */
@Composable
fun TransferSheet(
    availableBalance: Int,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }

    SlteSheet(
        title = stringResource(R.string.invite_transfer_title),
        subtitle = stringResource(R.string.invite_transfer_subtitle, stringResource(R.string.app_name)),
        onDismiss = onDismiss,
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        V5ReadOnlyField(
            value = stringResource(R.string.currency_symbol) + com.slte.app.utils.FormatUtils.balance(availableBalance),
            label = stringResource(R.string.invite_transfer_available),
            icon = SlteIcons.Balance,
            iconDesc = stringResource(R.string.invite_transfer_available),
        )

        Spacer(modifier = Modifier.height(12.dp))

        V5Input(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = stringResource(R.string.invite_transfer_amount_hint),
            icon = SlteIcons.Amount,
            keyboardType = KeyboardType.Decimal,
            small = true,
        )

        Spacer(modifier = Modifier.height(20.dp))

        V5Button(
            text = stringResource(R.string.invite_transfer_confirm),
            onClick = { amountText.toDoubleOrNull()?.let { onConfirm(it) } },
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.PRIMARY,
            onClickEnabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
            loading = isSubmitting,
        )
    }
}
