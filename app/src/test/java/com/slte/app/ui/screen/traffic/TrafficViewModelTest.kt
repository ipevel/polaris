// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import com.slte.app.R
import com.slte.app.data.repository.TrafficRepository
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
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

        val vm = TrafficViewModel(repository)
        advanceUntilIdle()

        assertFalse("不能停在加载中（用户看到的就是这个）", vm.data.value.isLoading)
        assertEquals(R.string.traffic_load_failed, vm.data.value.errorMessageRes)
    }

    @Test
    fun `加载成功时填充记录并清掉加载态`() = runTest(mainRule.dispatcher) {
        coEvery { repository.fetchTrafficLog() } returns
            Result.success(listOf(TrafficLogRecord("2026-09-26", 320_000_000L, 2_400_000_000L)))

        val vm = TrafficViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, vm.data.value.records.size)
        assertFalse(vm.data.value.isLoading)
        assertNull(vm.data.value.errorMessageRes)
    }
}
