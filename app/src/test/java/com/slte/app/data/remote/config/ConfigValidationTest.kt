package com.slte.app.data.remote.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidationTest {

    private val allowed = listOf("slte.me", "example.com")

    @Test
    fun `API 地址白名单与 https 校验`() {
        assertTrue(ConfigValidation.isValidApiUrl("https://app.slte.me", allowed))
        assertTrue(ConfigValidation.isValidApiUrl("https://api.slte.me:8443", allowed))
        assertFalse(ConfigValidation.isValidApiUrl("http://app.slte.me", allowed))
        assertFalse(ConfigValidation.isValidApiUrl("https://evil.com", allowed))
        assertFalse(ConfigValidation.isValidApiUrl("https://slte.me.evil.com", allowed))
        assertFalse(ConfigValidation.isValidApiUrl("not a url", allowed))
        assertFalse(ConfigValidation.isValidApiUrl("", allowed))
    }

    @Test
    fun `直连域名格式与白名单校验`() {
        assertTrue(ConfigValidation.isValidDomain("slte.me", allowed))
        assertTrue(ConfigValidation.isValidDomain("api.slte.me", allowed))
        assertFalse(ConfigValidation.isValidDomain("single", allowed))
        assertFalse(ConfigValidation.isValidDomain("evil.com", allowed))
        assertFalse(ConfigValidation.isValidDomain("", allowed))
        assertFalse(ConfigValidation.isValidDomain("a..b", allowed))
    }

    @Test
    fun `版本数字分段比较`() {
        assertTrue(ConfigValidation.compareVersions("1.10", "1.9") > 0)
        assertTrue(ConfigValidation.compareVersions("2.0", "1.99") > 0)
        assertTrue(ConfigValidation.compareVersions("1.0.1", "1.0.0") > 0)
        assertEquals(0, ConfigValidation.compareVersions("1.0", "1.0.0"))
        assertEquals(0, ConfigValidation.compareVersions("1.2-rc1", "1.2"))
        assertTrue(ConfigValidation.compareVersions("", "1.0") < 0)
        assertTrue(ConfigValidation.compareVersions("abc", "1.0") < 0)
    }

    @Test
    fun `多源择优版本最高且同版本取延迟最小`() {
        val list = listOf(
            FetchedConfig("https://a.example.com", "{}", "1.0", 50),
            FetchedConfig("https://b.example.com", "{}", "2.0", 10),
            FetchedConfig("https://c.example.com", "{}", "1.5", 5)
        )
        assertEquals("https://b.example.com", ConfigValidation.pickBest(list)?.url)

        // 版本相同取延迟最小（最先完成的镜像）
        val sameVersion = listOf(
            FetchedConfig("https://a.example.com", "{}", "1.0", 100),
            FetchedConfig("https://b.example.com", "{}", "1.0", 20)
        )
        assertEquals("https://b.example.com", ConfigValidation.pickBest(sameVersion)?.url)
        assertNull(ConfigValidation.pickBest(emptyList()))
    }

    @Test
    fun `缓存新鲜度判定`() {
        val now = 1_000_000L
        val ttl = 5 * 60_000L
        assertTrue(ConfigValidation.isCacheFresh(now - 60_000, now, ttl))
        assertFalse(ConfigValidation.isCacheFresh(now - 6 * 60_000, now, ttl))
        assertFalse(ConfigValidation.isCacheFresh(0L, now, ttl))
    }
}
