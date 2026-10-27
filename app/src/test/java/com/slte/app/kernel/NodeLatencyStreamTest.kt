package com.slte.app.kernel

import android.content.Context
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.kernel.api.KernelApi
import com.slte.app.kernel.api.KernelClash
import com.slte.app.kernel.api.KernelProxyGroupSnapshot
import com.slte.app.kernel.api.KernelProxySnapshot
import com.slte.app.kernel.api.KernelUrlTestResult
import com.slte.app.support.MainDispatcherRule
import com.slte.app.utils.Constants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 测速流约定：点测速必须产生新的实测，不能复用内核缓存的组延迟历史。
 *
 * 每节点独立 urlTest，谁先返回先推谁；失败归一成 [Constants.DELAY_TIMEOUT]，每批落盘。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeLatencyStreamTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val prefs = InMemoryPreferences()
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<KernelApi>(relaxed = true)
    private val clash = mockk<KernelClash>(relaxed = true)
    private val config = mockk<KernelConfig>(relaxed = true)
    private val store = mockk<SpeedResultStore>(relaxed = true)
    private val geoIp = mockk<GeoIpResolver>(relaxed = true)
    private val reporter = KernelFaultReporter(mainRule.dispatcher)

    private val group = "节点选择"
    private val saved = slot<Map<String, Int>>()

    private fun kernelProxy(): KernelProxy {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageName } returns "com.slte.app"
        coEvery { manager.awaitClash() } returns clash
        every { clash.queryProxyGroupNames(any()) } returns listOf(group)
        every { clash.queryProxyGroup(group, any()) } answers {
            KernelProxyGroupSnapshot(
                type = "Selector",
                proxies =
                listOf(
                    KernelProxySnapshot(NODE_HK, isGroup = false, delay = 0, measured = false),
                    KernelProxySnapshot(NODE_JP, isGroup = false, delay = 0, measured = false),
                ),
                now = NODE_HK,
            )
        }
        every { store.saveSpeedResults(capture(saved)) } returns Unit
        return KernelProxy(reporter, manager, config, store, geoIp, context)
    }

    private fun TestScope.collectUpdates(
        kernel: KernelProxy,
        into: MutableList<NodeLatencyUpdate>,
    ) = launch {
        kernel.nodeLatencyStream(maxWaitMs = MAX_WAIT_MS).toList(into)
    }

    @Test
    fun `测速对每个节点发起真实 urlTest 而不是读缓存`() = runTest(mainRule.dispatcher) {
        every { clash.urlTest(NODE_HK, any()) } returns KernelUrlTestResult(80)
        every { clash.urlTest(NODE_JP, any()) } returns KernelUrlTestResult(120)

        val updates = mutableListOf<NodeLatencyUpdate>()
        val job = collectUpdates(kernelProxy(), updates)
        advanceUntilIdle()
        job.join()

        assertTrue("必须真的调用内核测速", updates.any { it.fresh.containsKey(NODE_HK) })
        assertEquals("收尾带上全部实测结果", mapOf(NODE_HK to 80, NODE_JP to 120), updates.last().all)
        assertTrue("每批结果都要落盘", saved.captured.containsKey(NODE_HK))
    }

    @Test
    fun `测速失败节点记为超时且不影响其他节点`() = runTest(mainRule.dispatcher) {
        every { clash.urlTest(NODE_HK, any()) } returns KernelUrlTestResult(0)
        every { clash.urlTest(NODE_JP, any()) } returns KernelUrlTestResult(120)

        val updates = mutableListOf<NodeLatencyUpdate>()
        val job = collectUpdates(kernelProxy(), updates)
        advanceUntilIdle()
        job.join()

        assertEquals("失败节点归一成超时", Constants.DELAY_TIMEOUT, updates.last().all[NODE_HK])
        assertEquals("存活节点不受影响", 120, updates.last().all[NODE_JP])
    }

    @Test
    fun `全部失败也正常收尾`() = runTest(mainRule.dispatcher) {
        every { clash.urlTest(any(), any()) } returns KernelUrlTestResult(0)

        val updates = mutableListOf<NodeLatencyUpdate>()
        val job = collectUpdates(kernelProxy(), updates)
        advanceUntilIdle()
        job.join()

        assertTrue("必须有收尾事件", updates.last().finished)
        assertEquals(Constants.DELAY_TIMEOUT, updates.last().all[NODE_HK])
        assertEquals(Constants.DELAY_TIMEOUT, updates.last().all[NODE_JP])
    }

    @Test
    fun `内核不可用时立即收尾`() = runTest(mainRule.dispatcher) {
        val kernel = kernelProxy()
        coEvery { manager.awaitClash() } returns null

        val updates = mutableListOf<NodeLatencyUpdate>()
        val job = collectUpdates(kernel, updates)
        advanceUntilIdle()
        job.join()

        assertEquals(1, updates.size)
        assertTrue(updates.single().finished)
        assertTrue(updates.single().all.isEmpty())
    }

    private companion object {
        const val NODE_HK = "[vless]香港01"
        const val NODE_JP = "[ss]日本01"
        const val MAX_WAIT_MS = 5_000L
    }
}
