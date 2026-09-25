// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.R
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.ServerNode
import com.slte.app.domain.model.ServerType
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.support.MainDispatcherRule
import com.slte.app.support.stubKernelBridge
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val subscribeRepository = mockk<SubscribeRepository>(relaxed = true)
    private val kernelProxy = mockk<KernelProxy>(relaxed = true)

    private fun viewModel(): ServerViewModel {
        kernelProxy.stubKernelBridge()
        every { subscribeRepository.getCachedSubscribeInfo() } returns null
        return ServerViewModel(serverRepository, subscribeRepository, kernelProxy)
    }

    private fun node(
        name: String,
        id: Int = 1,
    ) = ServerNode(id = id, name = name, type = ServerType.VMESS, host = "h.example.com", port = 443)

    @Test
    fun `加载成功后填充节点并标注国家码`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns
            Result.success(listOf(node("香港01", 1), node("美国01", 2), node("香港01", 3)))
        val vm = viewModel()

        vm.loadNodes(force = true)
        advanceUntilIdle()

        val nodes = vm.data.value.nodes
        assertEquals("同名节点应去重", 2, nodes.size)
        assertEquals("HK", nodes.first { it.name == "香港01" }.countryCode)
        assertEquals("US", nodes.first { it.name == "美国01" }.countryCode)
        assertTrue(!vm.data.value.isLoading)
    }

    @Test
    fun `加载失败提示节点错误`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.retry()
        advanceUntilIdle()

        assertEquals(R.string.error_server_load, vm.errorMessageRes.value)
        assertTrue(!vm.data.value.isLoading)
    }

    @Test
    fun `选中普通节点调用内核选择并更新选中项`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.success(listOf(node("香港01", 1)))
        val vm = viewModel()
        vm.loadNodes(force = true)
        advanceUntilIdle()

        val target = vm.data.value.nodes.first()
        vm.selectNode(target.id)
        advanceUntilIdle()

        assertEquals(target.id, vm.data.value.selectedNodeId)
    }

    @Test
    fun `选中自动选择走专用内核入口`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.selectNode(0)
        advanceUntilIdle()

        assertEquals(0, vm.data.value.selectedNodeId)
    }

    @Test
    fun `测速中状态标记进行中，重复触发不叠加`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.startSpeedTest()
        assertTrue("测速应标记进行中", vm.isTestingAll.value)

        vm.startSpeedTest()
        assertTrue("重复触发不应改变状态", vm.isTestingAll.value)

        advanceUntilIdle()
        assertTrue("测速结束后应复位", !vm.isTestingAll.value)
    }

    @Test
    fun `全组测速完成后给出完成提示并刷新策略组延迟`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubClashGroups(listOf("香港01" to 42, "香港02" to 88), now = "香港01")

        vm.startSpeedTest()
        advanceUntilIdle()

        assertEquals(R.string.server_speed_test_done, vm.speedTestTipRes.value)
        assertTrue("测速结束应复位", !vm.isTestingAll.value)
        val members = vm.proxyGroups.value.first().members.associate { it.name to it.delay }
        assertEquals("策略组成员应带出内核实时延迟", 42, members["香港01"])

        vm.consumeSpeedTestTip()
        assertNull(vm.speedTestTipRes.value)
    }

    @Test
    fun `测速拿不到延迟时提示失败而非静默`() = runTest(mainRule.dispatcher) {
        // 内核未就绪 → 测速结果为空，此前这种失败是静默的
        val vm = viewModel()

        vm.startSpeedTest()
        advanceUntilIdle()

        assertEquals(R.string.server_speed_test_failed, vm.speedTestTipRes.value)
        assertTrue("测速结束应复位", !vm.isTestingAll.value)
    }

    @Test
    fun `策略组加载时用测速缓存回填未测出的延迟`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        stubClashGroups(listOf("香港01" to 0, "香港02" to 77), now = "香港01")
        every { kernelProxy.speedResultStore.getSpeedResults() } returns mapOf("香港01" to 32)

        vm.loadProxyGroups()
        advanceUntilIdle()

        val members = vm.proxyGroups.value.first().members.associate { it.name to it.delay }
        assertEquals("内核未测出的成员回填缓存", 32, members["香港01"])
        assertEquals("内核已测出的成员保留实时值", 77, members["香港02"])
    }

    @Test
    fun `无套餐时测速与更新订阅弹提示而非静默`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        every { subscribeRepository.getCachedSubscribeInfo() } returns
            SubscribeInfo(planName = "", transferEnable = 0L, usedTraffic = 0L, expiredAt = 0L)

        vm.startSpeedTest()
        assertEquals(R.string.dashboard_no_plan_tip, vm.errorMessageRes.value)
        assertTrue("无套餐不应进入测速态", !vm.isTestingAll.value)

        vm.dismissError()
        vm.updateSubscription()
        assertEquals(R.string.dashboard_no_plan_tip, vm.errorMessageRes.value)
    }

    @Test
    fun `策略组内切换失败时提示错误`() = runTest(mainRule.dispatcher) {
        // 未桩化内核时 selectInGroup 扩展函数实际执行后 clash==null，safe 返回 false，触发失败路径
        val vm = viewModel()

        vm.selectInGroup(GROUP_NAME, "香港01")
        advanceUntilIdle()

        assertEquals(R.string.proxy_group_select_failed, vm.errorMessageRes.value)
    }

    @Test
    fun `购买后刷新节点成功填充列表`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.success(listOf(node("香港01", 1)))
        val vm = viewModel()

        vm.refreshNodesForPurchase()
        advanceUntilIdle()

        assertEquals(1, vm.data.value.nodes.size)
        assertNull(vm.errorMessageRes.value)
    }

    @Test
    fun `购买后刷新节点失败提示错误`() = runTest(mainRule.dispatcher) {
        coEvery { serverRepository.fetchServers(any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.refreshNodesForPurchase()
        advanceUntilIdle()

        assertEquals(R.string.error_server_load, vm.errorMessageRes.value)
    }

    /** 以桩化的内核策略组替换默认的"未就绪"内核，供测速 / 策略组用例使用。 */
    private fun stubClashGroups(
        members: List<Pair<String, Int>>,
        now: String,
    ) {
        // 保留 safe() 直通桩，再把它挂到的 manager 换成可读策略组的内核
        kernelProxy.stubKernelBridge()
        val clash = mockk<IClashManager>(relaxed = true)
        val proxies =
            members.map { (name, delay) ->
                Proxy(name = name, title = name, subtitle = "", type = "vmess", delay = delay, isGroup = false)
            }
        every { clash.queryProxyGroupNames(any()) } returns listOf(GROUP_NAME)
        every { clash.queryProxyGroup(any(), any()) } returns ProxyGroup(type = "Selector", proxies = proxies, now = now)

        val manager = mockk<KernelManager>(relaxed = true)
        every { manager.clash() } returns clash
        every { kernelProxy.manager } returns manager
    }

    private companion object {

        const val GROUP_NAME = "节点选择"
    }
}
