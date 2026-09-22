// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscribePlanValidityDomainLogicTest {
    private val now = System.currentTimeMillis() / 1000L

    @Test
    fun `有套餐且未过期时为有效`() {
        assertTrue(isPlanValid(plan(expiredAt = now + 49L * 86_400)))
        assertTrue(isPlanValid(plan(expiredAt = now + 60L)))
    }

    @Test
    fun `已过期时无效但仍保留套餐信息`() {
        val expired = plan(expiredAt = now - 60L)
        assertTrue(expired.expired)
        assertTrue("过期不应抹掉套餐名", expired.planName.isNotBlank())
        assertTrue("过期不应抹掉套餐判定", expired.hasPlan)
        assertFalse(isPlanValid(expired))
    }

    @Test
    fun `无 planId 时按流量判定有效性`() {
        assertFalse(isPlanValid(plan(planId = 0, transferEnable = 0L)))
        assertTrue(isPlanValid(plan(planId = 0, transferEnable = 100L)))
        assertTrue(isPlanValid(plan(planId = 9, transferEnable = 0L)))
        assertFalse(isPlanValid(plan(planId = 0, transferEnable = 0L, expiredAt = now + 86_400L)))
    }

    @Test
    fun `planName 为空时无效`() {
        assertFalse(isPlanValid(plan(planName = "")))
        assertFalse(isPlanValid(plan(planName = " ")))
        assertFalse(isPlanValid(plan(planName = "", planId = 9, transferEnable = 100L)))
    }

    @Test
    fun `无订阅信息时无效`() {
        assertFalse(isPlanValid(null))
    }

    @Test
    fun `无到期时间的套餐视为永久有效`() {
        assertTrue(isPlanValid(plan(expiredAt = 0L)))
        assertFalse(isPlanValid(plan(expiredAt = 0L, transferEnable = 0L, planId = 0)))
    }

    private fun plan(
        planName: String = "进阶套餐",
        transferEnable: Long = 400L * 1024 * 1024 * 1024,
        usedTraffic: Long = 0L,
        expiredAt: Long = now + 49L * 86_400,
        planId: Int = 7,
    ) = SubscribeInfo(
        planName = planName,
        transferEnable = transferEnable,
        usedTraffic = usedTraffic,
        expiredAt = expiredAt,
        planId = planId,
    )
}
