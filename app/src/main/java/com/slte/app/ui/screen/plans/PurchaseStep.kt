package com.slte.app.ui.screen.plans

import com.slte.app.domain.model.PaymentMethod
import com.slte.app.domain.model.PlanInfo

sealed interface PurchaseStep {

    data class SelectPeriod(
        val plan: PlanInfo,
        val selectedPeriod: String = plan.periodPrices.firstOrNull()?.period ?: "",
        val couponCode: String = "",
        val couponDiscount: Int = 0,
        val couponVerified: Boolean = false,
        val isVerifying: Boolean = false,
        val showWarning: Boolean = false,
    ) : PurchaseStep {

        val priceCents: Int
            get() = plan.priceForPeriod(selectedPeriod)?.toIntOrNull() ?: 0

        val finalPrice: Int
            get() = finalPriceCents(priceCents, couponDiscount)
    }

    data class OrderPayment(
        val tradeNo: String,
        val planName: String,
        val totalAmount: Int,
        val balanceAmount: Int,
        val couponDiscount: Int,
        val surplusAmount: Int = 0,
        val refundAmount: Int = 0,
        val handlingAmount: Int,
        val paymentMethods: List<PaymentMethod> = emptyList(),
        val selectedMethod: Int? = null,
        val isLoading: Boolean = true,
        val isPaying: Boolean = false,
    ) : PurchaseStep {

        val productPrice: Int
            get() = (totalAmount + couponDiscount + surplusAmount + balanceAmount - refundAmount).coerceAtLeast(0)

        val payAmount: Int
            get() = (totalAmount + handlingAmount).coerceAtLeast(0)

        val zeroPayable: Boolean
            get() = payAmount <= 0
    }

    data class Paying(
        val redirectUrl: String,
    ) : PurchaseStep

    data class ExistingOrderError(
        val errorMessageRes: Int,
        val plan: PlanInfo,
        val period: String,
        val couponCode: String?,
    ) : PurchaseStep

    data class OrderCreateError(
        val errorMessageRes: Int,
    ) : PurchaseStep

    data object Idle : PurchaseStep
}
