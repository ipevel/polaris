package com.slte.app.ui.screen.plans

import com.slte.app.R
import com.slte.app.data.remote.ApiException
import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.CheckoutResult
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject

sealed interface CheckoutOutcome {

    data class Completed(val tradeNo: String) : CheckoutOutcome

    data class Redirect(
        val tradeNo: String,
        val redirectUrl: String,
    ) : CheckoutOutcome

    data class Retry(
        val tradeNo: String,
        val messageRes: Int,
    ) : CheckoutOutcome
}

class PaymentCheckout
@Inject
constructor(
    private val orderRepository: OrderRepository,
) {
    suspend fun checkout(
        tradeNo: String,
        methodId: Int,
    ): CheckoutOutcome {
        AppLog.d(TAG, "confirmPayment: tradeNo=$tradeNo method=$methodId")
        return orderRepository
            .checkoutOrder(
                tradeNo = tradeNo,
                paymentMethod = methodId,
            ).fold(
                onSuccess = { decide(tradeNo, it) },
                onFailure = { e ->
                    AppLog.w(TAG, "checkoutOrder failed: ${sanitizeLog(e.message ?: "Unknown")}")
                    CheckoutOutcome.Retry(
                        tradeNo = tradeNo,
                        messageRes = (e as? ApiException)?.stringResId ?: R.string.order_pay_failed,
                    )
                },
            )
    }

    private fun decide(
        tradeNo: String,
        result: CheckoutResult,
    ): CheckoutOutcome {
        AppLog.d(TAG, "checkout result: tradeNo=$tradeNo type=${result.type} hasRedirect=${result.redirectUrl != null}")
        val redirectUrl = result.redirectUrl
        return when (decideCheckoutStep(result)) {
            CheckoutDecision.SUCCESS -> {
                AppLog.i(TAG, "余额支付成功: tradeNo=$tradeNo")
                CheckoutOutcome.Completed(tradeNo)
            }

            CheckoutDecision.REDIRECT ->
                if (redirectUrl != null) {
                    CheckoutOutcome.Redirect(tradeNo, redirectUrl)
                } else {
                    CheckoutOutcome.Retry(tradeNo, R.string.order_pay_failed)
                }

            CheckoutDecision.RETRY -> CheckoutOutcome.Retry(tradeNo, R.string.order_pay_failed)
        }
    }

    private companion object {
        const val TAG = "SLTE-Purchase"
    }
}
