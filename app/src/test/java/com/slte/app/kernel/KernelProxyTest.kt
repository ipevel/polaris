package com.slte.app.kernel

import android.content.Context
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.support.MainDispatcherRule
import com.slte.app.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KernelProxyTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val prefs = InMemoryPreferences()
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<KernelManager>(relaxed = true)
    private val clash = mockk<IClashManager>(relaxed = true)
    private val config = mockk<KernelConfig>(relaxed = true)
    private val store = mockk<SpeedResultStore>(relaxed = true)
    private val geoIp = mockk<GeoIpResolver>(relaxed = true)
    private val reporter = KernelFaultReporter(mainRule.dispatcher)

    private fun proxy(): KernelProxy {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageName } returns "com.slte.app"
        every { manager.clash() } returns clash
        return KernelProxy(reporter, manager, config, store, geoIp, context)
    }

    private fun overrideWith(mode: TunnelState.Mode?) = ConfigurationOverride(mode = mode)

    @Test
    fun `代理模式映射为界面常量`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()

        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Global)
        assertEquals(Constants.PROXY_MODE_GLOBAL, proxy.proxyMode())

        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Direct)
        assertEquals(Constants.PROXY_MODE_DIRECT, proxy.proxyMode())

        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Script)
        assertEquals(Constants.PROXY_MODE_SCRIPT, proxy.proxyMode())

        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Rule)
        assertEquals(Constants.DEFAULT_PROXY_MODE, proxy.proxyMode())
    }

    @Test
    fun `持久化覆盖为空时回落到隧道状态模式`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(null)
        every { clash.queryTunnelState() } returns TunnelState(mode = TunnelState.Mode.Global)

        assertEquals(Constants.PROXY_MODE_GLOBAL, proxy.proxyMode())
    }

    @Test
    fun `内核不可用时代理模式返回空`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { manager.clash() } returns null

        assertNull(proxy.proxyMode())
    }

    @Test
    fun `内核不可用时切换只落本地不广播`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { manager.clash() } returns null

        proxy.setProxyMode(Constants.PROXY_MODE_DIRECT)
        advanceUntilIdle()

        assertEquals(Constants.PROXY_MODE_DIRECT, prefs.getString("proxy_mode", null))
        verify(exactly = 0) { context.sendBroadcast(any(), any()) }
    }

    @Test
    fun `无持久化记录时不同步模式`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()

        proxy.ensurePersistedMode()
        advanceUntilIdle()

        verify(exactly = 0) { clash.patchOverride(any(), any()) }
    }

    @Test
    fun `读取 TUN 模式优先内核并写入缓存`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { clash.tunStackMode() } returns "gvisor"

        assertEquals("gvisor", proxy.tunStackMode())
        assertEquals("gvisor", prefs.getString("tun_stack", null))
    }

    @Test
    fun `内核不可用时 TUN 模式回退缓存再回退默认`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { manager.clash() } returns null

        assertEquals("system", proxy.tunStackMode())

        prefs.edit().putString("tun_stack", "mixed").commit()
        assertEquals("mixed", proxy.tunStackMode())
    }

    @Test
    fun `设置 TUN 模式把非法值归一化为默认`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { clash.tunStackMode() } returns "gvisor"

        proxy.setTunStack("bogus")
        advanceUntilIdle()

        assertEquals("system", prefs.getString("tun_stack", null))
        verify { clash.setTunStackMode("system") }
    }

    @Test
    fun `内核异常时返回默认值并上报带操作名的故障`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        val fault = async { reporter.faults.first() }
        runCurrent()
        every { manager.clash() } throws IllegalStateException("内核不可用")

        assertNull(proxy.proxyMode())

        assertEquals("proxyMode", fault.await().operation)
        assertTrue(fault.await().cause is IllegalStateException)
    }
}
