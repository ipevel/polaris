package com.slte.app.ui.screen.plans

import com.slte.app.data.repository.OrderRepository
import com.slte.app.utils.AppLog
import com.slte.app.utils.ErrorMessages
import javax.inject.Inject

sealed interface CouponCheck {
    data class Applied(val discount: Int) : CouponCheck

    data class Rejected(val messageRes: Int) : CouponCheck
}

class CouponChecker
@Inject
constructor(
    private val orderRepository: OrderRepository,
) {
    suspend fun check(
        code: String,
        planId: Int,
        priceCents: Int,
    ): CouponCheck = orderRepository
        .checkCoupon(
            code = code.trim(),
            planId = planId,
        ).fold(
            onSuccess = { result ->
                val discount = computeCouponDiscount(result.type, result.value, priceCents)
                AppLog.d(TAG, "checkCoupon: type=${result.type} value=${result.value} price=$priceCents discount=$discount")
                CouponCheck.Applied(discount)
            },
            onFailure = { e -> CouponCheck.Rejected(ErrorMessages.forOrder(e)) },
        )

    private companion object {
        const val TAG = "SLTE-Purchase"
    }
}
