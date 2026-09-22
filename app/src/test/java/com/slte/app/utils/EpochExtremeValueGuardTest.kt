// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpochExtremeValueGuardTest {

    @Test
    fun `非法纪元秒不格式化且不抛异常`() {
        assertEquals("", FormatUtils.formatDate(0L))
        assertEquals("", FormatUtils.formatDate(-1L))
        assertEquals("", FormatUtils.formatDate(Long.MIN_VALUE))
        assertEquals("", FormatUtils.formatDate(Long.MAX_VALUE))
        assertEquals("", FormatUtils.formatExpiryDate(0L))
        assertEquals("", FormatUtils.formatExpiryDate(-1L))
        assertEquals("", FormatUtils.formatExpiryDate(Long.MIN_VALUE))
        assertEquals("", FormatUtils.formatExpiryDate(Long.MAX_VALUE))
    }

    @Test
    fun `超出可表示上限的纪元秒不格式化`() {
        assertEquals("", FormatUtils.formatDate(253_402_300_800L))
        assertEquals("", FormatUtils.formatExpiryDate(253_402_300_800L))
        assertEquals("", FormatUtils.formatDate(31_556_889_864_403_199L))
    }

    @Test
    fun `常规纪元秒正常格式化`() {
        assertTrue(FormatUtils.formatDate(1_700_000_000L).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(FormatUtils.formatExpiryDate(1_700_000_000L).matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}")))
        assertTrue(FormatUtils.formatDate(253_402_300_799L).isNotEmpty())
    }
}
