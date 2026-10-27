package com.slte.app.data.remote.config

import com.slte.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigMergeTest {
    private fun mergedWebsite(websiteUrl: String?): String = RemoteConfigParser.buildMergedConfig(
        dto = RemoteConfigDto(websiteUrl = websiteUrl),
        primary = BuildConfig.API_BASE_URL,
        candidates = listOf(BuildConfig.API_BASE_URL),
    ).websiteUrl

    @Test
    fun `远程配置的官网地址优先生效且独立于 API 白名单`() {
        assertEquals("https://www.example.com", mergedWebsite("https://www.example.com"))
        // 官网是独立配置项：非白名单域名同样生效
        assertEquals("https://official-site.example.org", mergedWebsite("https://official-site.example.org"))
    }

    @Test
    fun `远程缺失或空白时回退编译期值`() {
        assertEquals(BuildConfig.WEBSITE_URL, mergedWebsite(null))
        assertEquals(BuildConfig.WEBSITE_URL, mergedWebsite("   "))
    }

    @Test
    fun `非 https 被拒后回退编译期值`() {
        assertEquals(BuildConfig.WEBSITE_URL, mergedWebsite("http://www.example.com"))
        assertEquals(BuildConfig.WEBSITE_URL, mergedWebsite("ftp://www.example.com"))
    }
}
