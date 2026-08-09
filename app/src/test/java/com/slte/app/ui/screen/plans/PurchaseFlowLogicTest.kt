package com.slte.app.ui.screen.plans

import com.slte.app.domain.model.CheckoutResult
import com.slte.app.domain.model.OrderStatus
import com.slte.app.domain.model.PlanInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 支付流程决策与订单状态分类（纯函数） */
class PurchaseFlowLogicTest {

    @Test
    fun `结算类型负一判定支付成功`() {
        assertEquals(
            CheckoutDecision.SUCCESS,
            decideCheckoutStep(CheckoutResult(type = -1))
        )
    }

    @Test
    fun `结算类型零或一且带跳转地址判定跳转`() {
        assertEquals(
            CheckoutDecision.REDIRECT,
            decideCheckoutStep(CheckoutResult(type = 0, redirectUrl = "https://pay.example"))
        )
        assertEquals(
            CheckoutDecision.REDIRECT,
            decideCheckoutStep(CheckoutResult(type = 1, redirectUrl = "https://pay.example"))
        )
    }

    @Test
    fun `结算类型零或一但缺跳转地址判定重试`() {
        assertEquals(
            CheckoutDecision.RETRY,
            decideCheckoutStep(CheckoutResult(type = 0, redirectUrl = null))
        )
        assertEquals(
            CheckoutDecision.RETRY,
            decideCheckoutStep(CheckoutResult(type = 1, redirectUrl = null))
        )
    }

    @Test
    fun `未知结算类型判定重试`() {
        assertEquals(
            CheckoutDecision.RETRY,
            decideCheckoutStep(CheckoutResult(type = 99))
        )
    }

    @Test
    fun `订单状态码映射到分类`() {
        assertEquals(OrderStatus.PENDING, OrderStatus.from(0))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.from(1))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.from(2))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.from(3))
        assertEquals(OrderStatus.ABNORMAL, OrderStatus.from(-1))
        assertEquals(OrderStatus.ABNORMAL, OrderStatus.from(9))
    }

    @Test
    fun `应付金额钳制为非负`() {
        val step = PurchaseStep.OrderPayment(
            tradeNo = "T",
            planName = "P",
            totalAmount = 100,
            balanceAmount = 200,
            couponDiscount = 0,
            handlingAmount = 0
        )
        assertEquals(0, step.payAmount)
    }

    @Test
    fun `优惠后应付金额钳制为非负`() {
        val step = PurchaseStep.SelectPeriod(
            plan = PlanInfo(
                id = 1,
                name = "P",
                periodPrices = listOf(PlanInfo.PeriodPrice(period = "month", price = "100"))
            ),
            selectedPeriod = "month",
            couponDiscount = 500
        )
        assertEquals(0, step.finalPrice)
    }

    @Test
    fun `轮询状态待支付或缺失时继续等待`() {
        assertNull(pollOutcome(null))
        assertNull(pollOutcome(0))
    }

    @Test
    fun `轮询状态支付完成判定完成`() {
        assertEquals(PollOutcome.COMPLETED, pollOutcome(1))
        assertEquals(PollOutcome.COMPLETED, pollOutcome(3))
    }

    @Test
    fun `轮询状态取消或异常判定终态停止`() {
        assertEquals(PollOutcome.TERMINATED, pollOutcome(2))
        assertEquals(PollOutcome.TERMINATED, pollOutcome(-1))
        assertEquals(PollOutcome.TERMINATED, pollOutcome(9))
    }
}
