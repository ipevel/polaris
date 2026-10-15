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
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * D2 回归：`awaitTunnelReady` 的 binder 调用必须跑在 ioDispatcher 上。
 *
 * 背景：调用点是 MainViewModel 的 viewModelScope.launch（Main.immediate），
 * 而 `manager.clash()` / `queryTunnelState()` / `queryProxyGroupNames()` 全是 binder
 * 调用。此前它是 KernelProxy 里唯一没切 IO 的探测，界面刚连上时这几个调用直接
 * 压在主线程上（ANR 风险）。
 *
 * 断言方式：把 KernelFaultReporter 注入的 ioDispatcher 换成一个「直通但记账」的
 * dispatcher——safe → guard → withContext(ioDispatcher) 会把协程切到它，块内
 * 标记位为真；binder 桩在标记位为假时计数。回归时（探测不进 safe）dispatches 为 0、
 * 离 IO 的 binder 调用数 > 0，两条断言都会失败。
 *
 * 不用 TestDispatcher 做记账：TestDispatcher 会把块排队到 advance 时才执行，
 * 标记位早已复位，观察不到执行上下文。
 */
class KernelReadinessDispatcherTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val ioDispatcher = RecordingDispatcher()
    private val prefs = InMemoryPreferences()
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<KernelManager>(relaxed = true)
    private val clash = mockk<IClashManager>(relaxed = true)
    private val config = mockk<KernelConfig>(relaxed = true)
    private val store = mockk<SpeedResultStore>(relaxed = true)
    private val geoIp = mockk<GeoIpResolver>(relaxed = true)

    /** 内核「已就绪」：策略组可读，第一轮轮询就返回 true（不走 delay 与超时日志）。 */
    private fun proxy(): KernelProxy {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageName } returns "com.slte.app"
        every { manager.connected } returns MutableStateFlow(true)
        every { manager.clash() } answers {
            ioDispatcher.recordBinderCall()
            clash
        }
        every { clash.queryTunnelState() } answers {
            ioDispatcher.recordBinderCall()
            TunnelState(TunnelState.Mode.Rule)
        }
        every { clash.queryProxyGroupNames(excludeNotSelectable = false) } answers {
            ioDispatcher.recordBinderCall()
            listOf("🚀 节点选择")
        }
        return KernelProxy(KernelFaultReporter(ioDispatcher), manager, config, store, geoIp, context)
    }

    @Test
    fun `就绪探测的 binder 调用全部跑在 ioDispatcher 上`() = runTest(mainRule.dispatcher) {
        val proxy = proxy()

        assertTrue("策略组可读时应就绪", proxy.awaitTunnelReady())

        assertTrue(
            "safe 必须把协程切到注入的 ioDispatcher（实际 dispatch=${ioDispatcher.dispatches}）",
            ioDispatcher.dispatches >= 1,
        )
        assertTrue(
            "应至少观察到一次 binder 调用",
            ioDispatcher.binderCallsOnIo + ioDispatcher.binderCallsOffIo >= 1,
        )
        // 这条是 D2 的核心：回归时探测在主线程上下文执行，离 IO 计数会 > 0。
        assertEquals(
            "binder 调用不得发生在调用方的 Main.immediate 上下文里",
            0,
            ioDispatcher.binderCallsOffIo,
        )
        // 只经 safe 一种方式切：再套一层 withContext(Dispatchers.IO) 会让这里变成 2。
        assertEquals("一次探测只应经 ioDispatcher 一次（不得双重切换）", 1, ioDispatcher.dispatches)
    }
}

/** 直通但记账的 dispatcher：就地执行块，并标记块内是否处于本 dispatcher 的上下文。 */
private class RecordingDispatcher : CoroutineDispatcher() {
    var dispatches = 0
        private set

    var binderCallsOnIo = 0
        private set

    var binderCallsOffIo = 0
        private set

    private var inside = false

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatches++
        inside = true
        try {
            block.run()
        } finally {
            inside = false
        }
    }

    fun recordBinderCall() {
        if (inside) binderCallsOnIo++ else binderCallsOffIo++
    }
}
