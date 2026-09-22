// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeCountryBoundaryTest {

    @Test
    fun `单字关键词可命中`() {
        assertEquals("US", extractCountryCode("美西节点"))
        assertEquals("GB", extractCountryCode("英伦节点"))
        assertEquals("KR", extractCountryCode("韩服节点"))
        assertEquals("HK", extractCountryCode("港区中转"))
    }

    @Test
    fun `关键词优先于 ISO 分段`() {
        assertEquals("HK", extractCountryCode("US-香港"))
        assertEquals("JP", extractCountryCode("SG-东京"))
        assertEquals("DE", extractCountryCode("US-frankfurt"))
    }

    @Test
    fun `多国关键词按关键词表声明顺序命中`() {
        assertEquals("HK", extractCountryCode("新加坡-香港"))
        assertEquals("HK", extractCountryCode("香港-新加坡"))
        assertEquals("SG", extractCountryCode("日本-新加坡"))
        assertEquals("DE", extractCountryCode("德国-法国"))
    }

    @Test
    fun `大小写与空白归一后再做分段匹配`() {
        assertEquals("US", extractCountryCode("  us  "))
        assertEquals("HK", extractCountryCode("hk-01"))
        assertEquals("JP", extractCountryCode("jp"))
    }

    @Test
    fun `长度不为二的代码不被当作 ISO 分段`() {
        assertEquals("XX", extractCountryCode("USA"))
        assertEquals("XX", extractCountryCode("CHN"))
        assertEquals("XX", extractCountryCode("节点A"))
    }

    @Test
    fun `分段中的非 ISO 两字母会被跳过`() {
        assertEquals("US", extractCountryCode("ZZ-US"))
        assertEquals("SG", extractCountryCode("SG-01-KR"))
        assertEquals("KR", extractCountryCode("01-KR"))
    }

    @Test
    fun `无任何线索时回落到未知代码`() {
        assertEquals("XX", extractCountryCode(""))
        assertEquals("XX", extractCountryCode(" "))
        assertEquals("XX", extractCountryCode("节点A 01"))
        assertEquals("XX", extractCountryCode("----"))
    }
}
