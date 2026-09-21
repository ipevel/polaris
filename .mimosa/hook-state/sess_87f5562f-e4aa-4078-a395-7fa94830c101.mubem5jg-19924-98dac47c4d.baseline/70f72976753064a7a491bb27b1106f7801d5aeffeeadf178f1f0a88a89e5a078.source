package com.slte.app.data.remote.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSelectorTest {
    @Test
    fun `主地址健康时粘滞不切换`() {
        val selector = EndpointSelector()
        val picked =
            selector.pickPrimary(
                candidates = listOf("https://a.example.com", "https://b.example.com"),
                probes = mapOf("https://a.example.com" to 100L, "https://b.example.com" to 80L),
                currentPrimary = "https://a.example.com",
            )

        assertEquals("https://a.example.com", picked)
    }

    @Test
    fun `候选明显更快时切换`() {
        val selector = EndpointSelector()
        val picked =
            selector.pickPrimary(
                candidates = listOf("https://a.example.com", "https://b.example.com"),
                probes = mapOf("https://a.example.com" to 100L, "https://b.example.com" to 60L),
                currentPrimary = "https://a.example.com",
            )

        assertEquals("https://b.example.com", picked)
    }

    @Test
    fun `主地址熔断时切换到健康候选`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure("https://a.example.com") }
        val picked =
            selector.pickPrimary(
                candidates = listOf("https://a.example.com", "https://b.example.com"),
                probes = mapOf("https://a.example.com" to 100L, "https://b.example.com" to 90L),
                currentPrimary = "https://a.example.com",
            )
        assertEquals("https://b.example.com", picked)
    }

    @Test
    fun `候选排序将熔断地址排最后`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure("https://c.example.com") }
        val order =
            selector.candidateOrder(
                "https://a.example.com",
                listOf("https://a.example.com", "https://b.example.com", "https://c.example.com"),
            )
        assertEquals("https://a.example.com", order.first())
        assertEquals("https://c.example.com", order.last())
    }

    @Test
    fun `候选入参含主地址时结果不重复且主地址置前`() {
        val selector = EndpointSelector()
        val order =
            selector.candidateOrder(
                "https://a.example.com",
                listOf("https://a.example.com", "https://b.example.com"),
            )
        assertEquals(1, order.count { it == "https://a.example.com" })
        assertEquals("https://a.example.com", order.first())
    }

    @Test
    fun `成功记录复位熔断状态`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure("https://a.example.com") }
        selector.recordSuccess("https://a.example.com", 50L)
        val state =
            selector.state.value.endpoints
                .first { it.url == "https://a.example.com" }
        assertEquals(HealthState.HEALTHY, state.state)
        assertEquals(0, state.consecutiveFailures)
    }

    @Test
    fun `半开候选仅在退避期结束后返回`() {
        val selector = EndpointSelector()
        repeat(3) { selector.recordFailure("https://a.example.com") }

        assertTrue(selector.halfOpenCandidates().isEmpty())

        val later = System.currentTimeMillis() + 60_000
        assertEquals(listOf("https://a.example.com"), selector.halfOpenCandidates(later))
    }
}
