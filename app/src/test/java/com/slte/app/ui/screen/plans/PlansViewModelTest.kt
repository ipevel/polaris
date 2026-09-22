// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import com.slte.app.data.remote.api.dto.PlanInfoDto
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

class PlansViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val repository = OrderRepository(api)

    private fun plan(
        id: Int,
        show: Boolean = true,
    ) = PlanInfoDto(
        id = id,
        name = "套餐 $id",
        transferEnable = 1024,
        monthPrice = 5_000,
        show = show,
    )

    @Test
    fun `加载成功后进入空闲并过滤下架套餐`() = runTest(mainRule.dispatcher) {
        api.plans = listOf(plan(1), plan(2, show = false))
        val vm = PlansViewModel(repository)

        assertEquals(ContentPhase.Loading, vm.data.value.phase)

        vm.enterAndRefresh()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals("下架套餐不应展示", 1, vm.data.value.plans.size)
        assertTrue(!vm.data.value.isEntering)
    }

    @Test
    fun `加载失败进入空闲并带错误资源`() = runTest(mainRule.dispatcher) {
        api.plansError = java.io.IOException("boom")
        val vm = PlansViewModel(repository)

        vm.retry()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(com.slte.app.R.string.error_order_failed, vm.data.value.errorMessageRes)
    }

    @Test
    fun `下拉刷新期间阶段为 Refreshing`() = runTest(mainRule.dispatcher) {
        api.plans = listOf(plan(1))
        val vm = PlansViewModel(repository)
        vm.enterAndRefresh()
        advanceUntilIdle()

        vm.refresh()
        assertEquals(ContentPhase.Refreshing, vm.data.value.phase)

        advanceUntilIdle()
        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(null, vm.data.value.errorMessageRes)
    }

    @Test
    fun `刷新失败清空错误并保留阶段收敛`() = runTest(mainRule.dispatcher) {
        api.plans = listOf(plan(1))
        val vm = PlansViewModel(repository)
        vm.enterAndRefresh()
        advanceUntilIdle()

        api.plansError = java.io.IOException("boom")
        vm.refresh()
        advanceUntilIdle()

        assertEquals(ContentPhase.Idle, vm.data.value.phase)
        assertEquals(com.slte.app.R.string.error_order_failed, vm.data.value.errorMessageRes)
    }
}
