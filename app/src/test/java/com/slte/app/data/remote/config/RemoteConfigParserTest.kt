// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import com.slte.app.BuildConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigParserTest {

    // --- validateDto ---

    @Test
    fun `版本号合法字符通过校验`() {
        assertTrue(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "1.0.0")))
        assertTrue(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "2.1-rc1")))
        assertTrue(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "v10.2")))
    }

    @Test
    fun `版本号为 null 时跳过校验`() {
        assertTrue(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = null)))
    }

    @Test
    fun `版本号含非法字符不通过`() {
        assertFalse(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "1.0@bad")))
        assertFalse(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "1.0 test")))
        assertFalse(RemoteConfigParser.validateDto(RemoteConfigDto(configVersion = "v1$0")))
    }

    // --- resolveApiCandidates ---

    @Test
    fun `单个 apiBaseUrl 解析为候选列表`() {
        val dto = RemoteConfigDto(apiBaseUrl = "https://api.example.com")
        assertEquals(
            listOf("https://api.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `apiBaseUrls 数组展开为候选`() {
        val dto = RemoteConfigDto(
            apiBaseUrls = JsonArray(
                listOf(
                    JsonPrimitive("https://a.example.com"),
                    JsonPrimitive("https://b.example.com"),
                ),
            ),
        )
        assertEquals(
            listOf("https://a.example.com", "https://b.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `api 字段为 JsonPrimitive 也能解析`() {
        val dto = RemoteConfigDto(api = JsonPrimitive("https://api2.example.com"))
        assertEquals(
            listOf("https://api2.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `多源候选去重后合并`() {
        val dto = RemoteConfigDto(
            apiBaseUrl = "https://api.example.com",
            apiBaseUrls = JsonArray(
                listOf(
                    JsonPrimitive("https://api.example.com"),
                    JsonPrimitive("https://b.example.com"),
                ),
            ),
            api = JsonPrimitive("https://b.example.com"),
        )
        assertEquals(
            listOf("https://api.example.com", "https://b.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `http 地址被白名单过滤`() {
        val dto = RemoteConfigDto(
            apiBaseUrls = JsonArray(
                listOf(
                    JsonPrimitive("http://insecure.example.com"),
                    JsonPrimitive("https://secure.example.com"),
                ),
            ),
        )
        assertEquals(
            listOf("https://secure.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `非白名单主机被过滤`() {
        val dto = RemoteConfigDto(apiBaseUrl = "https://evil.com")
        assertEquals(
            listOf(BuildConfig.API_BASE_URL),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `Base64 编码的 URL 被解码后纳入候选`() {
        val encoded = java.util.Base64
            .getEncoder()
            .encodeToString("https://api.example.com".toByteArray())
        val dto = RemoteConfigDto(apiBaseUrl = encoded)
        assertEquals(
            listOf("https://api.example.com"),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    @Test
    fun `全部候选被过滤时回落 BuildConfig 默认`() {
        val dto = RemoteConfigDto(apiBaseUrl = "https://evil.com")
        val result = RemoteConfigParser.resolveApiCandidates(dto)
        assertEquals(1, result.size)
        assertEquals(BuildConfig.API_BASE_URL, result.first())
    }

    @Test
    fun `空 DTO 回落 BuildConfig 默认`() {
        val dto = RemoteConfigDto()
        assertEquals(
            listOf(BuildConfig.API_BASE_URL),
            RemoteConfigParser.resolveApiCandidates(dto),
        )
    }

    // --- buildMergedConfig ---

    @Test
    fun `所有字段正确映射到 MergedConfig`() {
        val dto = RemoteConfigDto(
            apiType = "xboard",
            updateVersion = "1.2.3",
            updateChangelogTitle = " New Features ",
            updateChangelog = "Bug fixes and improvements",
            updateForce = true,
            updateApkUrl = "https://update.example.com/app.apk",
            updateApkSha256 = "  ABC123DEF456  ",
        )
        val merged = RemoteConfigParser.buildMergedConfig(
            dto,
            primary = "https://api.example.com",
            candidates = listOf("https://api.example.com"),
        )
        assertEquals("https://api.example.com", merged.apiBaseUrl)
        assertEquals(listOf("https://api.example.com"), merged.apiBaseUrls)
        assertEquals("xboard", merged.apiType)
        assertEquals("1.2.3", merged.updateVersion)
        assertEquals("New Features", merged.updateChangelogTitle)
        assertEquals("Bug fixes and improvements", merged.updateChangelog)
        assertTrue(merged.updateForce)
        assertEquals("https://update.example.com/app.apk", merged.updateApkUrl)
        assertEquals("abc123def456", merged.updateApkSha256)
    }

    @Test
    fun `apiType 默认为空串而非编译期默认`() {
        val dto = RemoteConfigDto(apiType = null)
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals("", merged.apiType)
    }

    @Test
    fun `apiType 空白被 trim 为空串`() {
        val dto = RemoteConfigDto(apiType = "  ")
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals("", merged.apiType)
    }

    @Test
    fun `updateForce 需同时满足标志位和有效 APK 地址`() {
        val dto = RemoteConfigDto(
            updateForce = true,
            updateApkUrl = "https://evil.com/apk",
        )
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertFalse("非白名单 APK 地址应导致 force=false", merged.updateForce)
        assertEquals("", merged.updateApkUrl)
    }

    @Test
    fun `updateForce 为 false 时无论 APK 地址如何均为 false`() {
        val dto = RemoteConfigDto(
            updateForce = false,
            updateApkUrl = "https://update.example.com/app.apk",
        )
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertFalse(merged.updateForce)
    }

    @Test
    fun `updateForce 为 null 时视为 false`() {
        val dto = RemoteConfigDto(
            updateForce = null,
            updateApkUrl = "https://update.example.com/app.apk",
        )
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertFalse(merged.updateForce)
    }

    @Test
    fun `空候选列表回落 BuildConfig 默认`() {
        val dto = RemoteConfigDto()
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals(listOf(BuildConfig.API_BASE_URL), merged.apiBaseUrls)
    }

    @Test
    fun `directDomains 解析与去重`() {
        val dto = RemoteConfigDto(
            directDomains = JsonArray(
                listOf(
                    JsonPrimitive("cdn.example.com"),
                    JsonPrimitive("cdn.example.com"),
                    JsonPrimitive("img.example.com"),
                ),
            ),
        )
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals(listOf("cdn.example.com", "img.example.com"), merged.directDomains)
    }

    @Test
    fun `directDomains 非白名单域名被过滤`() {
        val dto = RemoteConfigDto(
            directDomains = JsonArray(
                listOf(
                    JsonPrimitive("cdn.example.com"),
                    JsonPrimitive("evil.com"),
                ),
            ),
        )
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals(listOf("cdn.example.com"), merged.directDomains)
    }

    @Test
    fun `directDomains 为 JsonPrimitive 也能解析`() {
        val dto = RemoteConfigDto(directDomains = JsonPrimitive("cdn.example.com"))
        val merged = RemoteConfigParser.buildMergedConfig(dto, "https://api.example.com", emptyList())
        assertEquals(listOf("cdn.example.com"), merged.directDomains)
    }
}
