// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OrderPaymentPollerTest {
    private val api = FakeAuthApi()
    private val poller = OrderPaymentPoller(OrderRepository(api))

    @Test
    fun `订单开通后触发完成回调`() = runBlocking {
        api.orderDetail = order(status = 3)
        var completedTradeNo: String? = null

        poller.start(this, "TN-1") { completedTradeNo = it }
        delay(4_000)

        assertEquals("TN-1", completedTradeNo)
        assertEquals(1, api.orderDetailCalls)
    }

    @Test
    fun `同一订单重复启动只轮询一次`() = runBlocking {
        api.orderDetail = order(status = 3)

        poller.start(this, "TN-1") { }
        poller.start(this, "TN-1") { }
        delay(4_000)

        assertEquals("去重后只应发起一次查询", 1, api.orderDetailCalls)
    }

    @Test
    fun `停掉轮询后不再查询也不再回调`() = runBlocking {
        api.orderDetail = order(status = 3)

        poller.start(this, "TN-1") { fail("已停止的轮询不应回调") }
        poller.stop()
        delay(4_000)

        assertEquals(0, api.orderDetailCalls)
    }

    @Test
    fun `订单未支付时继续等待且不回调`() = runBlocking {
        api.orderDetail = order(status = 0)
        var completed = false

        poller.start(this, "TN-1") { completed = true }
        delay(4_000)

        assertEquals("待支付应继续轮询", 1, api.orderDetailCalls)
        assertEquals("未开通不应回调", false, completed)
    }

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
