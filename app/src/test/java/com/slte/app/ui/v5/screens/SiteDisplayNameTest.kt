// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶栏站点显示名的回退与清洗。对应用户反馈「首页左上角名称没有引用站点名称」。
 */
class SiteDisplayNameTest {

    private val fallback = "Polaris"

    @Test
    fun `有站点名时用站点名`() {
        assertEquals("星辰机场", siteDisplayName("星辰机场", fallback))
    }

    @Test
    fun `空值一律回退应用名`() {
        assertEquals(fallback, siteDisplayName(null, fallback))
        assertEquals(fallback, siteDisplayName("", fallback))
        assertEquals(fallback, siteDisplayName("   ", fallback))
        assertEquals(fallback, siteDisplayName("\n\t ", fallback))
    }

    @Test
    fun `换行与制表被清除避免撑成两行`() {
        assertEquals("MySite", siteDisplayName("My\nSite", fallback))
        assertEquals("MySite", siteDisplayName("My\tSite", fallback))
        assertEquals("AB", siteDisplayName("A\r\nB", fallback))
    }

    @Test
    fun `双向控制符与零宽字符被清除避免视觉伪装`() {
        // U+202E(RLO) 会把后面的文字视觉反转，可用于品牌伪装
        assertEquals("abc", siteDisplayName("a\u202Eb\u202Cc", fallback))
        assertEquals("ab", siteDisplayName("a\u200Eb", fallback))
        assertEquals("ab", siteDisplayName("a\u200Bb", fallback))
        assertEquals("ab", siteDisplayName("a\uFEFFb", fallback))
        assertEquals("ab", siteDisplayName("a\u2066b\u2069", fallback))
    }

    @Test
    fun `连续空白折叠为单个空格并去首尾`() {
        assertEquals("My Site", siteDisplayName("  My    Site  ", fallback))
    }

    @Test
    fun `超长按码点截断且不切断 emoji 代理对`() {
        val long = "あ".repeat(100)
        val capped = siteDisplayName(long, fallback)
        assertTrue(capped.codePointCount(0, capped.length) <= MAX_SITE_NAME_LENGTH)

        // 每个 emoji 是 2 个 UTF-16 单元：按码点截断必须整颗保留
        val emoji = "🌏".repeat(40)
        val cappedEmoji = siteDisplayName(emoji, fallback)
        assertEquals(MAX_SITE_NAME_LENGTH, cappedEmoji.codePointCount(0, cappedEmoji.length))
        // 不能出现孤立代理项（截断到半个 emoji 的典型症状）：
        // 码点计数 × 2 必须等于 UTF-16 单元数
        assertEquals(cappedEmoji.codePointCount(0, cappedEmoji.length) * 2, cappedEmoji.length)
        assertTrue(cappedEmoji.isNotEmpty())
    }

    @Test
    fun `清洗后为空则回退应用名`() {
        assertEquals(fallback, siteDisplayName("\n\u202E\u200B", fallback))
    }

    @Test
    fun `正常站点名不被改写`() {
        val name = "Polaris 加速器"
        assertEquals(name, siteDisplayName(name, fallback))
        assertFalse(siteDisplayName(name, fallback).contains("\n"))
    }
}
