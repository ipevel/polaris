package com.slte.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeCountryTraditionalAliasTest {

    @Test
    fun `繁體國家名可解析`() {
        assertEquals("TW", extractCountryCode("台灣 01"))
        assertEquals("US", extractCountryCode("美國 02"))
        assertEquals("GB", extractCountryCode("英國 03"))
        assertEquals("DE", extractCountryCode("德國 04"))
        assertEquals("KR", extractCountryCode("韓國 05"))
        assertEquals("JP", extractCountryCode("東京 06"))
        assertEquals("SG", extractCountryCode("獅城 07"))
        assertEquals("MY", extractCountryCode("馬來西亞 08"))
        assertEquals("PH", extractCountryCode("菲律賓 09"))
        assertEquals("TH", extractCountryCode("泰國 10"))
        assertEquals("VN", extractCountryCode("越南 11"))
        assertEquals("ID", extractCountryCode("雅加達 12"))
        assertEquals("MO", extractCountryCode("澳門 13"))
        assertEquals("AU", extractCountryCode("澳大利亞 14"))
        assertEquals("RU", extractCountryCode("俄羅斯 15"))
        assertEquals("UA", extractCountryCode("烏克蘭 16"))
        assertEquals("NL", extractCountryCode("荷蘭 17"))
        assertEquals("FI", extractCountryCode("芬蘭 18"))
        assertEquals("PL", extractCountryCode("波蘭 19"))
        assertEquals("AT", extractCountryCode("奧地利 20"))
    }

    @Test
    fun `繁體城市名可解析`() {
        assertEquals("US", extractCountryCode("洛杉磯"))
        assertEquals("US", extractCountryCode("矽谷"))
        assertEquals("US", extractCountryCode("聖何塞"))
        assertEquals("US", extractCountryCode("西雅圖"))
        assertEquals("US", extractCountryCode("紐約"))
        assertEquals("GB", extractCountryCode("倫敦"))
        assertEquals("DE", extractCountryCode("法蘭克福"))
        assertEquals("KR", extractCountryCode("首爾"))
        assertEquals("IN", extractCountryCode("孟買"))
        assertEquals("BR", extractCountryCode("聖保羅"))
        assertEquals("FR", extractCountryCode("法國"))
        assertEquals("IT", extractCountryCode("米蘭"))
        assertEquals("ES", extractCountryCode("馬德里"))
        assertEquals("CH", extractCountryCode("蘇黎世"))
        assertEquals("SE", extractCountryCode("斯德哥爾摩"))
        assertEquals("NO", extractCountryCode("挪威"))
        assertEquals("IE", extractCountryCode("愛爾蘭"))
        assertEquals("KZ", extractCountryCode("哈薩克"))
        assertEquals("AE", extractCountryCode("阿聯酋"))
    }

    @Test
    fun `繁體單字與土耳其兩種寫法可解析`() {
        assertEquals("KR", extractCountryCode("韓服節點"))
        assertEquals("TR", extractCountryCode("伊斯坦堡"))
        assertEquals("TR", extractCountryCode("伊斯坦布爾"))
    }

    @Test
    fun `繁體別名不改變简体既有解析`() {
        assertEquals("TW", extractCountryCode("台湾 01"))
        assertEquals("US", extractCountryCode("美国 02"))
        assertEquals("GB", extractCountryCode("英国 03"))
        assertEquals("HK", extractCountryCode("新加坡-香港"))
        assertEquals("US", extractCountryCode("美西节点"))
    }

    @Test
    fun `繁體與简体混寫仍可解析`() {
        assertEquals("TW", extractCountryCode("台灣-TW-01"))
        assertEquals("JP", extractCountryCode("東京-JP-01"))
        assertEquals("US", extractCountryCode("美國-US-01"))
    }

    @Test
    fun `無法辨識時仍回退為 XX`() {
        assertEquals("XX", extractCountryCode("未知地區 01"))
        assertEquals("XX", extractCountryCode("中轉"))
    }
}
