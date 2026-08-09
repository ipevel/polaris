package com.slte.app.data.remote.adapter.xiaov2b

import org.junit.Assert.assertEquals
import org.junit.Test

class XiaoV2bDtoTest {

    @Test
    fun `userInfo 字段透传`() {
        val info = XiaoV2bUserInfoData(
            email = "a@b.c",
            balance = 100,
            planId = 2,
            expiredAt = 1000L,
            transferEnable = 1024L,
            remindExpire = 1,
            remindTraffic = 0
        )
        val dto = info.toDomainUserInfo()
        assertEquals("a@b.c", dto.email)
        assertEquals(100, dto.balance)
        assertEquals(2, dto.planId)
        assertEquals(1000L, dto.expiredAt)
        assertEquals(1024L, dto.transferEnable)
        assertEquals(1, dto.remindExpire)
        assertEquals(0, dto.remindTraffic)
    }

    @Test
    fun `subscribeInfo 解析套餐名与重置日`() {
        val data = XiaoV2bSubscribeData(
            planId = 3,
            expiredAt = 2000L,
            transferEnable = 512L,
            u = 10L,
            d = 20L,
            resetDay = 5,
            plan = XiaoV2bPlanData(name = "基础套餐")
        )
        val dto = data.toDomainSubscribeInfo()
        assertEquals(3, dto.planId)
        assertEquals("基础套餐", dto.planName)
        assertEquals(5, dto.resetDay)
        assertEquals(10L, dto.upload)
        assertEquals(20L, dto.download)
    }

    @Test
    fun `subscribeInfo 无套餐时名称兜底为空`() {
        val dto = XiaoV2bSubscribeData(planId = 3).toDomainSubscribeInfo()
        assertEquals("", dto.planName)
        assertEquals(null, dto.resetDay)
    }

    @Test
    fun `plan 开关 0-1 转为布尔`() {
        val plan = XiaoV2bPlanData(name = "p", show = 1, renew = 0)
        val dto = plan.toDomainPlan()
        assertEquals(true, dto.show)
        assertEquals(false, dto.renew)
    }
}
