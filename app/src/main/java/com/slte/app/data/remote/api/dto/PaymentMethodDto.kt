package com.slte.app.data.remote.api.dto

/**
 * 支付方式（来自 [com.slte.app.data.remote.api.AuthApi.getPaymentMethods]）。
 */
data class PaymentMethodDto(
    val id: Int,
    val name: String,
    val payment: String = "",
    val icon: String? = null
)

/**
 * 创建订单结果。
 */
data class CreateOrderResultDto(
    val tradeNo: String
)

/**
 * 支付结算结果。
 *
 * @param type -1=免费/余额支付成功 0=跳转支付链接 1=表单提交
 * @param redirectUrl 支付跳转 URL
 * @param message 提示消息
 */
data class CheckoutResultDto(
    val type: Int,
    val redirectUrl: String? = null,
    val message: String? = null
)
