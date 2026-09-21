package com.slte.app.ui.screen.main

import com.slte.app.R
import com.slte.app.data.local.ThemeMode
import com.slte.app.data.local.ThemePreference
import com.slte.app.data.remote.FallbackDns
import com.slte.app.data.repository.AuthRepository
import com.slte.app.domain.model.SessionState
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import com.slte.app.support.MainDispatcherRule
import com.slte.app.support.stubKernelBridge
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val kernelManager = mockk<KernelManager>(relaxed = true)
    private val kernelProxy = mockk<KernelProxy>(relaxed = true)
    private val kernelConfig = mockk<KernelConfig>(relaxed = true)
    private val fallbackDns = mockk<FallbackDns>(relaxed = true)
    private val subscriptionUpdater = mockk<SubscriptionUpdater>(relaxed = true)
    private val dataWriter = mockk<DashboardDataWriter>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val themePreference = mockk<ThemePreference>(relaxed = true)

    private fun viewModel(
        hasPlan: Boolean = true,
        connected: Boolean = false,
        ready: Boolean = false,
    ): MainViewModel {
        stubAuthSession()
        kernelProxy.stubKernelBridge(ready)
        every { kernelManager.connected } returns MutableStateFlow(connected)
        every { kernelManager.profileLoaded } returns MutableStateFlow(0)
        every { themePreference.mode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { dataWriter.applyCached(any()) } answers {
            firstArg<MutableStateFlow<DashboardData>>().value =
                DashboardData(hasPlan = hasPlan, isConnected = connected)
        }
        coEvery { authRepository.fetchSiteInfo(any()) } returns com.slte.app.domain.model.SiteInfo()
        return MainViewModel(mainRule.dispatcher, kernelManager, kernelProxy, kernelConfig, fallbackDns, subscriptionUpdater, dataWriter, authRepository, themePreference)
    }

    /**
     * relaxed mock 的 StateFlow.collect 契约返回 Nothing，未打桩时 mockk 返回 null
     * 会抛 KotlinNothingValueException；显式给一个恒定状态流。
     */
    private fun stubAuthSession(state: SessionState = SessionState.LoggedOut) {
        every { authRepository.sessionState } returns MutableStateFlow(state) as StateFlow<SessionState>
    }

    @Test
    fun `无套餐时点击连接不启动 VPN`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(hasPlan = false)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        verify(exactly = 0) { kernelManager.startVpn() }
        verify(exactly = 0) { kernelManager.stopVpn() }
    }

    @Test
    fun `内核配置不可用时提示内核不可用且不启动`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.ensureProfile() } returns null
        val vm = viewModel(hasPlan = true)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        assertEquals(R.string.error_vpn_kernel_unavailable, vm.data.value.errorMessageRes)
        assertTrue("失败后应复位连接中", !vm.data.value.isConnecting)
        verify(exactly = 0) { kernelManager.startVpn() }
    }

    @Test
    fun `配置就绪时启动 VPN`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.ensureProfile() } returns mockk(relaxed = true)
        val vm = viewModel(hasPlan = true)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        verify { kernelManager.startVpn() }
    }

    @Test
    fun `已连接时点击断开`() = runTest(mainRule.dispatcher) {
        // ready=true：observeKernelState 会经 awaitTunnelReady 门控，
        // 未就绪时会把 isConnected 纠正回 false（假连接防护），断开路径走不到
        val vm = viewModel(hasPlan = true, connected = true, ready = true)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        verify { kernelManager.stopVpn() }
        verify(exactly = 0) { kernelManager.startVpn() }
    }

    @Test
    fun `连接中重复点击被忽略`() = runTest(mainRule.dispatcher) {
        coEvery { kernelConfig.ensureProfile() } returns mockk(relaxed = true)
        val vm = viewModel(hasPlan = true)
        advanceUntilIdle()

        vm.toggleConnection()
        vm.toggleConnection()
        advanceUntilIdle()

        verify(exactly = 1) { kernelManager.startVpn() }
    }

    @Test
    fun `切换代理模式写入状态并通知内核`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setProxyMode("global")
        advanceUntilIdle()

        assertEquals("global", vm.data.value.proxyMode)
        coVerify { kernelProxy.setProxyMode("global") }
    }

    @Test
    fun `内核连接状态同步到首页并清空 DNS 缓存`() = runTest(mainRule.dispatcher) {
        kernelProxy.stubKernelBridge(ready = true)
        coEvery { authRepository.fetchSiteInfo(any()) } returns com.slte.app.domain.model.SiteInfo()
        every { themePreference.mode } returns MutableStateFlow(ThemeMode.SYSTEM)
        val connected = MutableStateFlow(false)
        every { kernelManager.connected } returns connected
        every { kernelManager.profileLoaded } returns MutableStateFlow(0)
        stubAuthSession()
        val vm =
            MainViewModel(
                mainRule.dispatcher,
                kernelManager,
                kernelProxy,
                kernelConfig,
                fallbackDns,
                subscriptionUpdater,
                dataWriter,
                authRepository,
                themePreference,
            )
        advanceUntilIdle()

        connected.value = true
        advanceUntilIdle()

        verify { fallbackDns.clearCache() }
        assertTrue(vm.data.value.isConnected)
    }

    @Test
    fun `内核未就绪时不判定为已连接`() = runTest(mainRule.dispatcher) {
        kernelProxy.stubKernelBridge(ready = false)
        coEvery { authRepository.fetchSiteInfo(any()) } returns com.slte.app.domain.model.SiteInfo()
        every { themePreference.mode } returns MutableStateFlow(ThemeMode.SYSTEM)
        val connected = MutableStateFlow(false)
        every { kernelManager.connected } returns connected
        every { kernelManager.profileLoaded } returns MutableStateFlow(0)
        stubAuthSession()
        val vm =
            MainViewModel(
                mainRule.dispatcher,
                kernelManager,
                kernelProxy,
                kernelConfig,
                fallbackDns,
                subscriptionUpdater,
                dataWriter,
                authRepository,
                themePreference,
            )
        advanceUntilIdle()

        connected.value = true
        advanceUntilIdle()

        assertTrue("内核未就绪时不得显示已连接", !vm.data.value.isConnected)
        assertEquals(R.string.error_vpn_kernel_unavailable, vm.data.value.errorMessageRes)
        verify(exactly = 0) { fallbackDns.clearCache() }
    }
}
