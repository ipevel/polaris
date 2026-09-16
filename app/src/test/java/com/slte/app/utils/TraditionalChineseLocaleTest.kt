package com.slte.app.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraditionalChineseLocaleTest {

    @Test
    fun `繁体中文由文字集或地区判定`() {
        assertTrue("zh-TW 视为繁体", isTraditionalChinese(Locale.TRADITIONAL_CHINESE))
        assertTrue("zh-HK 视为繁体", isTraditionalChinese(Locale.forLanguageTag("zh-HK")))
        assertTrue("zh-MO 视为繁体", isTraditionalChinese(Locale.forLanguageTag("zh-MO")))
        assertTrue("Hant 文字集视为繁体", isTraditionalChinese(Locale.forLanguageTag("zh-Hant")))
        assertTrue("Hant 带地区也视为繁体", isTraditionalChinese(Locale.forLanguageTag("zh-Hant-MO")))
    }

    @Test
    fun `简体与其它语言不视为繁体`() {
        assertFalse(isTraditionalChinese(Locale.SIMPLIFIED_CHINESE))
        assertFalse(isTraditionalChinese(Locale.forLanguageTag("zh-Hans-CN")))
        assertFalse(isTraditionalChinese(Locale.forLanguageTag("zh")))
        assertFalse("非中文语种一律不是繁体", isTraditionalChinese(Locale.ENGLISH))
        assertFalse("英文加繁体地区也不是繁体", isTraditionalChinese(Locale.forLanguageTag("en-TW")))
    }

    @Test
    fun `跟随系统时按文字集与地区选择中文变体`() {
        assertEquals(Locale.TRADITIONAL_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh-Hant"), null))
        assertEquals(Locale.TRADITIONAL_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh-MO"), null))
        assertEquals(Locale.TRADITIONAL_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh-TW"), null))
        assertEquals(Locale.TRADITIONAL_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh-Hant-TW"), null))
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveEffectiveLocale(Locale.SIMPLIFIED_CHINESE, null))
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh-Hans-CN"), null))
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveEffectiveLocale(Locale.forLanguageTag("zh"), null))
    }

    @Test
    fun `存储的语言优先于系统语言`() {
        assertEquals(
            Locale.ENGLISH,
            resolveEffectiveLocale(Locale.forLanguageTag("zh-Hant"), Locale.ENGLISH),
        )
        assertEquals(
            Locale.TRADITIONAL_CHINESE,
            resolveEffectiveLocale(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE),
        )
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            resolveEffectiveLocale(Locale.forLanguageTag("zh-HK"), Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun `不支持的系统语言回退英文`() {
        assertEquals(Locale.ENGLISH, resolveEffectiveLocale(Locale.JAPANESE, null))
        assertEquals(Locale.ENGLISH, resolveEffectiveLocale(Locale.KOREAN, null))
        assertEquals(Locale.ENGLISH, resolveEffectiveLocale(Locale.forLanguageTag("th-TH"), null))
        assertEquals(Locale.ENGLISH, resolveEffectiveLocale(Locale.forLanguageTag("en-GB"), null))
    }
}
