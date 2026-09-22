// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.R

internal fun OrderPaymentLoad.toToastRes(): Int? = when (this) {
    is OrderPaymentLoad.Failed -> messageRes
    OrderPaymentLoad.AlreadyPaid -> R.string.order_already_paid
    OrderPaymentLoad.Cancelled -> R.string.order_status_cancelled
    is OrderPaymentLoad.Ready -> null
}

internal fun OrderPaymentLoad.Ready.toPaymentStep(tradeNo: String): PurchaseStep.OrderPayment = PurchaseStep.OrderPayment(
    tradeNo = tradeNo,
    planName = detail.planName,
    totalAmount = detail.totalAmount,
    balanceAmount = detail.balanceAmount,
    couponDiscount = detail.discountAmount,
    surplusAmount = detail.surplusAmount,
    refundAmount = detail.refundAmount,
    handlingAmount = detail.handlingAmount ?: 0,
    paymentMethods = methods,
    selectedMethod = methods.firstOrNull()?.id,
    isLoading = false,
)

internal fun OrderCreateOutcome.nextStep(
    current: PurchaseStep.SelectPeriod,
    couponCode: String?,
): PurchaseStep = when (this) {
    is OrderCreateOutcome.Created -> PurchaseStep.Idle
    is OrderCreateOutcome.ExistingOrderPending ->
        PurchaseStep.ExistingOrderError(
            errorMessageRes = errorMessageRes,
            plan = current.plan,
            period = current.selectedPeriod,
            couponCode = couponCode,
        )
    is OrderCreateOutcome.Failed -> PurchaseStep.OrderCreateError(errorMessageRes = errorMessageRes)
}

internal fun CheckoutOutcome.nextStep(current: PurchaseStep.OrderPayment): PurchaseStep = when (this) {
    is CheckoutOutcome.Completed -> PurchaseStep.Idle
    is CheckoutOutcome.Redirect -> PurchaseStep.Paying(redirectUrl = redirectUrl)
    is CheckoutOutcome.Retry -> current.copy(isPaying = false)
}

internal fun CheckoutOutcome.toToastRes(): Int? = when (this) {
    is CheckoutOutcome.Completed -> R.string.order_pay_success
    is CheckoutOutcome.Redirect -> null
    is CheckoutOutcome.Retry -> messageRes
}

internal fun PurchaseStep.ExistingOrderError.toSelectPeriod(): PurchaseStep.SelectPeriod = PurchaseStep.SelectPeriod(
    plan = plan,
    selectedPeriod = period,
    couponCode = couponCode ?: "",
)
