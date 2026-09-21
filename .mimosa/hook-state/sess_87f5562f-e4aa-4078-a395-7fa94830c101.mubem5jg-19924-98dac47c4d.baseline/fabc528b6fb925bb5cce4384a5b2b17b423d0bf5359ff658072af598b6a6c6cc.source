package com.slte.app.ui.screen.plans

import com.slte.app.domain.model.CheckoutResult
import com.slte.app.domain.model.isOrderActivated

enum class CheckoutDecision {

    SUCCESS,

    REDIRECT,

    RETRY,
}

enum class PollOutcome { TERMINATED, COMPLETED }

fun pollOutcome(status: Int?): PollOutcome? = when {
    status == null || status == 0 -> null
    isOrderActivated(status) -> PollOutcome.COMPLETED
    else -> PollOutcome.TERMINATED
}

fun decideCheckoutStep(result: CheckoutResult): CheckoutDecision = when (result.type) {
    -1 -> CheckoutDecision.SUCCESS
    1 -> if (result.redirectUrl != null) CheckoutDecision.REDIRECT else CheckoutDecision.RETRY
    2 -> if (result.paid) CheckoutDecision.SUCCESS else CheckoutDecision.RETRY
    else -> CheckoutDecision.RETRY
}

internal fun computeCouponDiscount(
    type: Int,
    value: Int,
    priceCents: Int,
): Int = when (type) {
    2 -> priceCents * value / 100
    else -> value
}.coerceAtLeast(0)

internal fun finalPriceCents(
    priceCents: Int,
    couponDiscount: Int,
): Int = (priceCents - couponDiscount).coerceAtLeast(0)
