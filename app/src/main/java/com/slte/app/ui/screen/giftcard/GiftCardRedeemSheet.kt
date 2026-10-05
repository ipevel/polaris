// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.giftcard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.slte.app.R
import com.slte.app.ui.component.AnimatedSticker
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5Sheet
import com.slte.app.utils.Dimens
import com.slte.app.utils.Stickers

@Composable
fun GiftCardRedeemSheet(
    state: GiftCardRedeemState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    V5Sheet(
        title = stringResource(R.string.gift_card_title),
        subtitle = stringResource(R.string.gift_card_subtitle),
        onDismiss = onDismiss,
        dismissible = !state.submitting,
        header = {
            AnimatedSticker(
                assetPath = Stickers.GIFT_CARD,
                modifier = Modifier.size(Dimens.stateStickerSize),
            )
        },
    ) {
        V5Input(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.gift_card_code_hint),
            icon = SlteIcons.InviteCode,
            iconDesc = stringResource(R.string.gift_card_title),
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            enabled = !state.submitting,
            small = true,
        )

        V5Button(
            text = stringResource(R.string.gift_card_redeem),
            style = ButtonStyle.PRIMARY,
            modifier = Modifier
                .padding(top = Dimens.gap.md)
                .fillMaxWidth(),
            loading = state.submitting,
            onClickEnabled = state.code.isNotBlank(),
            onClick = onSubmit,
        )
    }
}
