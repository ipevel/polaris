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
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SlteInputSize
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.utils.Dimens
import com.slte.app.utils.Stickers

@Composable
fun GiftCardRedeemSheet(
    state: GiftCardRedeemState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    SlteSheet(
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
        SlteInput(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.gift_card_code_hint),
            icon = SlteIcons.InviteCode,
            iconDesc = stringResource(R.string.gift_card_title),
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            enabled = !state.submitting,
            size = SlteInputSize.Compact,
        )

        SlteButton(
            text = stringResource(R.string.gift_card_redeem),
            onClick = onSubmit,
            enabled = state.code.isNotBlank() && !state.submitting,
            loading = state.submitting,
            modifier = Modifier
                .padding(top = Dimens.gap.md)
                .fillMaxWidth(),
            style = SlteButtonStyle.Primary,
        )
    }
}
