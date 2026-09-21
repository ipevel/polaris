package com.slte.app.utils

import android.content.Context
import com.slte.app.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormatUtilsTest {

    private lateinit var defaultLocale: Locale
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun `金额分转元固定两位小数`() {
        assertEquals("0.00", FormatUtils.balance(0))
        assertEquals("0.01", FormatUtils.balance(1))
        assertEquals("1.00", FormatUtils.balance(100))
        assertEquals("123.45", FormatUtils.balance(12345))
    }

    @Test
    fun `金额负值与极大值不丢失符号与量级`() {
        assertEquals("-0.01", FormatUtils.balance(-1))
        assertEquals("-123.45", FormatUtils.balance(-12345))
        assertEquals("21474836.47", FormatUtils.balance(Int.MAX_VALUE))
        assertEquals("-21474836.48", FormatUtils.balance(Int.MIN_VALUE))
    }

    @Test
    fun `流量为零或负数统一为零B`() {
        assertEquals("0B", FormatUtils.traffic(0L))
        assertEquals("0B", FormatUtils.traffic(-1L))
        assertEquals("0B", FormatUtils.traffic(Long.MIN_VALUE))
    }

    @Test
    fun `流量按B KB MB GB TB逐级进位`() {
        assertEquals("1B", FormatUtils.traffic(1L))
        assertEquals("1023B", FormatUtils.traffic(1023L))
        assertEquals("1KB", FormatUtils.traffic(1024L))
        assertEquals("1MB", FormatUtils.traffic(1024L * 1024))
        assertEquals("1GB", FormatUtils.traffic(1024L * 1024 * 1024))
        assertEquals("1TB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `流量去除多余尾随零与小数点`() {
        assertEquals("1.5KB", FormatUtils.traffic(1536L))
        assertEquals("2MB", FormatUtils.traffic(2L * 1024 * 1024))
        assertEquals("1.5GB", FormatUtils.traffic(1024L * 1024 * 1024 * 3 / 2))
        assertEquals("2.25TB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024 * 9 / 4))
    }

    @Test
    fun `流量单位边界取值不溢出`() {
        assertEquals("1024KB", FormatUtils.traffic(1024L * 1024 - 1))
        assertEquals("1024MB", FormatUtils.traffic(1024L * 1024 * 1024 - 1))
        assertEquals("1024GB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024 - 1))
        assertEquals("1024TB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024 * 1024))
        assertEquals("8388608TB", FormatUtils.traffic(Long.MAX_VALUE))
    }

    @Test
    fun `非IPv6与短地址保持原样`() {
        assertEquals("1.2.3.4", FormatUtils.compactIp("1.2.3.4"))
        assertEquals("::1", FormatUtils.compactIp("::1"))
        assertEquals("", FormatUtils.compactIp(""))
        assertEquals("a".repeat(40), FormatUtils.compactIp("a".repeat(40)))
        assertEquals("0000:0000:0000:0000:", FormatUtils.compactIp("0000:0000:0000:0000:"))
    }

    @Test
    fun `超长IPv6压缩为省略形式`() {
        assertEquals("2001:0db8:85a…0:7334", FormatUtils.compactIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
        assertEquals("0000:0000:000…0000:0", FormatUtils.compactIp("0000:0000:0000:0000:0"))
    }

    @Test
    fun `日期格式在零或负值时返回空串`() {
        assertEquals("", FormatUtils.formatDate(0L))
        assertEquals("", FormatUtils.formatDate(-1L))
        assertEquals("", FormatUtils.formatExpiryDate(0L))
        assertEquals("", FormatUtils.formatExpiryDate(-1L))
    }

    @Test
    fun `日期按系统时区格式化为ISO与点号两种形式`() {
        assertEquals("1970-01-01", FormatUtils.formatDate(1L))
        assertEquals("1970.01.01", FormatUtils.formatExpiryDate(1L))
        assertEquals("1970-01-02", FormatUtils.formatDate(86400L))
        assertEquals("1970.01.02", FormatUtils.formatExpiryDate(86400L))
        assertEquals("2023-11-14", FormatUtils.formatDate(1_700_000_000L))
        assertEquals("2023.11.14", FormatUtils.formatExpiryDate(1_700_000_000L))
    }

    @Test
    fun `格式化输出与设备默认区域无关`() {
        Locale.setDefault(Locale.US)
        val balances = listOf(0, 1, 100, 12345, -1, -12345, Int.MAX_VALUE, Int.MIN_VALUE)
        val traffic = listOf(0L, 1L, 1024L, 1536L, 1024L * 1024, 1024L * 1024 * 1024 * 3 / 2, 2L * 1024 * 1024 * 1024 * 1024, Long.MAX_VALUE)
        val epochs = listOf(1L, 86400L, 1_700_000_000L)

        val expectedBalance = balances.map { it to FormatUtils.balance(it) }
        val expectedTraffic = traffic.map { it to FormatUtils.traffic(it) }
        val expectedDate = epochs.map { it to FormatUtils.formatExpiryDate(it) }

        listOf(Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG")).forEach { locale ->
            Locale.setDefault(locale)
            expectedBalance.forEach { (cents, expected) ->
                assertEquals("balance($cents) 在 $locale 下不一致", expected, FormatUtils.balance(cents))
            }
            expectedTraffic.forEach { (bytes, expected) ->
                assertEquals("traffic($bytes) 在 $locale 下不一致", expected, FormatUtils.traffic(bytes))
            }
            expectedDate.forEach { (seconds, expected) ->
                assertEquals("formatExpiryDate($seconds) 在 $locale 下不一致", expected, FormatUtils.formatExpiryDate(seconds))
            }
        }
    }

    @Test
    fun `小数点与进位在区域切换前后保持英文习惯`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("1.00", FormatUtils.balance(100))
        assertEquals("123.45", FormatUtils.balance(12345))
        assertEquals("-0.01", FormatUtils.balance(-1))
        assertEquals("1.5KB", FormatUtils.traffic(1536L))
        assertEquals("2.25TB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024 * 9 / 4))
        assertEquals("1970.01.01", FormatUtils.formatExpiryDate(1L))

        Locale.setDefault(Locale.FRANCE)
        assertEquals("1.00", FormatUtils.balance(100))
        assertEquals("1.5KB", FormatUtils.traffic(1536L))
        assertEquals("2.25TB", FormatUtils.traffic(1024L * 1024 * 1024 * 1024 * 9 / 4))
        assertEquals("1970.01.01", FormatUtils.formatExpiryDate(1L))
    }

    @Test
    fun `已知周期映射到对应字符串资源`() {
        val context = mockk<Context>()
        every { context.getString(R.string.period_month) } returns "月付"
        every { context.getString(R.string.period_quarter) } returns "季付"
        every { context.getString(R.string.period_half_year) } returns "半年"
        every { context.getString(R.string.period_year) } returns "年付"
        every { context.getString(R.string.period_two_year) } returns "两年"
        every { context.getString(R.string.period_three_year) } returns "三年"
        every { context.getString(R.string.period_onetime) } returns "一次性"
        every { context.getString(R.string.period_reset) } returns "流量重置"

        assertEquals("月付", FormatUtils.periodLabel("month_price", context))
        assertEquals("季付", FormatUtils.periodLabel("quarter_price", context))
        assertEquals("半年", FormatUtils.periodLabel("half_year_price", context))
        assertEquals("年付", FormatUtils.periodLabel("year_price", context))
        assertEquals("两年", FormatUtils.periodLabel("two_year_price", context))
        assertEquals("三年", FormatUtils.periodLabel("three_year_price", context))
        assertEquals("一次性", FormatUtils.periodLabel("onetime_price", context))
        assertEquals("流量重置", FormatUtils.periodLabel("reset_price", context))
    }

    @Test
    fun `未知周期原样返回且不查询字符串资源`() {
        val context = mockk<Context>()

        assertEquals("weekly_price", FormatUtils.periodLabel("weekly_price", context))
        assertEquals("", FormatUtils.periodLabel("", context))
        assertEquals("MONTH_PRICE", FormatUtils.periodLabel("MONTH_PRICE", context))
        assertEquals("month_price ", FormatUtils.periodLabel("month_price ", context))

        verify(exactly = 0) { context.getString(any()) }
    }
}
