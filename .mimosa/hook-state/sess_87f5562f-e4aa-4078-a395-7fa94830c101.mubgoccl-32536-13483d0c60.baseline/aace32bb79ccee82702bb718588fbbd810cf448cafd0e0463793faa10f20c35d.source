package com.slte.app.domain

import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.isPlanValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val DAY_SECONDS = 86_400L
private const val TEN_YEARS_SECONDS = 3_650L * DAY_SECONDS
private const val NO_PLAN_ID = 0
private const val PLAN_ID = 7
private const val NO_TRANSFER_ENABLE = 0L
private const val PLAN_TRANSFER_ENABLE = 400L * 1024 * 1024 * 1024

private fun subscribeInfo(
    planName: String,
    planId: Int,
    transferEnable: Long,
    expiredAt: Long,
    usedTraffic: Long = 0L,
) = SubscribeInfo(
    planName = planName,
    transferEnable = transferEnable,
    usedTraffic = usedTraffic,
    expiredAt = expiredAt,
    planId = planId,
)

@RunWith(Parameterized::class)
class SubscribeInfoExpiryBoundaryTest(
    private val label: String,
    private val expiredAt: Long,
    private val expectedExpired: Boolean,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf<Any>("到期时间为零视为永久有效", 0L, false),
            arrayOf<Any>("到期时间为负一视为永久有效", -1L, false),
            arrayOf<Any>("到期时间为极小负值视为永久有效", Long.MIN_VALUE, false),
            arrayOf<Any>("到期时间为纪元第一秒已过期", 1L, true),
            arrayOf<Any>("到期时间为上个十年已过期", 1_600_000_000L, true),
            arrayOf<Any>("到期时间为极大值不视为过期", Long.MAX_VALUE, false),
        )
    }

    @Test
    fun `非正数到期时间一律视为未过期且不参与有效性判定`() {
        val info =
            subscribeInfo(
                planName = "Pro",
                planId = PLAN_ID,
                transferEnable = PLAN_TRANSFER_ENABLE,
                expiredAt = expiredAt,
            )

        assertEquals("$label：过期判定", expectedExpired, info.expired)
        assertEquals("$label：有过期时间的套餐有效期只由过期判定决定", !expectedExpired, isPlanValid(info))
    }

    @Test
    fun `到期时间不参与套餐存在性判定`() {
        val info =
            subscribeInfo(
                planName = "Pro",
                planId = PLAN_ID,
                transferEnable = PLAN_TRANSFER_ENABLE,
                expiredAt = expiredAt,
            )

        assertTrue("$label：到期时间取值不影响 hasPlan", info.hasPlan)
    }
}

@RunWith(Parameterized::class)
class SubscribeInfoClockRelativeMatrixTest(
    private val label: String,
    private val planName: String,
    private val planId: Int,
    private val transferEnable: Long,
    private val expiryOffsetSeconds: Long,
    private val expectedHasPlan: Boolean,
    private val expectedExpired: Boolean,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf<Any>("无套餐名无额度_一天前到期", " ", NO_PLAN_ID, NO_TRANSFER_ENABLE, -DAY_SECONDS, false, true),
            arrayOf<Any>("无套餐名无额度_一秒后到期", "", NO_PLAN_ID, NO_TRANSFER_ENABLE, 1L, false, false),
            arrayOf<Any>("无套餐名无额度_十年后到期", "  ", NO_PLAN_ID, NO_TRANSFER_ENABLE, TEN_YEARS_SECONDS, false, false),
            arrayOf<Any>("有套餐_一天前到期", "Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, -DAY_SECONDS, true, true),
            arrayOf<Any>("有套餐_一秒前到期", "Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, -1L, true, true),
            arrayOf<Any>("有套餐_一秒后到期", "Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, 1L, true, false),
            arrayOf<Any>("有套餐_一小时后到期", "Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, 3_600L, true, false),
            arrayOf<Any>("有套餐_十年后到期", "Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, TEN_YEARS_SECONDS, true, false),
            arrayOf<Any>("仅套餐ID_一秒前到期", "Pro", PLAN_ID, NO_TRANSFER_ENABLE, -1L, true, true),
            arrayOf<Any>("仅额度_一小时后到期", "Pro", NO_PLAN_ID, PLAN_TRANSFER_ENABLE, 3_600L, true, false),
            arrayOf<Any>("无套餐名但额度非零_一小时后到期", "", NO_PLAN_ID, PLAN_TRANSFER_ENABLE, 3_600L, false, false),
        )
    }

    @Test
    fun `套餐存在性与过期性的全组合矩阵`() {
        val info =
            subscribeInfo(
                planName = planName,
                planId = planId,
                transferEnable = transferEnable,
                expiredAt = SubscribeInfo.currentTimeSeconds() + expiryOffsetSeconds,
            )

        assertEquals("$label：套餐存在性", expectedHasPlan, info.hasPlan)
        assertEquals("$label：过期判定", expectedExpired, info.expired)
        assertEquals(
            "$label：有效性必须等价于「有套餐且未过期」",
            expectedHasPlan && !expectedExpired,
            isPlanValid(info),
        )
    }
}

class SubscribeInfoClockBoundaryTest {

    @Test
    fun `到期秒正好等于读取时刻时沿用严格大于的比较`() {
        val before = SubscribeInfo.currentTimeSeconds()
        val info =
            subscribeInfo(
                planName = "Pro",
                planId = PLAN_ID,
                transferEnable = PLAN_TRANSFER_ENABLE,
                expiredAt = before,
            )

        val expired = info.expired
        val after = SubscribeInfo.currentTimeSeconds()

        if (expired) {
            assertTrue("过期必须发生在到期秒之后", info.expiredAt < after)
        } else {
            assertTrue("未过期说明到期秒不早于读取到的时钟", info.expiredAt >= before)
        }
    }

    @Test
    fun `套餐存在性与到期时间取值相互独立`() {
        val plan = subscribeInfo("Pro", PLAN_ID, PLAN_TRANSFER_ENABLE, expiredAt = 0L)
        val variants =
            listOf(
                Long.MIN_VALUE,
                -1L,
                0L,
                1L,
                SubscribeInfo.currentTimeSeconds() - 1L,
                SubscribeInfo.currentTimeSeconds() + 1L,
                Long.MAX_VALUE,
            )
        val flags = variants.map { plan.copy(expiredAt = it).hasPlan }

        assertTrue("hasPlan 不受到期时间影响", flags.all { it == plan.hasPlan })
        assertTrue("示例套餐本身必须有套餐", plan.hasPlan)
    }

    @Test
    fun `已用流量不参与任何到期或有效性判定`() {
        val base = subscribeInfo("Pro", PLAN_ID, 100L, expiredAt = SubscribeInfo.currentTimeSeconds() + 3_600L)
        val overdrawn = base.copy(usedTraffic = Long.MAX_VALUE)
        val untouched = base.copy(usedTraffic = 0L)

        assertEquals(untouched.hasPlan, overdrawn.hasPlan)
        assertEquals(untouched.expired, overdrawn.expired)
        assertEquals(isPlanValid(untouched), isPlanValid(overdrawn))
        assertTrue("超出额度的套餐仍然算「有套餐」", overdrawn.hasPlan)
    }

    @Test
    fun `空订阅信息判为无效`() {
        assertFalse(isPlanValid(null))
    }
}
