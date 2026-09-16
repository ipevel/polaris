package com.slte.app.ui.screen.plans

import com.slte.app.data.remote.ApiException
import com.slte.app.data.remote.api.dto.CouponCheckResultDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponCheckerTest {
    private val api = FakeAuthApi()
    private val checker = CouponChecker(OrderRepository(api))

    @Test
    fun `百分比折扣按下单金额计算`() {
        api.couponResult = CouponCheckResultDto(name = "八折", type = 2, value = 20)

        val result = check(priceCents = 5_000)

        assertEquals(CouponCheck.Applied(1_000), result)
        assertEquals("去空格后的码", "SAVE", api.couponCode)
    }

    @Test
    fun `固定金额减扣直接取值且不低于零`() {
        api.couponResult = CouponCheckResultDto(name = "减 5 元", type = 1, value = 500)
        assertEquals(CouponCheck.Applied(500), check(priceCents = 5_000))

        api.couponResult = CouponCheckResultDto(name = "超额券", type = 1, value = 9_999)
        assertEquals(CouponCheck.Applied(9_999), check(priceCents = 5_000))
    }

    @Test
    fun `校验失败映射为错误提示资源`() {
        api.couponError = ApiException(message = "优惠券无效", stringResId = com.slte.app.R.string.error_coupon_invalid)

        val result = check(priceCents = 5_000)

        assertTrue("失败应走 Rejected", result is CouponCheck.Rejected)
        assertEquals(
            com.slte.app.R.string.error_coupon_invalid,
            (result as CouponCheck.Rejected).messageRes,
        )
    }

    @Test
    fun `非协议异常走通用订单错误`() {
        api.couponError = java.io.IOException("网络中断")

        val result = check(priceCents = 5_000)

        assertTrue(result is CouponCheck.Rejected)
        assertEquals(
            com.slte.app.R.string.error_order_failed,
            (result as CouponCheck.Rejected).messageRes,
        )
    }

    private fun check(priceCents: Int): CouponCheck = kotlinx.coroutines.runBlocking {
        checker.check(code = " SAVE ", planId = 7, priceCents = priceCents)
    }
}
