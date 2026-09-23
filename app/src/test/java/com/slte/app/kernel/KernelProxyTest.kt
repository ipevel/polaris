// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

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
import java.io.File
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

        // proxyMode 以内核真实隧道状态为准（override 只是期望值）
        val cases =
            listOf(
                TunnelState.Mode.Global to Constants.PROXY_MODE_GLOBAL,
                TunnelState.Mode.Direct to Constants.PROXY_MODE_DIRECT,
                TunnelState.Mode.Script to Constants.PROXY_MODE_SCRIPT,
                TunnelState.Mode.Rule to Constants.DEFAULT_PROXY_MODE,
            )
        cases.forEach { (mode, expected) ->
            every { clash.queryTunnelState() } returns TunnelState(mode = mode)
            every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(mode)
            assertEquals(expected, proxy.proxyMode())
        }
    }

    @Test
    fun `隧道状态与覆盖不一致时以真实隧道为准`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        // 不一致时不许用 override 掩盖实际生效模式（历史"全局不生效"的遮蔽源）
        every { clash.queryTunnelState() } returns TunnelState(mode = TunnelState.Mode.Rule)
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Global)

        assertEquals(Constants.DEFAULT_PROXY_MODE, proxy.proxyMode())
    }

    @Test
    fun `隧道状态不可读时回落到覆盖模式`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { clash.queryTunnelState() } throws IllegalStateException("tunnel unavailable")
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Global)

        assertEquals(Constants.PROXY_MODE_GLOBAL, proxy.proxyMode())
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
    fun `内核不可用时切换写磁盘并落本地`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { manager.clash() } returns null

        // 准备临时 filesDir 让 writePersistOverrideModeToDisk 可以写入
        val tmpDir = createTempDir("slte_test")
        every { context.filesDir } returns tmpDir

        proxy.setProxyMode(Constants.PROXY_MODE_GLOBAL)
        advanceUntilIdle()

        assertEquals(Constants.PROXY_MODE_GLOBAL, prefs.getString("proxy_mode", null))
        // 验证 override.json 已写入磁盘，包含正确的 mode
        val overrideFile = File(tmpDir, "clash/override.json")
        assertTrue("override.json should exist", overrideFile.exists())
        val content = overrideFile.readText()
        assertTrue("override.json should contain mode=global", content.contains("\"mode\":\"global\""))

        tmpDir.deleteRecursively()
    }

    @Test
    fun `无持久化记录时不同步模式`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()

        proxy.ensurePersistedMode()
        advanceUntilIdle()

        verify(exactly = 0) { clash.patchOverride(any(), any()) }
    }

    @Test
    fun `持久化记录与覆盖不一致时补写`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        prefs.edit().putString("proxy_mode", "全局").commit()
        every { clash.queryTunnelState() } returns TunnelState(mode = TunnelState.Mode.Global)
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Rule)

        proxy.ensurePersistedMode()
        advanceUntilIdle()

        verify { clash.patchOverride(Clash.OverrideSlot.Persist, match { it.mode == TunnelState.Mode.Global }) }
    }

    @Test
    fun `覆盖已正确时不重复补写`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        prefs.edit().putString("proxy_mode", "全局").commit()
        // override 与隧道都已全局：不应再 patchOverride（避免多余重载）
        every { clash.queryTunnelState() } returns TunnelState(mode = TunnelState.Mode.Global)
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(TunnelState.Mode.Global)

        proxy.ensurePersistedMode()
        advanceUntilIdle()

        verify(exactly = 0) { clash.patchOverride(any(), any()) }
    }

    @Test
    fun `Script 模式写入时降级为 Rule`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { clash.queryOverride(Clash.OverrideSlot.Persist) } returns overrideWith(null)

        proxy.setProxyMode(Constants.PROXY_MODE_SCRIPT)
        advanceUntilIdle()

        // Go 内核不支持 script 模式，tunnelModeOf 应降级为 Rule
        verify { clash.patchOverride(Clash.OverrideSlot.Persist, match { it.mode == TunnelState.Mode.Rule }) }
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

    @Test
    fun `累计流量解码内核压缩编码`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        // 高 32 位上传（type=1 → KB 段），低 32 位下载（type=2 → MB 段）
        every { clash.queryTrafficTotal() } returns 0x40000002_80000003L

        assertEquals(2048L to 3_145_728L, proxy.trafficTotal())
    }

    @Test
    fun `秒级 blip 解码与累计同编码`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        // 高 32 位上传（type=0 → 原始字节），低 32 位下载（type=1 → KB 段）
        // returns 也是中缀函数，须加括号避免与 shl/or 抢结合
        every { clash.queryTrafficNow() } returns (1500L shl 32 or 0x40000005L)

        assertEquals(1500L to 5120L, proxy.trafficNow())
    }

    @Test
    fun `内核不可用时流量读取返回空`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()
        every { manager.clash() } returns null

        assertNull(proxy.trafficTotal())
        assertNull(proxy.trafficNow())
    }
}
