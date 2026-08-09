package com.slte.app.domain.model

/** 订单状态分类 */
enum class OrderStatus {
    PENDING, COMPLETED, CANCELLED, ABNORMAL;

    companion object {
        /** 后端状态码 → 分类：0=待支付 1=已支付 2=已取消 3=已开通 */
        fun from(code: Int): OrderStatus = when (code) {
            0 -> PENDING
            1, 3 -> COMPLETED
            2 -> CANCELLED
            else -> ABNORMAL
        }
    }
}

data class OrderInfo(
    val id: Int,
    /** 订单号 */
    val tradeNo: String,
    /** 套餐名称 */
    val planName: String,
    /** 金额（分） */
    val totalAmount: Int,
    /** 余额抵扣（分） */
    val balanceAmount: Int = 0,
    /** 优惠券抵扣（分） */
    val discountAmount: Int = 0,
    /** 手续费（分），无手续费为 null */
    val handlingAmount: Int? = null,
    /** 订单状态：0=待支付 1=已支付 2=已取消 3=已开通 */
    val status: Int,
    /** 周期标识：month_price / quarter_price / year_price 等 */
    val period: String = "",
    /** 创建时间戳（秒） */
    val createdAt: Long,
    /** 到期时间戳（秒），0 表示无到期时间 */
    val expiredAt: Long
) {
    /** 状态分类（UI 展示语义） */
    val statusClass: OrderStatus get() = OrderStatus.from(status)
}

data class PaymentMethod(
    val id: Int,
    val name: String,
    val payment: String = "",
    val icon: String? = null
)

data class CreateOrderResult(
    val tradeNo: String
)

data class CheckoutResult(
    /** -1=免费/余额支付成功 0=跳转支付链接 1=表单提交 */
    val type: Int,
    val redirectUrl: String? = null,
    val message: String? = null
)

data class CouponCheckResult(
    val name: String,
    /** 2=百分比折扣，1=固定金额减扣 */
    val type: Int,
    /** 百分比时表示折扣百分数，固定金额时表示减扣金额（分） */
    val value: Int
)
