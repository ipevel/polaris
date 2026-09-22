// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.data.repository.OrderRepository
import com.slte.app.utils.ErrorMessages
import javax.inject.Inject

sealed interface OrderCreateOutcome {

    val tradeNo: String?
        get() = null

    data class Created(override val tradeNo: String) : OrderCreateOutcome

    data class ExistingOrderPending(val errorMessageRes: Int) : OrderCreateOutcome

    data class Failed(val errorMessageRes: Int) : OrderCreateOutcome
}

class OrderCreator
@Inject
constructor(
    private val orderRepository: OrderRepository,
) {
    suspend fun create(
        planId: Int,
        period: String,
        couponCode: String?,
    ): OrderCreateOutcome = orderRepository
        .createOrder(
            planId = planId,
            period = period,
            couponCode = couponCode,
        ).fold(
            onSuccess = { OrderCreateOutcome.Created(it.tradeNo) },
            onFailure = { e ->
                val errorMessageRes = ErrorMessages.forOrder(e)
                if (ErrorMessages.isPendingOrderMessage(e.message ?: "")) {
                    OrderCreateOutcome.ExistingOrderPending(errorMessageRes)
                } else {
                    OrderCreateOutcome.Failed(errorMessageRes)
                }
            },
        )
}
