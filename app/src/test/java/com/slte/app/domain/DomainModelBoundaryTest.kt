// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain

import com.slte.app.domain.model.OrderInfo
import com.slte.app.domain.model.OrderStatus
import com.slte.app.domain.model.PlanInfo
import com.slte.app.domain.model.isOrderActivated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanInfoPeriodPriceTest {

    @Test
    fun `周期价格按周期名精确匹配`() {
        val plan = plan(listOf("month_price" to "12.00", "year_price" to "120.00"))

        assertEquals("12.00", plan.priceForPeriod("month_price"))
        assertEquals("120.00", plan.priceForPeriod("year_price"))
        assertNull("缺少后缀不得匹配", plan.priceForPeriod("month"))
        assertNull("大小写必须一致", plan.priceForPeriod("MONTH_PRICE"))
        assertNull(plan.priceForPeriod(""))
        assertEquals(listOf("month_price", "year_price"), plan.availablePeriods)
    }

    @Test
    fun `重复周期取首个价格`() {
        val plan = plan(listOf("month_price" to "12.00", "month_price" to "99.00"))

        assertEquals("12.00", plan.priceForPeriod("month_price"))
        assertEquals("可用周期原样保留并允许重复", listOf("month_price", "month_price"), plan.availablePeriods)
    }

    @Test
    fun `无周期价格时返回空列表与空匹配`() {
        val plan = plan(emptyList())

        assertTrue(plan.availablePeriods.isEmpty())
        assertNull(plan.priceForPeriod("month_price"))
    }

    @Test
    fun `可用周期保持后端返回顺序`() {
        val plan = plan(listOf("two_year_price" to "1", "onetime_price" to "2", "month_price" to "3"))

        assertEquals(listOf("two_year_price", "onetime_price", "month_price"), plan.availablePeriods)
    }

    private fun plan(periodPrices: List<Pair<String, String>>) = PlanInfo(
        id = 1L,
        name = "Pro",
        periodPrices = periodPrices.map { PlanInfo.PeriodPrice(period = it.first, price = it.second) },
    )
}

class OrderStatusBoundaryTest {

    @Test
    fun `状态码一到四判定为已完成`() {
        assertEquals(OrderStatus.COMPLETED, OrderStatus.from(1))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.from(3))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.from(4))
        assertTrue(isOrderActivated(1))
        assertTrue(isOrderActivated(3))
        assertTrue(isOrderActivated(4))
    }

    @Test
    fun `未知与越界状态码判定为异常`() {
        val unknown = listOf(5, 6, 99, Int.MAX_VALUE, Int.MIN_VALUE, -1)

        unknown.forEach { code ->
            assertEquals("状态码 $code 必须判为异常", OrderStatus.ABNORMAL, OrderStatus.from(code))
            assertFalse("状态码 $code 不得视为已开通", isOrderActivated(code))
        }
    }

    @Test
    fun `待支付与已取消不视为已开通`() {
        assertEquals(OrderStatus.PENDING, OrderStatus.from(0))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.from(2))
        assertFalse(isOrderActivated(0))
        assertFalse(isOrderActivated(2))
    }

    @Test
    fun `订单状态分类与开通判定同源`() {
        val codes = listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 99, Int.MAX_VALUE)

        codes.forEach { code ->
            assertEquals(
                "状态码 $code 的分类与开通判定必须一致",
                OrderStatus.from(code) == OrderStatus.COMPLETED,
                isOrderActivated(code),
            )
        }
    }

    @Test
    fun `订单信息的状态分类跟随状态码`() {
        assertEquals(OrderStatus.PENDING, order(status = 0).statusClass)
        assertEquals(OrderStatus.COMPLETED, order(status = 3).statusClass)
        assertEquals(OrderStatus.CANCELLED, order(status = 2).statusClass)
        assertEquals(OrderStatus.ABNORMAL, order(status = 42).statusClass)
    }

    private fun order(status: Int) = OrderInfo(
        id = 1,
        tradeNo = "TN",
        planName = "Pro",
        totalAmount = 100,
        status = status,
        createdAt = 0L,
        expiredAt = 0L,
    )
}
