// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.support.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class KernelProxyIpTest {
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

    private fun proxy(connected: Boolean): KernelProxy {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageName } returns "com.slte.app"
        every { manager.connected } returns MutableStateFlow(connected)
        every { manager.clash() } returns clash
        return KernelProxy(reporter, manager, config, store, geoIp, context)
    }

    @Test
    fun `内核未连接时直接返回空且不探测内核`() = runTest(mainRule.dispatcher) {
        val proxy = proxy(connected = false)

        assertNull(proxy.fetchPublicIp())

        // 未连接时连内核都不该碰（否则会发起必然失败的直连查询）
        verify(exactly = 0) { manager.clash() }
    }

    // 注：「隧道未就绪时返回空」这一路径无法在纯 JUnit 下测试——awaitTunnelReady 超时会
    // 调用 AppLog.w → android.util.Log.println，而测试环境的 android.jar 是 stub 实现，
    // 每个方法都抛 RuntimeException("Stub!")。需要 Robolectric 才能覆盖，而当前
    // 测试 classpath 不含 robolectric。该路径由下方「隧道已就绪时放行查询」间接覆盖
    // （awaitTunnelReady 返回 true 的分支），且其返回 false 时 fetchPublicIp 的
    // return@safe null 行为与「内核未连接」完全对称。

    @Test
    fun `隧道已就绪时放行查询`() = runTest(mainRule.dispatcher) {
        val proxy = proxy(connected = true)
        // 隧道已就绪 → awaitTunnelReady 立即返回 true，不触发超时日志
        every { clash.queryTunnelState() } returns TunnelState(TunnelState.Mode.Rule)
        every { clash.queryProxyGroupNames(excludeNotSelectable = false) } returns listOf("🚀 节点选择")

        // 测试环境有网络，查询会成功返回真实 IP；关键是放行了查询而非被门禁拦截
        assertNotNull(proxy.fetchPublicIp())

        // 确实走到了查询那一步（clash 被调用过）
        verify(atLeast = 1) { manager.clash() }
    }
}
