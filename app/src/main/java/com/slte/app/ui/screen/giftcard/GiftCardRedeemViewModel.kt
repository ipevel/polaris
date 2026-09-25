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

/**
 * 兑换结果提示：**只允许本地化资源**。
 *
 * 刻意不保留「后端原文」通道：面板/网关返回的 message 属于外部不可信文本，
 * 直接 toast 出来等于把远端内容回显给用户（也曾出现把码值/内部错误一起吐出的情况）。
 * 无法映射的失败一律落到 [ApiErrors.GIFT_CARD] 这个本地化兜底文案。
 */
data class GiftCardTip(
    @StringRes val messageRes: Int? = null,
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
        // 只按关键词映射到本地化文案；未命中就用兜底，绝不回显后端原文（含码值/内部错误）。
        val mapped = ErrorMessages.giftCardMessageRes(message)
        return GiftCardTip(messageRes = mapped ?: ApiErrors.GIFT_CARD)
    }

    private companion object {
        const val CODE_MAX_LENGTH = 32
    }
}
