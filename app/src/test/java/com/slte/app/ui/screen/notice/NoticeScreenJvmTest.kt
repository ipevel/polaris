// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.notice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.Notice
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.utils.FormatUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 公告页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 逐项盯的是迁移清单里的可测项：三态渲染、重试、行点击开详情面板、同一 id 去重。
 * 顶栏返回按钮不在本测试的可断言范围内：`V5TopIconButton` 的图标 `contentDescription` 为 null，
 * 语义树里没有可定位的文本/描述（这是既有 v5 组件的无障碍缺口，用户本轮明确不处理），
 * 因此返回入口由雷电模拟器真机验证覆盖，不以单测冒充分。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class NoticeScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val sessionStore = SessionStore(InMemoryPreferences())
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val repository = SubscribeRepository(api, sessionStore, sessionManager)
    private val viewModel = NoticeViewModel(repository)

    init {
        every { sessionManager.sessionState } returns
            MutableStateFlow<SessionState>(SessionState.LoggedIn(User(id = "notice-test", displayName = "notice@example.com")))
        every { sessionManager.logoutEvents } returns MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    private fun notice(id: Int) = Notice(
        id = id,
        title = "线路升级公告 $id",
        body = "<p>9929 线路将于凌晨升级维护。</p>",
        tags = listOf("公告"),
        createdAt = 1_700_000_000L,
    )

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                NoticeScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun 初始渲染加载态() {
        content()

        composeRule.onNodeWithText("正在加载…").assertIsDisplayed()
        composeRule.onNodeWithText("公告通知").assertIsDisplayed()
    }

    @Test
    fun 无公告时显示空态() {
        api.notices = emptyList()
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.phase == ContentPhase.Idle }

        composeRule.onNodeWithText("暂无公告").assertIsDisplayed()
    }

    @Test
    fun 有公告时渲染标题标签摘要与日期() {
        api.notices = listOf(notice(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.notices.isNotEmpty() }

        composeRule.onNodeWithText("线路升级公告 1").assertIsDisplayed()
        composeRule.onNodeWithText("公告").assertIsDisplayed()
        composeRule.onNodeWithText("9929 线路将于凌晨升级维护。").assertIsDisplayed()
        // 日期用被测代码的同一个格式化函数算期望值：跨时区跑也不会误判（ISO 日期按本机时区取日）。
        composeRule.onNodeWithText(FormatUtils.formatDate(1_700_000_000L)).assertIsDisplayed()
    }

    @Test
    fun 同名重复公告只渲染一行() {
        api.notices = listOf(notice(1), notice(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.notices.isNotEmpty() }

        assertEquals(1, composeRule.onAllNodesWithText("线路升级公告 1").fetchSemanticsNodes().size)
    }

    @Test
    fun 加载失败显示错误并可重试成功() {
        api.noticesError = java.io.IOException("连接超时")
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.errorMessageRes != null }

        composeRule.onNodeWithText("连接超时").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()

        api.noticesError = null
        api.notices = listOf(notice(2))
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.notices.isNotEmpty() }

        composeRule.onNodeWithText("线路升级公告 2").assertIsDisplayed()
    }

    @Test
    fun 点击公告行打开详情面板() {
        api.notices = listOf(notice(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.notices.isNotEmpty() }

        assertEquals(1, composeRule.onAllNodesWithText("线路升级公告 1").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("线路升级公告 1").performClick()
        composeRule.waitForIdle()

        // 打开面板后标题同时出现在行内与面板标题上，用节点数从 1 变 2 证明面板真的拉起来了。
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("线路升级公告 1").fetchSemanticsNodes().size == 2
        }
    }
}
