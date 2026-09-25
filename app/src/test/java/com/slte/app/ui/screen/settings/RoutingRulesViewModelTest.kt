// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import com.slte.app.R
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.RoutingState
import com.slte.app.kernel.RoutingStateStore
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoutingRulesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val routingStateStore = mockk<RoutingStateStore>(relaxed = true)
    private val kernelConfig = mockk<KernelConfig>(relaxed = true)

    private fun viewModel(state: RoutingState = RoutingState()): RoutingRulesViewModel {
        every { routingStateStore.load() } returns state
        return RoutingRulesViewModel(routingStateStore, kernelConfig)
    }

    @Test
    fun `列表生效开关优先取覆盖值否则取内置默认`() = runTest(mainRule.dispatcher) {
        val vm =
            viewModel(
                RoutingState(groups = mapOf("📲 电报消息" to true, "🍎 苹果服务" to false)),
            )

        val items = vm.data.value.items
        assertTrue(items.isNotEmpty())
        assertEquals(true, items.first { it.name == "📲 电报消息" }.enabled) // 覆盖值优先
        assertEquals(false, items.first { it.name == "🍎 苹果服务" }.enabled) // 覆盖值优先
        assertEquals(true, items.first { it.name == "🌏 国外穿墙" }.enabled) // 默认开
        assertEquals(false, items.first { it.name == "🛑 广告拦截" }.enabled) // 默认关
    }

    @Test
    fun `开关成功后回到 Idle 并计数`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.applyRoutingGroup("📲 电报消息", true) } returns true
        val vm = viewModel()

        vm.setEnabled("📲 电报消息", true)
        advanceUntilIdle()

        assertEquals(RoutingSync.Idle, vm.data.value.sync)
        assertEquals(1, vm.data.value.savedCount)
        assertTrue(vm.data.value.items.first { it.name == "📲 电报消息" }.enabled)
        coVerify(exactly = 1) { kernelConfig.applyRoutingGroup("📲 电报消息", true) }
    }

    @Test
    fun `开关失败回滚到该组内置默认并提示`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.applyRoutingGroup("🛑 广告拦截", true) } returns false
        val vm = viewModel()

        vm.setEnabled("🛑 广告拦截", true)
        advanceUntilIdle()

        // 广告拦截内置默认为关：写盘失败回滚为关
        assertTrue(!vm.data.value.items.first { it.name == "🛑 广告拦截" }.enabled)
        assertEquals(RoutingSync.Idle, vm.data.value.sync)
        assertEquals(R.string.settings_local_routing_failed, vm.data.value.errorMessageRes)
    }

    @Test
    fun `恢复默认清空覆盖并回读默认值`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.resetRoutingGroups() } returns true
        every { routingStateStore.load() } returns
            RoutingState(groups = mapOf("📲 电报消息" to true))

        val vm = viewModel()
        vm.resetDefaults()
        advanceUntilIdle()

        assertTrue(!vm.data.value.items.first { it.name == "🛑 广告拦截" }.enabled)
        assertTrue(vm.data.value.items.first { it.name == "🌏 国外穿墙" }.enabled)
        coVerify(exactly = 1) { kernelConfig.resetRoutingGroups() }
    }
}
