// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.remote.api.dto.PaymentMethodDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPaymentLoaderTest {
    private val api = FakeAuthApi()
    private val loader = OrderPaymentLoader(OrderRepository(api))

    @Test
    fun `待支付订单返回详情与支付方式`() {
        api.orderDetail = order(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))

        val result = load()

        assertTrue(result is OrderPaymentLoad.Ready)
        result as OrderPaymentLoad.Ready
        assertEquals("进阶套餐", result.detail.planName)
        assertEquals(1, result.methods.size)
        assertEquals(3, result.methods.first().id)
    }

    @Test
    fun `已完成订单不再进入支付`() {
        api.orderDetail = order(status = 3)
        assertTrue(load() is OrderPaymentLoad.AlreadyPaid)
    }

    @Test
    fun `已取消订单不再进入支付`() {
        api.orderDetail = order(status = 2)
        assertTrue(load() is OrderPaymentLoad.Cancelled)
    }

    @Test
    fun `详情拉取失败返回可展示的错误资源`() {
        api.orderDetailError = java.io.IOException("network down")

        val result = load()

        assertTrue(result is OrderPaymentLoad.Failed)
        assertEquals(
            com.slte.app.R.string.error_order_failed,
            (result as OrderPaymentLoad.Failed).messageRes,
        )
    }

    @Test
    fun `支付方式拉取失败仍可展示订单（列表降级为空）`() {
        api.orderDetail = order(status = 0)
        api.paymentMethods = emptyList()

        val result = load()

        assertTrue(result is OrderPaymentLoad.Ready)
        assertTrue((result as OrderPaymentLoad.Ready).methods.isEmpty())
    }

    private fun load(): OrderPaymentLoad = runBlocking { loader.load("TN-1") }

    private fun order(status: Int): OrderInfoDto = OrderInfoDto(
        id = 1,
        tradeNo = "TN-1",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = status,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )
}
