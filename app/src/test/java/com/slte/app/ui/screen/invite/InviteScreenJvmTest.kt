// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.repository.InviteRepository
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.domain.model.InviteInfo
import com.slte.app.domain.model.InviteStat
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class InviteScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val viewModel = InviteViewModel(InviteRepository(api))

    private fun stubPage() {
        api.inviteInfo =
            InviteInfo(
                stat = InviteStat(availableBalance = 56_700, registeredUsers = 3, commissionRate = 25),
                codes = listOf(InviteCodeInfo(code = "ABC123", pv = 0)),
            )
        api.commissionRecords = emptyList()
        api.withdrawMethods = listOf("USDT")
    }

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                InviteScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.stat.availableBalance == 56_700 }
    }

    @Test
    fun 渲染邀请码与佣金信息() {
        stubPage()
        viewModel.enterAndRefresh()
        content()

        composeRule.onNodeWithText("ABC123").assertIsDisplayed()
        composeRule.onNodeWithText("567.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun 打开提现弹层展示后端下发的方式() {
        stubPage()
        viewModel.enterAndRefresh()
        content()

        composeRule.onNodeWithText("申请提现").performClick()

        composeRule.onNodeWithText("USDT").assertIsDisplayed()
        composeRule.onNodeWithText("请输入提现账号").assertIsDisplayed()
    }

    @Test
    fun 打开佣金划转弹层() {
        stubPage()
        viewModel.enterAndRefresh()
        content()

        composeRule.onNodeWithText("佣金划转").performClick()

        composeRule.onNodeWithText("确认划转").assertIsDisplayed()
    }
}
