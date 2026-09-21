package com.slte.app.domain

import com.slte.app.domain.usecase.CountdownUseCase
import com.slte.app.utils.VerificationCodeConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownUseCaseTest {

    @Test
    fun `倒计时从配置秒数逐秒递减到零`() = runTest {
        val emissions = CountdownUseCase().invoke().toList()

        assertEquals(
            (VerificationCodeConfig.countdownSeconds downTo 0).toList(),
            emissions,
        )
        assertEquals(VerificationCodeConfig.countdownSeconds + 1, emissions.size)
    }

    @Test
    fun `首帧立即发出且每帧间隔等于配置间隔`() = runTest {
        val emitTimes = mutableListOf<Long>()

        CountdownUseCase().invoke().collect { emitTimes.add(testScheduler.currentTime) }

        assertEquals(
            (0..VerificationCodeConfig.countdownSeconds)
                .map { it * VerificationCodeConfig.countdownIntervalMs },
            emitTimes,
        )
        assertEquals("首帧不应等待", 0L, emitTimes.first())
    }

    @Test
    fun `倒计时总时长等于秒数乘间隔`() = runTest {
        val start = testScheduler.currentTime

        CountdownUseCase().invoke().toList()

        assertEquals(
            VerificationCodeConfig.countdownSeconds * VerificationCodeConfig.countdownIntervalMs,
            testScheduler.currentTime - start,
        )
    }
}
