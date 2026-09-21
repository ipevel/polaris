package com.slte.app.ui.screen.invite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slte.app.data.repository.InviteRepository
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.domain.model.InviteInfo
import com.slte.app.domain.model.InviteStat
import com.slte.app.support.FakeAuthApi
import com.slte.app.ui.theme.SlteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InviteScreenTest {
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

    @Test
    fun 渲染邀请码与佣金信息() {
        stubPage()
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                InviteScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.stat.availableBalance == 56_700 }

        composeRule.onNodeWithText("ABC123").assertIsDisplayed()
        composeRule.onNodeWithText("567.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun 打开提现弹层展示后端下发的方式() {
        stubPage()
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                InviteScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.stat.availableBalance == 56_700 }

        composeRule.onNodeWithText("申请提现").performClick()

        composeRule.onNodeWithText("USDT").assertIsDisplayed()
        composeRule.onNodeWithText("请输入提现账号").assertIsDisplayed()
    }

    @Test
    fun 打开佣金划转弹层() {
        stubPage()
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                InviteScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.stat.availableBalance == 56_700 }

        composeRule.onNodeWithText("佣金划转").performClick()

        composeRule.onNodeWithText("确认划转").assertIsDisplayed()
    }
}
