package com.slte.app.domain

import com.slte.app.domain.usecase.DaysUntilExpiryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaysUntilExpiryExtremeValueTest {

    private val useCase = DaysUntilExpiryUseCase()

    @Test
    fun `非正纪元秒返回零`() {
        assertEquals(0, useCase(0L))
        assertEquals(0, useCase(-1L))
        assertEquals(0, useCase(Long.MIN_VALUE))
    }

    @Test
    fun `极端远期纪元秒不抛异常且收敛到上限`() {
        assertEquals(36_500, useCase(Long.MAX_VALUE))
        assertEquals(36_500, useCase(31_556_889_864_403_199L))
    }

    @Test
    fun `常规远期纪元秒按天数返回`() {
        val tenDaysLater = System.currentTimeMillis() / 1000L + 10L * 86_400L
        val days = useCase(tenDaysLater)
        assertTrue("实际 $days", days in 9..10)
    }
}
