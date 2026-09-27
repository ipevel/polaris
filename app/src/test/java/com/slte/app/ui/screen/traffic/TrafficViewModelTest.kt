// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import com.slte.app.R
import com.slte.app.data.repository.TrafficRepository
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.support.MainDispatcherRule
import com.slte.app.utils.Diagnostics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TrafficViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = mockk<TrafficRepository>(relaxed = true)

    private val diagnostics = mockk<Diagnostics>(relaxed = true)

    init {
        // relaxed mock 的 Boolean 默认是 false，而 false 在流量页意味着"面板不支持明细"，
        // 会让下面两个用例直接走能力缺失分支。默认桩成支持，「不支持」的分支单独用例覆盖。
        every { repository.supportsTrafficLog() } returns true
    }

    /**
     * 面板长时间不响应时必须落到失败态，而不是把「加载中…」一直挂在页面上。
     *
     * 回归背景（用户反馈"流量页一直加载中"）：底层 OkHttp 的 `API_CALL_TIMEOUT_SECONDS = 45`，
     * 面板不可达时用户要盯着「加载中…」45 秒，期间没有任何出口。现在 ViewModel 侧收紧到 15s，
     * 超时合成一个失败结果，走与真实失败同一条出口（错误横幅 + 重试按钮）。
     *
     * 用例里用虚拟时间：`delay(60_000)` 会被 15s 的超时中断，`advanceUntilIdle()` 一次推完。
     */
    @Test
    fun `面板长时间不响应时落到失败态而不是一直加载中`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchTrafficLog() } coAnswers {
            delay(60_000)
            Result.success(listOf(TrafficLogRecord("2026-09-26", 1L, 2L)))
        }

        val vm = TrafficViewModel(repository, diagnostics)
        advanceUntilIdle()

        assertFalse("不能停在加载中（用户看到的就是这个）", vm.data.value.isLoading)
        assertEquals(R.string.traffic_load_failed, vm.data.value.errorMessageRes)
    }

    @Test
    fun `加载成功时填充记录并清掉加载态`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchTrafficLog() } returns
            Result.success(listOf(TrafficLogRecord("2026-09-26", 320_000_000L, 2_400_000_000L)))

        val vm = TrafficViewModel(repository, diagnostics)
        advanceUntilIdle()

        assertEquals(1, vm.data.value.records.size)
        assertFalse(vm.data.value.isLoading)
        assertNull(vm.data.value.errorMessageRes)
    }

    /**
     * 面板不支持每日流量明细（xiaov2b 系列没有该路由）时：不发请求、不报错，
     * 只把能力缺失告诉 UI。否则页面会显示「暂无流量记录」，把"面板没这个接口"
     * 伪装成"你没有流量"。
     */
    @Test
    fun `面板不支持流量明细时不发请求且标记能力缺失`() = runTest(mainRule.dispatcher) {
        every { repository.supportsTrafficLog() } returns false

        val vm = TrafficViewModel(repository, diagnostics)
        advanceUntilIdle()

        assertFalse("不能停在加载中", vm.data.value.isLoading)
        assertFalse("要标记面板不支持，UI 才能给出正确说明", vm.data.value.backendSupportsLog)
        assertNull("这不是失败，不该显示错误态", vm.data.value.errorMessageRes)
        coVerify(exactly = 0) { repository.fetchTrafficLog() }
    }
}
