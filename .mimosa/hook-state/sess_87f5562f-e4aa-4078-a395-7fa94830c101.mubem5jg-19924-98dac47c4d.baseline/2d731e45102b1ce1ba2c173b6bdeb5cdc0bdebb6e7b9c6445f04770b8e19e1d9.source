package com.slte.app.data.remote.api.dto

data class PaymentMethodDto(
    val id: Int,
    val name: String,
    val payment: String = "",
    val icon: String? = null,
)

data class CreateOrderResultDto(
    val tradeNo: String,
)

data class CheckoutResultDto(
    val type: Int,
    val redirectUrl: String? = null,
    val message: String? = null,
    val paid: Boolean = false,
) {
    companion object {

        fun fromRawJson(raw: String): CheckoutResultDto? = runCatching {
            val json = org.json.JSONObject(raw)
            CheckoutResultDto(
                type = json.optInt("type", 0),
                redirectUrl = (json.opt("data") as? String)?.takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() },
                paid = json.optBoolean("data", false),
            )
        }.getOrNull()
    }
}
