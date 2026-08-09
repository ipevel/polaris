package com.slte.app.data.remote.adapter.xboard

import org.junit.Assert.assertEquals
import org.junit.Test

class XboardDtoTest {

    @Test
    fun `userInfo 布尔提醒开关转为 0-1`() {
        val info = XboardUserInfoData(remindExpire = true, remindTraffic = false)
        val dto = info.toDomainUserInfo()
        assertEquals(1, dto.remindExpire)
        assertEquals(0, dto.remindTraffic)
    }

    @Test
    fun `userInfo 默认提醒开关为关闭`() {
        val dto = XboardUserInfoData().toDomainUserInfo()
        assertEquals(0, dto.remindExpire)
        assertEquals(0, dto.remindTraffic)
    }

    @Test
    fun `邀请码布尔状态转为 0-1`() {
        val code = XboardInviteCodeData(code = "ABC123", status = false)
        assertEquals(0, code.toDomain().status)
        assertEquals("ABC123", code.toDomain().code)
    }

    @Test
    fun `plan 布尔 show renew 透传`() {
        val plan = XboardPlanData(name = "p", show = true, renew = false)
        val dto = plan.toDomainPlan()
        assertEquals(true, dto.show)
        assertEquals(false, dto.renew)
    }

    @Test
    fun `notice 布尔 show 可解析`() {
        val notice = XboardNoticeData(title = "标题", content = "正文", show = true)
        val domain = notice.toDomain()
        assertEquals("标题", domain.title)
        assertEquals("正文", domain.body)
    }

    @Test
    fun `订单金额容错解析`() {
        val order = XboardOrderData(
            tradeNo = "T1",
            balanceAmount = kotlinx.serialization.json.JsonPrimitive("100"),
            discountAmount = null
        )
        val dto = order.toDomainOrder()
        assertEquals(100, dto.balanceAmount)
        assertEquals(0, dto.discountAmount)
    }
}
