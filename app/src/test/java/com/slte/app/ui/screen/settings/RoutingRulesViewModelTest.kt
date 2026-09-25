// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import com.slte.app.R
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.RoutingCustomGroup
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

    @Test
    fun `自定义组名校验拦截空名与内置组冲突`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.showAddCustomGroup()
        vm.onCustomNameChange("")
        vm.onCustomUrlChange("https://example.com/rules.yaml")
        vm.submitCustomGroup()
        assertEquals(R.string.routing_custom_invalid_name, editingError(vm))

        vm.onCustomNameChange("🐟 漏网之鱼")
        vm.submitCustomGroup()
        assertEquals(R.string.routing_custom_invalid_name, editingError(vm))

        vm.onCustomNameChange("ok-name")
        vm.onCustomUrlChange("http://example.com/rules.yaml")
        vm.submitCustomGroup()
        assertEquals(R.string.routing_custom_invalid_url, editingError(vm))

        vm.onCustomUrlChange("https://192.168.1.1/rules.yaml")
        vm.submitCustomGroup()
        assertEquals(R.string.routing_custom_invalid_url, editingError(vm))

        coVerify(exactly = 0) { kernelConfig.addRoutingCustomGroup(any()) }
    }

    @Test
    fun `自定义组提交成功后关闭弹层并刷新列表`() = runTest(mainRule.dispatcher) {
        val group = RoutingCustomGroup(name = "我的规则", url = "https://example.com/rules.yaml", behavior = "domain")
        coEvery { kernelConfig.addRoutingCustomGroup(any()) } returns true
        // 提交成功后 refresh() 会重读 load()，返回带自定义组的状态
        val vm = viewModel(state = RoutingState(custom = listOf(group)))
        vm.showAddCustomGroup()
        vm.onCustomNameChange("我的规则")
        vm.onCustomUrlChange("https://example.com/rules.yaml")
        vm.onCustomBehaviorChange("domain")
        vm.submitCustomGroup()
        advanceUntilIdle()

        assertTrue(vm.customGroupState.value is CustomGroupState.Closed)
        assertEquals(1, vm.data.value.custom.size)
        coVerify(exactly = 1) { kernelConfig.addRoutingCustomGroup(group) }
    }

    @Test
    fun `同名自定义组存储层拒绝后提示重复`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.addRoutingCustomGroup(any()) } returns false
        val vm = viewModel()

        vm.showAddCustomGroup()
        vm.onCustomNameChange("重复组")
        vm.onCustomUrlChange("https://example.com/rules.yaml")
        vm.submitCustomGroup()
        advanceUntilIdle()

        val state = vm.customGroupState.value as CustomGroupState.Editing
        assertEquals(R.string.routing_custom_duplicate_name, state.errorMessageRes)
    }

    @Test
    fun `删除自定义组走内核通道`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.removeRoutingCustomGroup("x") } returns true
        val vm = viewModel()

        vm.removeCustomGroup("x")
        advanceUntilIdle()

        coVerify(exactly = 1) { kernelConfig.removeRoutingCustomGroup("x") }
    }

    private fun editingError(vm: RoutingRulesViewModel): Int? = (vm.customGroupState.value as? CustomGroupState.Editing)?.errorMessageRes
}
