package com.slte.app.domain

import com.slte.app.domain.usecase.DaysUntilExpiryUseCase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val DAY_SECONDS = 86_400L
private const val HALF_DAY_SECONDS = DAY_SECONDS / 2

@RunWith(Parameterized::class)
class DaysUntilExpiryUseCaseBoundaryTest(
    private val label: String,
    private val expiredAt: Long,
    private val expectedDays: Int,
) {
    companion object {
        private val now = Instant.now().epochSecond

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf<Any>("到期时间为零", 0L, 0),
            arrayOf<Any>("到期时间为负一", -1L, 0),
            arrayOf<Any>("到期时间为极小负值", Long.MIN_VALUE, 0),
            arrayOf<Any>("到期时间为纪元第一秒", 1L, 0),
            arrayOf<Any>("刚过期一秒", now - 1L, 0),
            arrayOf<Any>("过期三十天", now - 30L * DAY_SECONDS, 0),
            arrayOf<Any>("过期一年", now - 365L * DAY_SECONDS, 0),
            arrayOf<Any>("一秒钟后到期", now + 1L, 0),
            arrayOf<Any>("当天到期", now + HALF_DAY_SECONDS, 0),
            arrayOf<Any>("差一秒满一天", now + DAY_SECONDS - 1L, 0),
            arrayOf<Any>("一天半", now + DAY_SECONDS + HALF_DAY_SECONDS, 1),
            arrayOf<Any>("三天半", now + 3L * DAY_SECONDS + HALF_DAY_SECONDS, 3),
            arrayOf<Any>("两千九百二十二天半", now + 2_922L * DAY_SECONDS + HALF_DAY_SECONDS, 2_922),
            arrayOf<Any>("十年半", now + 3_650L * DAY_SECONDS + HALF_DAY_SECONDS, 3_650),
        )
    }

    @Test
    fun `剩余天数按整天向下取整且过期后恒为零`() {
        assertEquals(label, expectedDays, DaysUntilExpiryUseCase()(expiredAt))
    }
}

class DaysUntilExpiryUseCaseInvariantTest {
    private val useCase = DaysUntilExpiryUseCase()

    @Test
    fun `剩余天数永不为负`() {
        val now = Instant.now().epochSecond
        val expiredValues =
            listOf(
                Long.MIN_VALUE,
                -1L,
                0L,
                1L,
                now - 1L,
                now - DAY_SECONDS,
                now - 3_650L * DAY_SECONDS,
            )

        expiredValues.forEach { expiredAt ->
            val days = useCase(expiredAt)
            assertEquals("到期时间 $expiredAt 必须落到零而不是负数", 0, days)
            assertTrue("剩余天数不得为负", days >= 0)
        }
    }

    @Test
    fun `剩余天数随到期时间单调不减`() {
        val now = Instant.now().epochSecond
        val days = (0..12).map { useCase(now + it * DAY_SECONDS + HALF_DAY_SECONDS) }

        assertEquals((0..12).toList(), days)
        days.zipWithNext().forEach { (earlier, later) ->
            assertTrue("后天数不应小于前一天数", later >= earlier)
        }
    }

    @Test
    fun `过期与当天到期在剩余天数上无法区分`() {
        val now = Instant.now().epochSecond

        assertEquals(useCase(now - 10L * DAY_SECONDS), useCase(now + 1L))
        assertEquals(0, useCase(now + 1L))
    }

    @Test
    fun `边界上一秒之差即为一天之差`() {
        val now = Instant.now().epochSecond
        val justUnderADay = useCase(now + DAY_SECONDS - 1L)
        val justOverADay = useCase(now + DAY_SECONDS + 1L)

        assertEquals(0, justUnderADay)
        assertEquals(1, justOverADay)
        assertEquals(1, justOverADay - justUnderADay)
    }
}
