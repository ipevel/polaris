// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.order

import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.MainDispatcherRule
import com.slte.app.ui.ContentPhase
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrdersViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val repository = OrderRepository(api)

    private fun order(id: Int) = OrderInfoDto(
        id = id,
        tradeNo = "TN-$id",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = 0,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )

    @Test
    fun `初始为加载中，拉取成功后进入空闲并填充订单`() = runTest(mainRule.dispatcher) {
        api.orders = listOf(order(1), order(2))
        val vm = OrdersViewModel(repository)

        assertEquals(ContentPhase.Loading, vm.data.value.phase)

        vm.enterAndRefresh()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(2, vm.data.value.orders.size)
        assertTrue("预加载结束后应可进入页面", !vm.data.value.isEntering)
    }

    @Test
    fun `拉取失败进入空闲并带错误资源`() = runTest(mainRule.dispatcher) {
        api.ordersError = java.io.IOException("boom")
        val vm = OrdersViewModel(repository)

        vm.retry()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(com.slte.app.R.string.error_order_failed, vm.data.value.errorMessageRes)
    }

    @Test
    fun `下拉刷新期间阶段为 Refreshing 且内容保留`() = runTest(mainRule.dispatcher) {
        api.orders = listOf(order(1))
        val vm = OrdersViewModel(repository)
        vm.enterAndRefresh()
        advanceUntilIdle()

        vm.refresh()
        assertEquals(ContentPhase.Refreshing, vm.data.value.phase)

        advanceUntilIdle()
        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(1, vm.data.value.orders.size)
        assertEquals(null, vm.data.value.errorMessageRes)
    }

    @Test
    fun `取消订单成功后提示并重新拉取`() = runTest(mainRule.dispatcher) {
        api.orders = listOf(order(1))
        val vm = OrdersViewModel(repository)
        vm.enterAndRefresh()
        advanceUntilIdle()

        vm.cancelOrder("TN-1")
        advanceUntilIdle()

        assertEquals(com.slte.app.R.string.order_cancel_success, vm.data.value.toastRes)
    }

    @Test
    fun `取消订单失败只提示不重拉`() = runTest(mainRule.dispatcher) {
        api.orders = listOf(order(1))
        api.cancelError = java.io.IOException("boom")
        val vm = OrdersViewModel(repository)
        vm.enterAndRefresh()
        advanceUntilIdle()

        vm.cancelOrder("TN-1")
        advanceUntilIdle()

        assertEquals(com.slte.app.R.string.order_cancel_failed, vm.data.value.toastRes)
    }
}
