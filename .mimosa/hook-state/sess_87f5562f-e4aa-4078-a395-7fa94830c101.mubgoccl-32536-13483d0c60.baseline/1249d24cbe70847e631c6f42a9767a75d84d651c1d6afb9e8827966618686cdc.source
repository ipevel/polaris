package com.slte.app.data.remote.config

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRaceAggregationTest {
    private val primaryUrl = "https://primary.example.com"
    private val secondaryUrl = "https://secondary.example.com"
    private val spoofedUrl = "https://spoofed.example.com"

    private fun fetched(
        version: String,
        latencyMs: Long,
        notModified: Boolean = false,
    ): FetchedConfig = FetchedConfig(
        url = spoofedUrl,
        raw = "raw-$version",
        version = version,
        latencyMs = latencyMs,
        notModified = notModified,
    )

    @Test
    fun `空地址列表不发起请求且返回空结果`() = runTest {
        val calls = AtomicInteger(0)
        val result =
            ConfigRace.race(emptyList()) {
                calls.incrementAndGet()
                fetched("1.0", 0L)
            }
        assertNull(result.chosen)
        assertEquals(0, calls.get())
    }

    @Test
    fun `每个地址各请求一次且调用次数等于入参数量`() = runTest {
        val calls = AtomicInteger(0)
        val requested = Collections.synchronizedList(mutableListOf<String>())
        ConfigRace.race(listOf(primaryUrl, secondaryUrl, primaryUrl)) { url ->
            calls.incrementAndGet()
            requested.add(url)
            fetched("1.0", 10L)
        }
        assertEquals(3, calls.get())
        assertEquals(listOf(primaryUrl, secondaryUrl, primaryUrl), requested.toList())
    }

    @Test
    fun `胜出地址以请求地址为准而非返回体内的地址`() = runTest {
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                if (url == primaryUrl) fetched("1.0", 10L) else fetched("1.0", 20L)
            }
        assertEquals(primaryUrl, result.chosen?.url)
        assertNotEquals(spoofedUrl, result.chosen?.url)
    }

    @Test
    fun `版本更高者胜出即使延迟更高`() = runTest {
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                if (url == primaryUrl) fetched("1.9", 1L) else fetched("1.10", 900L)
            }
        assertEquals(secondaryUrl, result.chosen?.url)
        assertEquals("1.10", result.chosen?.version)
    }

    @Test
    fun `版本相同时延迟更低者胜出`() = runTest {
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                if (url == primaryUrl) fetched("2.0", 500L) else fetched("2.0", 100L)
            }
        assertEquals(secondaryUrl, result.chosen?.url)
        assertEquals(100L, result.chosen?.latencyMs)
    }

    @Test
    fun `版本与延迟都相同时取入参中靠前者`() = runTest {
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                if (url == primaryUrl) fetched("3.0", 50L) else fetched("3.0", 50L)
            }
        assertEquals(primaryUrl, result.chosen?.url)
    }

    @Test
    fun `部分源失败时忽略失败源且仍然请求全部地址`() = runTest {
        val calls = AtomicInteger(0)
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                calls.incrementAndGet()
                if (url == primaryUrl) fetched("1.0", 10L, notModified = true) else null
            }
        assertEquals(2, calls.get())
        assertEquals(primaryUrl, result.chosen?.url)
        assertEquals("raw-1.0", result.chosen?.raw)
        assertTrue(result.chosen?.notModified == true)
    }

    @Test
    fun `全部源失败时返回空结果且仍请求全部地址`() = runTest {
        val calls = AtomicInteger(0)
        val result =
            ConfigRace.race(listOf(primaryUrl, secondaryUrl)) {
                calls.incrementAndGet()
                null
            }
        assertNull(result.chosen)
        assertEquals(2, calls.get())
    }

    @Test
    fun `单地址成功时返回该地址`() = runTest {
        val result = ConfigRace.race(listOf(primaryUrl)) { fetched("5.0", 12L) }
        assertEquals(primaryUrl, result.chosen?.url)
        assertEquals("raw-5.0", result.chosen?.raw)
    }

    @Test
    fun `任一源抛出异常时整体竞速以该异常失败`() = runTest {
        val failure = IllegalStateException("boom")
        val outcome =
            runCatching {
                ConfigRace.race(listOf(primaryUrl, secondaryUrl)) { url ->
                    if (url == secondaryUrl) throw failure else fetched("1.0", 1L)
                }
            }
        assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        assertEquals("boom", outcome.exceptionOrNull()?.message)
    }
}
