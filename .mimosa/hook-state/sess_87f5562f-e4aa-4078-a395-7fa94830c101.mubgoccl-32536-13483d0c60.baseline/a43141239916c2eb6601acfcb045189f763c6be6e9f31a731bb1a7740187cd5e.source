package com.slte.app.ui.screen.main

import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.isPlanValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPatchTest {
    private val now = System.currentTimeMillis() / 1000

    @Test
    fun `有效套餐写入用量与到期信息`() {
        val patched =
            DashboardData().withSubscribeInfo(
                info = plan(transferEnable = 400L * 1024 * 1024 * 1024, usedTraffic = 65L * 1024 * 1024 * 1024),
                daysUntilExpired = 49,
                errorMessageRes = null,
            )

        assertEquals(400L * 1024 * 1024 * 1024, patched.totalBytes)
        assertEquals(65L * 1024 * 1024 * 1024, patched.usedBytes)
        assertEquals("进阶套餐", patched.planName)
        assertEquals(49, patched.daysUntilExpired)
        assertTrue(patched.hasPlan)
        assertTrue(patched.isValid)
        assertTrue(patched.dataLoaded)
        assertFalse("加载完成后不应仍标记刷新中", patched.isRefreshing)
        assertFalse(patched.isUpdating)
    }

    @Test
    fun `已到期套餐不算有效`() {
        val patched =
            DashboardData().withSubscribeInfo(
                info = plan(expiredAt = now - 60L),
                daysUntilExpired = 0,
                errorMessageRes = null,
            )

        assertTrue("仍应保留套餐名用于展示", patched.planName.isNotBlank())
        assertTrue(patched.hasPlan)
        assertFalse(patched.isValid)
    }

    @Test
    fun `无套餐时清空展示字段`() {
        val patched =
            DashboardData(
                planName = "旧套餐",
                usedBytes = 1L,
                totalBytes = 2L,
                isValid = true,
            ).withSubscribeInfo(info = null, daysUntilExpired = 0, errorMessageRes = null)

        assertEquals("", patched.planName)
        assertEquals(0L, patched.usedBytes)
        assertEquals(0L, patched.totalBytes)
        assertFalse(patched.hasPlan)
        assertFalse(patched.isValid)
    }

    @Test
    fun `错误提示随映射写入且成功时清空`() {
        assertEquals(123, DashboardData().withSubscribeInfo(null, 0, errorMessageRes = 123).errorMessageRes)
        assertNull(DashboardData(errorMessageRes = 123).withSubscribeInfo(null, 0, errorMessageRes = null).errorMessageRes)
    }

    @Test
    fun `无 planId 时按流量判定是否有套餐`() {
        assertFalse(isPlanValid(plan(planId = 0, transferEnable = 0L)))
        assertTrue(isPlanValid(plan(planId = 0, transferEnable = 100L)))
        assertTrue(isPlanValid(plan(planId = 9, transferEnable = 0L)))
        assertFalse("空套餐名不算有效", isPlanValid(plan(planName = "")))
    }

    @Test
    fun `展示落库与领域规则使用同一有效性判定`() {
        assertTrue(isPlanValid(plan()))
        assertTrue(DashboardData().withSubscribeInfo(plan(), 49, null).isValid)

        assertFalse("无订阅信息时领域规则判为无效", isPlanValid(null))
        assertFalse(DashboardData().withSubscribeInfo(null, 0, null).isValid)

        assertFalse("已过期时领域规则判为无效", isPlanValid(plan(expiredAt = now - 60L)))
        assertFalse(DashboardData().withSubscribeInfo(plan(expiredAt = now - 60L), 0, null).isValid)
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
