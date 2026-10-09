// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.adapter

import com.slte.app.data.remote.adapter.xboard.XboardSiteConfig
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bSiteConfig
import com.slte.app.domain.model.EmailWhitelist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 邮箱后缀白名单解析：各面板给法不统一（数组 / 单串 / 分隔串 / 0 / null），
 * 这里锁住统一后的规范化结果，并确认两套后端适配器拿到同一份 payload 时结果一致。
 */
class EmailWhitelistParserTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun `数组形式的邮箱后缀白名单解析成规范化列表`() {
        val element = json.parseToJsonElement("""["Gmail.com", "QQ.com"]""")

        assertEquals(listOf("gmail.com", "qq.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `分隔串形式的邮箱后缀白名单统一分隔符并去重`() {
        val element = JsonPrimitive(" Gmail.com, *.163.com ;outlook.com ")

        assertEquals(listOf("gmail.com", "163.com", "outlook.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `单个字符串形式的邮箱后缀白名单`() {
        val element = JsonPrimitive("GMAIL.com")

        assertEquals(listOf("gmail.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `未启用白名单时 0 与 null 都解析成空列表`() {
        assertEquals(emptyList<String>(), parseEmailWhitelistSuffixes(JsonPrimitive(0)))
        assertEquals(emptyList<String>(), parseEmailWhitelistSuffixes(JsonNull))
        assertEquals(emptyList<String>(), parseEmailWhitelistSuffixes(null))
    }

    @Test
    fun `带 @ 前缀与点号前缀的后缀被规范化`() {
        val element = JsonPrimitive("@gmail.com\n.qq.com")

        assertEquals(listOf("gmail.com", "qq.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `不含点号的非法后缀被过滤`() {
        val element = JsonPrimitive("gmail.com, localhost, ")

        assertEquals(listOf("gmail.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `重复后缀只保留一个`() {
        val element = JsonPrimitive("gmail.com, GMAIL.com, gmail.com")

        assertEquals(listOf("gmail.com"), parseEmailWhitelistSuffixes(element))
    }

    @Test
    fun `空列表表示未启用白名单`() {
        val whitelist = EmailWhitelist.None

        assertFalse(whitelist.isEnabled)
        assertNull(whitelist.defaultSuffix)
    }

    @Test
    fun `有后缀时白名单启用且默认取第一个`() {
        val whitelist = EmailWhitelist(listOf("gmail.com", "qq.com"))

        assertTrue(whitelist.isEnabled)
        assertEquals("gmail.com", whitelist.defaultSuffix)
    }

    @Test
    fun `两套适配器解析同一份 payload 得到相同的白名单`() {
        val payload = """{"email_whitelist_suffix": ["Gmail.com", "163.com"]}"""

        val xiaoV2b = json.decodeFromString<XiaoV2bSiteConfig>(payload)
        val xboard = json.decodeFromString<XboardSiteConfig>(payload)

        val xiaoV2bSuffixes = parseEmailWhitelistSuffixes(xiaoV2b.emailWhitelistSuffix)
        val xboardSuffixes = parseEmailWhitelistSuffixes(xboard.emailWhitelistSuffix)

        assertEquals(xiaoV2bSuffixes, xboardSuffixes)
        assertEquals(listOf("gmail.com", "163.com"), xiaoV2bSuffixes)
    }

    @Test
    fun `两套适配器在字段缺失时都按未启用处理`() {
        val payload = """{"is_email_verify": 1}"""

        val xiaoV2b = json.decodeFromString<XiaoV2bSiteConfig>(payload)
        val xboard = json.decodeFromString<XboardSiteConfig>(payload)

        val expected = EmailWhitelist.None
        assertEquals(expected, EmailWhitelist(parseEmailWhitelistSuffixes(xiaoV2b.emailWhitelistSuffix)))
        assertEquals(expected, EmailWhitelist(parseEmailWhitelistSuffixes(xboard.emailWhitelistSuffix)))
    }
}
