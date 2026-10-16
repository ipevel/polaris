// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.NodesSectionStore
import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.RoutingCustomGroup
import com.slte.app.kernel.RoutingState
import com.slte.app.kernel.RoutingStateStore
import com.slte.app.support.MainDispatcherRule
import com.slte.app.support.stubKernelBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 自建组名缓存的**失效路径**（决策主管要求的合并前硬条件）。
 *
 * 缓存方案的唯一风险是「用户增删了自建组、缓存没跟上 → 误映射」，所以这里钉住：
 * 内核每次重载成功（[KernelManager.profileLoaded]，改分流规则会触发）后缓存必须更新；
 * 同时钉住「刷新分组不重新读盘」——有人摘掉那个 hook，本类必红。
 */
class ServerViewModelCustomGroupNamesTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val subscribeRepository = mockk<SubscribeRepository>(relaxed = true)
    private val kernelProxy = mockk<KernelProxy>(relaxed = true)
    private val kernelManager = mockk<KernelManager>(relaxed = true)
    private val nodesSectionStore = NodesSectionStore(InMemoryPreferences())
    private val kernelConfig = mockk<KernelConfig>(relaxed = true)
    private val routingStateStore = mockk<RoutingStateStore>(relaxed = true)

    /** relaxed mock 的 StateFlow 一 collect 就抛 KotlinNothingValueException，必须给真流。 */
    private val profileLoadedFlow = MutableStateFlow(0)

    private fun viewModel(): ServerViewModel {
        kernelProxy.stubKernelBridge()
        every { kernelProxy.config } returns kernelConfig
        every { kernelConfig.routingStateStore } returns routingStateStore
        every { subscribeRepository.getCachedSubscribeInfo() } returns null
        every { kernelManager.profileLoaded } returns profileLoadedFlow
        return ServerViewModel(serverRepository, subscribeRepository, kernelProxy, kernelManager, nodesSectionStore)
    }

    private fun stateOf(vararg names: String): RoutingState = RoutingState(custom = names.map { RoutingCustomGroup(it, "https://x.example.com/a.yaml") })

    @Test
    fun `增删自建组触发内核重载后缓存跟着更新`() = runTest(mainRule.dispatcher) {
        every { routingStateStore.readOrNull() } returns stateOf("我的组")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("初始化读一次", setOf("我的组"), vm.customGroupNames.value)

        // 用户在设置页新增了一条叫旧内置名的自建组 → 内核重载 → 缓存必须跟上
        every { routingStateStore.readOrNull() } returns stateOf("我的组", "Ⓜ️ 微软")
        profileLoadedFlow.value = 1
        advanceUntilIdle()
        assertEquals(setOf("我的组", "Ⓜ️ 微软"), vm.customGroupNames.value)

        // 再删掉一条 → 再次重载 → 缓存继续跟上
        every { routingStateStore.readOrNull() } returns stateOf("Ⓜ️ 微软")
        profileLoadedFlow.value = 2
        advanceUntilIdle()
        assertEquals(setOf("Ⓜ️ 微软"), vm.customGroupNames.value)
    }

    @Test
    fun `刷新分组不重新读盘（只在初始化与重载时读）`() = runTest(mainRule.dispatcher) {
        every { routingStateStore.readOrNull() } returns stateOf("我的组")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(setOf("我的组"), vm.customGroupNames.value)
        // 初始化 1 次 + StateFlow 初值回放 1 次（collect 会立刻收到当前值）
        verify(exactly = 2) { routingStateStore.readOrNull() }

        // 磁盘内容变了但没发生重载：缓存保持旧值（不重新读盘）
        every { routingStateStore.readOrNull() } returns stateOf("我的组", "新组")
        vm.loadProxyGroups()
        advanceUntilIdle()
        assertEquals("刷新路径不得重新读盘", setOf("我的组"), vm.customGroupNames.value)
        verify(exactly = 2) { routingStateStore.readOrNull() }
    }
}
