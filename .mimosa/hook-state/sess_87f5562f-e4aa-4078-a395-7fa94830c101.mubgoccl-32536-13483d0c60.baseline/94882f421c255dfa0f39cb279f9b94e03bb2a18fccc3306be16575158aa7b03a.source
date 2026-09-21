package com.slte.app.ui.screen.plans

import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.OrderInfo
import com.slte.app.domain.model.OrderStatus
import com.slte.app.domain.model.PaymentMethod
import com.slte.app.utils.ErrorMessages
import javax.inject.Inject

sealed interface OrderPaymentLoad {
    data class Ready(
        val detail: OrderInfo,
        val methods: List<PaymentMethod>,
    ) : OrderPaymentLoad

    data class Failed(val messageRes: Int) : OrderPaymentLoad

    object AlreadyPaid : OrderPaymentLoad

    object Cancelled : OrderPaymentLoad
}

class OrderPaymentLoader
@Inject
constructor(
    private val orderRepository: OrderRepository,
) {
    suspend fun load(tradeNo: String): OrderPaymentLoad {
        val methodsResult = orderRepository.getPaymentMethods()
        val detailResult = orderRepository.getOrderDetail(tradeNo)

        val methods = methodsResult.getOrNull() ?: emptyList()
        val detail = detailResult.getOrNull()
        if (detail == null) {
            return OrderPaymentLoad.Failed(ErrorMessages.forOrder(detailResult.exceptionOrNull()))
        }
        return when (OrderStatus.from(detail.status)) {
            OrderStatus.COMPLETED -> OrderPaymentLoad.AlreadyPaid
            OrderStatus.CANCELLED -> OrderPaymentLoad.Cancelled
            else -> OrderPaymentLoad.Ready(detail = detail, methods = methods)
        }
    }
}
