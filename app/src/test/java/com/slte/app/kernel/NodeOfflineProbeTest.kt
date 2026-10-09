// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import android.content.Context
import com.github.kr328.clash.core.model.UrlTestResult
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.support.MainDispatcherRule
import com.slte.app.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 节点离线标识的判定缝（2026-10-09 跟进上游 shgnx/slte）。
 *
 * 这一层的全部价值在于**不误伤**：延迟超时只说明这一次没通，可能是抖动、
 * 也可能是节点真下线了。只有内核明确回 `offline`（DNS 解析不了 / 连接被拒 /
 * 没有路由）才允许打离线角标；内核没跑、查询抛异常、出现没见过的 kind，
 * 一律返回 null 当作"不知道"，宁可少标也不能把活节点标成死的。
 */
class NodeOfflineProbeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val prefs = InMemoryPreferences()
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<KernelManager>(relaxed = true)
    private val clash = mockk<IClashManager>(relaxed = true)
    private val config = mockk<KernelConfig>(relaxed = true)
    private val store = mockk<SpeedResultStore>(relaxed = true)
    private val geoIp = mockk<GeoIpResolver>(relaxed = true)

    private fun proxy(): KernelProxy {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.packageName } returns "com.slte.app"
        every { manager.clash() } returns clash
        return KernelProxy(KernelFaultReporter(mainRule.dispatcher), manager, config, store, geoIp, context)
    }

    @Test
    fun `内核判定不可达时归类为离线`() {
        val result = UrlTestResult(delay = 0, kind = UrlTestResult.KIND_OFFLINE)

        assertEquals(KernelUrlTestFailure.OFFLINE, urlTestFailureOf(result))
    }

    @Test
    fun `内核判定超时时归类为超时`() {
        val result = UrlTestResult(delay = 0, kind = UrlTestResult.KIND_TIMEOUT)

        assertEquals(KernelUrlTestFailure.TIMEOUT, urlTestFailureOf(result))
    }

    @Test
    fun `测速成功不产生失败分类`() {
        val result = UrlTestResult(delay = 123, kind = UrlTestResult.KIND_ALIVE)

        assertNull(urlTestFailureOf(result))
    }

    @Test
    fun `没见过的分类不当作离线`() {
        // 内核将来新增分类（或字段名变更）时，旧 App 必须安静地"不知道"，
        // 而不是把整个节点页刷成离线。
        val result = UrlTestResult(delay = 0, kind = "something-new")

        assertNull(urlTestFailureOf(result))
    }

    @Test
    fun `内核说不可达时返回离线`() = runTest(mainRule.dispatcher) {
        every { clash.urlTest("香港01", Constants.NODE_URLTEST_TIMEOUT_MS) } returns
            UrlTestResult(delay = 0, kind = UrlTestResult.KIND_OFFLINE)

        val kind = proxy().urlTestFailureKind("香港01", Constants.NODE_URLTEST_TIMEOUT_MS)

        assertEquals(KernelUrlTestFailure.OFFLINE, kind)
    }

    @Test
    fun `内核未就绪时不判离线`() = runTest(mainRule.dispatcher) {
        every { manager.clash() } returns null

        assertNull(proxy().urlTestFailureKind("香港01", Constants.NODE_URLTEST_TIMEOUT_MS))
    }

    @Test
    fun `内核查询抛异常时不判离线`() = runTest(mainRule.dispatcher) {
        every { clash.urlTest(any(), any()) } throws IllegalStateException("boom")

        assertNull(proxy().urlTestFailureKind("香港01", Constants.NODE_URLTEST_TIMEOUT_MS))
    }

    @Test
    fun `离线名单落盘后能读回`() {
        val proxy = proxy()
        every { store.getOfflineNodes() } returns setOf("香港01")

        proxy.saveOfflineNodes(setOf("香港01"))

        verify { store.saveOfflineNodes(setOf("香港01")) }
        assertEquals(setOf("香港01"), proxy.cachedOfflineNodes())
    }
}
