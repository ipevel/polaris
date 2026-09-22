// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSelectorInvariantsTest {
    private val a = "https://a.example.com"
    private val b = "https://b.example.com"
    private val c = "https://c.example.com"

    @Test
    fun `初始状态为主地址为空且端点快照为空`() {
        val selector = EndpointSelector()
        val state = selector.state.value
        assertNull(state.primary)
        assertTrue(state.endpoints.isEmpty())
    }

    @Test
    fun `空候选集永远返回空主地址`() {
        val selector = EndpointSelector()
        assertNull(selector.pickPrimary(emptyList(), emptyMap(), currentPrimary = null))
        assertNull(selector.pickPrimary(emptyList(), mapOf(a to 1L), currentPrimary = a))
        assertNull(selector.pickPrimary(emptyList(), emptyMap(), currentPrimary = a))
    }

    @Test
    fun `单候选集始终返回该候选`() {
        val selector = EndpointSelector()
        assertEquals(a, selector.pickPrimary(listOf(a), emptyMap(), currentPrimary = null))
        assertEquals(a, selector.pickPrimary(listOf(a), emptyMap(), currentPrimary = a))
        assertEquals(a, selector.pickPrimary(listOf(a), mapOf(a to 900L), currentPrimary = null))
        assertEquals(a, selector.pickPrimary(listOf(a), mapOf(b to 1L), currentPrimary = b))
    }

    @Test
    fun `当前主地址缺少探针时让位给已探测候选`() {
        val selector = EndpointSelector()
        val picked = selector.pickPrimary(listOf(a, b), mapOf(b to 400L), currentPrimary = a)
        assertEquals(b, picked)
    }

    @Test
    fun `候选均无探针时保留当前主地址或退回首候选`() {
        val selector = EndpointSelector()
        assertEquals(b, selector.pickPrimary(listOf(a, b), emptyMap(), currentPrimary = b))
        assertEquals(a, selector.pickPrimary(listOf(a, b), emptyMap(), currentPrimary = "https://z.example.com"))
    }

    @Test
    fun `当前主地址不在候选集中时选择探针延迟最低者`() {
        val selector = EndpointSelector()
        val picked =
            selector.pickPrimary(
                candidates = listOf(a, b, c),
                probes = mapOf(a to 300L, c to 100L),
                currentPrimary = "https://z.example.com",
            )
        assertEquals(c, picked)
    }

    @Test
    fun `候选恰好快百分之三十时仍保持粘滞`() {
        val selector = EndpointSelector()
        val picked =
            selector.pickPrimary(
                candidates = listOf(a, b),
                probes = mapOf(a to 100L, b to 70L),
                currentPrimary = a,
            )
        assertEquals(a, picked)
    }

    @Test
    fun `候选快于百分之三十阈值时切换`() {
        val selector = EndpointSelector()
        val picked =
            selector.pickPrimary(
                candidates = listOf(a, b),
                probes = mapOf(a to 100L, b to 69L),
                currentPrimary = a,
            )
        assertEquals(b, picked)
    }

    @Test
    fun `主地址处于熔断期时改用探针中最快的候选`() {
        val selector = EndpointSelector()
        val now = System.currentTimeMillis()
        repeat(3) { selector.recordFailure(a) }
        assertTrue(selector.isOpen(a, now))
        val picked =
            selector.pickPrimary(
                candidates = listOf(a, b, c),
                probes = mapOf(b to 120L, c to 60L),
                currentPrimary = a,
                now = now,
            )
        assertEquals(c, picked)
    }

    @Test
    fun `退避结束后按粘滞逻辑决定是否切换`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure(a) }
        val halfOpenNow = Long.MAX_VALUE

        val sticky =
            selector.pickPrimary(
                candidates = listOf(a, b),
                probes = mapOf(a to 100L, b to 200L),
                currentPrimary = a,
                now = halfOpenNow,
            )
        assertEquals(a, sticky)

        val switched =
            selector.pickPrimary(
                candidates = listOf(a, b),
                probes = mapOf(a to 200L, b to 100L),
                currentPrimary = a,
                now = halfOpenNow,
            )
        assertEquals(b, switched)
    }

    @Test
    fun `失败累计到阈值前保持降级状态`() {
        val selector = EndpointSelector()
        selector.recordFailure(a)
        val first = selector.state.value.endpoints.first { it.url == a }
        assertEquals(HealthState.DEGRADED, first.state)
        assertEquals(1, first.consecutiveFailures)

        selector.recordFailure(a)
        val second = selector.state.value.endpoints.first { it.url == a }
        assertEquals(HealthState.DEGRADED, second.state)
        assertEquals(2, second.consecutiveFailures)
    }

    @Test
    fun `达到失败阈值后进入熔断且退避结束前维持熔断判定`() {
        val selector = EndpointSelector()
        val now = System.currentTimeMillis()
        repeat(3) { selector.recordFailure(a) }

        val snapshot = selector.state.value.endpoints.first { it.url == a }
        assertEquals(HealthState.OPEN, snapshot.state)
        assertEquals(3, snapshot.consecutiveFailures)
        assertTrue(selector.isOpen(a, now))
        assertFalse(selector.isOpen(a, Long.MAX_VALUE))
    }

    @Test
    fun `未记录健康信息的地址不算熔断且不产生半开候选`() {
        val selector = EndpointSelector()
        assertFalse(selector.isOpen(a))
        assertTrue(selector.halfOpenCandidates().isEmpty())
    }

    @Test
    fun `成功与探测记录都清零失败计数并刷新延迟`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure(a) }

        selector.recordProbe(a, 42L)
        val probed = selector.state.value.endpoints.first { it.url == a }
        assertEquals(HealthState.HEALTHY, probed.state)
        assertEquals(0, probed.consecutiveFailures)
        assertEquals(42L, probed.lastLatencyMs)

        selector.recordFailure(a)
        selector.recordSuccess(a, 7L)
        val succeeded = selector.state.value.endpoints.first { it.url == a }
        assertEquals(HealthState.HEALTHY, succeeded.state)
        assertEquals(0, succeeded.consecutiveFailures)
        assertEquals(7L, succeeded.lastLatencyMs)
    }

    @Test
    fun `端点快照按地址字典序排列且同一地址只出现一次`() {
        val selector = EndpointSelector()
        selector.recordSuccess(c, 10L)
        selector.recordSuccess(a, 20L)
        selector.recordSuccess(b, 30L)
        selector.recordSuccess(c, 40L)

        val endpoints = selector.state.value.endpoints
        assertEquals(listOf(a, b, c), endpoints.map { it.url })
        assertEquals(40L, endpoints.first { it.url == c }.lastLatencyMs)
    }

    @Test
    fun `健康记录不改写主地址字段`() {
        val selector = EndpointSelector()
        selector.updatePrimary(a)
        selector.recordFailure(b)
        assertEquals(a, selector.state.value.primary)

        selector.updatePrimary(null)
        assertNull(selector.state.value.primary)
    }

    @Test
    fun `半开候选只含达到阈值的地址并按字典序排列`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure(b) }
        repeat(3) { selector.recordFailure(a) }
        selector.recordFailure(c)

        assertEquals(listOf(a, b), selector.halfOpenCandidates(Long.MAX_VALUE))
        assertTrue(selector.halfOpenCandidates(0L).isEmpty())
    }

    @Test
    fun `候选排序把降级地址排健康地址之后并把熔断地址排最后`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure(c) }
        selector.recordFailure(b)

        assertEquals(listOf(a, b, c), selector.candidateOrder(a, listOf(a, b, c)))
    }

    @Test
    fun `候选健康信息缺失时保持入参相对顺序且主地址置首`() {
        val selector = EndpointSelector()
        val order = selector.candidateOrder("https://p.example.com", listOf(b, a))
        assertEquals(listOf("https://p.example.com", b, a), order)
        assertEquals(listOf(b, a), selector.candidateOrder(b, listOf(b, a)))
    }
}
