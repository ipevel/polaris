// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import com.slte.app.data.local.SessionManager
import com.slte.app.data.repository.TicketRepository
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.domain.model.User
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * 工单 ViewModel 的详情加载回归。
 *
 * 存在的原因：v5 化改造后 `TicketScreenJvmTest.点击工单行打开详情面板` 一直没有等到详情，
 * 需要先在 VM 层把"点行 → 拉详情"这条路走通，才能判断问题在 UI 接线还是数据结构。
 */
class TicketDetailLoadTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    init {
        every { sessionManager.sessionState } returns
            MutableStateFlow<SessionState>(SessionState.LoggedIn(User(id = "u", displayName = "u@e.com")))
        every { sessionManager.logoutEvents } returns MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    private val repository by lazy { TicketRepository(api, sessionManager) }

    private fun ticket(id: Int) = Ticket(
        id = id,
        level = 1,
        replyStatus = 0,
        status = 0,
        subject = "无法连接节点 $id",
        createdAt = 1_700_000_000L,
        updatedAt = 1_700_000_000L,
    )

    @Test
    fun 打开详情后进入已加载状态() = runTest(mainRule.dispatcher) {
        api.ticketDetail = TicketDetail(ticket = ticket(1), messages = emptyList())
        val vm = TicketViewModel(repository)

        vm.openDetail(1)
        advanceUntilIdle()

        assertNotNull("详情应已加载", vm.uiState.value.detail)
        assertEquals(null, vm.uiState.value.detailErrorRes)
    }
}
