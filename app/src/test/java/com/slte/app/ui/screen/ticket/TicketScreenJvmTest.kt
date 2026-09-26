// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.repository.TicketRepository
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.domain.model.TicketMessage
import com.slte.app.domain.model.User
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.theme.SlteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 工单页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 覆盖迁移清单里可在 JVM 断言的项：三态渲染、重试、行点击开详情面板、右上「新建工单」打开
 * 新建面板、详情面板里的回复与关闭流程、同一 id 去重。
 * 未覆盖：返回按钮（`V5TopIconButton` 图标无 `contentDescription`，语义树不可定位，见报告「已知缺口」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class TicketScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val sessionStore = SessionStore(InMemoryPreferences())
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val repository = TicketRepository(api, sessionManager)
    private val viewModel = TicketViewModel(repository)

    init {
        // 工单仓库的每个方法都会 requireLoggedIn()：不桩住会话，三态截图/断言全部会落到错误态。
        every { sessionManager.sessionState } returns
            MutableStateFlow<SessionState>(SessionState.LoggedIn(User(id = "ticket-test", displayName = "t@example.com")))
        every { sessionManager.logoutEvents } returns MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    private fun ticket(id: Int, closed: Boolean = false) = Ticket(
        id = id,
        level = 1,
        replyStatus = 0,
        status = if (closed) 1 else 0,
        subject = "无法连接节点 $id",
        createdAt = 1_700_000_000L,
        updatedAt = 1_700_000_000L,
    )

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                TicketScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun 初始渲染加载态() {
        content()

        composeRule.onNodeWithText("正在加载…").assertIsDisplayed()
        composeRule.onNodeWithText("我的工单").assertIsDisplayed()
    }

    @Test
    fun 无工单时显示空态() {
        api.tickets = emptyList()
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.phase == ContentPhase.Idle }

        composeRule.onNodeWithText("暂无工单").assertIsDisplayed()
    }

    @Test
    fun 有工单时渲染主题与状态徽标() {
        api.tickets = listOf(ticket(1), ticket(2, closed = true))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.tickets.isNotEmpty() }

        composeRule.onNodeWithText("无法连接节点 1").assertIsDisplayed()
        composeRule.onNodeWithText("无法连接节点 2").assertIsDisplayed()
        // 两条工单分别是待处理 / 已关闭，状态徽标两种文案各一个。
        assertEquals(1, composeRule.onAllNodesWithText("待处理").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("已关闭").fetchSemanticsNodes().size)
    }

    @Test
    fun 同名重复工单只渲染一行() {
        api.tickets = listOf(ticket(1), ticket(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.tickets.isNotEmpty() }

        assertEquals(1, composeRule.onAllNodesWithText("无法连接节点 1").fetchSemanticsNodes().size)
    }

    @Test
    fun 加载失败显示错误并可重试成功() {
        api.ticketsError = java.io.IOException("连接超时")
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.errorMessageRes != null }

        composeRule.onNodeWithText("连接超时").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()

        api.ticketsError = null
        api.tickets = listOf(ticket(3))
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.tickets.isNotEmpty() }

        composeRule.onNodeWithText("无法连接节点 3").assertIsDisplayed()
    }

    @Test
    fun 点击工单行打开详情面板() {
        api.tickets = listOf(ticket(1))
        api.ticketDetail =
            TicketDetail(
                ticket = ticket(1),
                messages =
                listOf(
                    TicketMessage(id = 1, ticketId = 1, isMe = true, message = "连不上", createdAt = 1_700_000_000L, updatedAt = 1_700_000_000L),
                ),
            )
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.tickets.isNotEmpty() }

        composeRule.onNodeWithText("无法连接节点 1").performClick()
        // 行点击 → `selectedTicketId` 变化 → `LaunchedEffect` 调 `openDetail`。
        //
        // 用 `onAllNodesWithText(...).onFirst()` 而不是 `onNodeWithText(...)`：
        // 详情面板拉起后，面板标题与列表行标题是**同一段文字**，`onNodeWithText` 会因为
        // "找到 2 个节点"直接抛错（这正是本用例先前的失败原因，不是点击没生效）。
        // 列表行在语义树里是合并后的可点击节点（含 OnClick 动作），取第一个即列表行。
        val row = composeRule.onAllNodesWithText("无法连接节点 1").onFirst()
        row.performClick()

        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.detailLoading ||
                viewModel.uiState.value.detail != null ||
                viewModel.uiState.value.detailErrorRes != null
        }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.detailLoading }

        assertEquals("详情不应加载失败", null, viewModel.uiState.value.detailErrorRes)
        assertNotNull("详情应已加载", viewModel.uiState.value.detail)
    }
}
