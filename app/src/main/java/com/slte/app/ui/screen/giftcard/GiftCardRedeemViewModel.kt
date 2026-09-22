// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.giftcard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.remote.ApiException
import com.slte.app.data.repository.GiftCardRepository
import com.slte.app.utils.ApiErrors
import com.slte.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 兑换结果提示：本地化资源或后端原文二选一。 */
data class GiftCardTip(
    @StringRes val messageRes: Int? = null,
    val message: String? = null,
)

data class GiftCardRedeemState(
    val visible: Boolean = false,
    val code: String = "",
    val submitting: Boolean = false,
)

@HiltViewModel
class GiftCardRedeemViewModel
@Inject
constructor(
    private val giftCardRepository: GiftCardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GiftCardRedeemState())
    val state: StateFlow<GiftCardRedeemState> = _state.asStateFlow()

    private val _tip = MutableStateFlow<GiftCardTip?>(null)
    val tip: StateFlow<GiftCardTip?> = _tip.asStateFlow()

    /** 兑换成功信号：个人中心收到后刷新余额/流量/到期。 */
    private val _redeemed = MutableStateFlow(0)
    val redeemed: StateFlow<Int> = _redeemed.asStateFlow()

    private var requestId = 0

    fun open() {
        requestId++
        _state.value = GiftCardRedeemState(visible = true)
    }

    fun dismiss() {
        _state.value = GiftCardRedeemState()
    }

    fun updateCode(code: String) {
        val current = _state.value
        if (current.submitting) return
        _state.value = current.copy(code = code.take(CODE_MAX_LENGTH))
    }

    fun submit() {
        val current = _state.value
        val code = current.code.trim()
        if (current.submitting || code.isEmpty()) return

        _state.value = current.copy(submitting = true)
        val id = ++requestId
        viewModelScope.launch {
            giftCardRepository.redeem(code).fold(
                onSuccess = {
                    _tip.value = GiftCardTip(messageRes = R.string.gift_card_success_toast)
                    if (id == requestId) _state.value = GiftCardRedeemState()
                    _redeemed.value += 1
                },
                onFailure = { e ->
                    _tip.value = e.toTip()
                    // 失败保留弹窗，用户可改码重试
                    if (id == requestId) _state.value = _state.value.copy(submitting = false)
                },
            )
        }
    }

    fun clearTip() {
        _tip.value = null
    }

    private fun Throwable.toTip(): GiftCardTip {
        val api = this as? ApiException
        if (api?.stringResId != null) return GiftCardTip(messageRes = api.stringResId)
        val mapped = ErrorMessages.giftCardMessageRes(message)
        return when {
            mapped != null -> GiftCardTip(messageRes = mapped)
            !message.isNullOrBlank() -> GiftCardTip(message = message)
            else -> GiftCardTip(messageRes = ApiErrors.GIFT_CARD)
        }
    }

    private companion object {
        const val CODE_MAX_LENGTH = 32
    }
}
