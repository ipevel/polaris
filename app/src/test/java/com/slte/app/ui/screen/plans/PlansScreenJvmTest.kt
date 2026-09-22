// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.remote.api.dto.PlanInfoDto
import com.slte.app.data.repository.OrderRepository
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
class PlansScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val repository = OrderRepository(api)
    private val viewModel = PlansViewModel(repository)
    private val purchaseViewModel =
        PurchaseViewModel(
            couponChecker = CouponChecker(repository),
            paymentLoader = OrderPaymentLoader(repository),
            poller = OrderPaymentPoller(repository),
            orderCreator = OrderCreator(repository),
            paymentCheckout = PaymentCheckout(repository),
        )

    private fun plan(id: Int) = PlanInfoDto(
        id = id,
        name = "进阶套餐",
        monthPrice = 5_000,
        transferEnable = 400,
        show = true,
    )

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                PlansScreen(
                    onBack = {},
                    viewModel = viewModel,
                    purchaseViewModel = purchaseViewModel,
                )
            }
        }
    }

    @Test
    fun 渲染套餐卡并可打开购买弹层() {
        api.plans = listOf(plan(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.isNotEmpty() }

        composeRule.onNodeWithText("进阶套餐").assertIsDisplayed()
        composeRule.onNode(hasText("订阅") and hasClickAction()).performClick()

        composeRule.onNodeWithText("有优惠券？").assertIsDisplayed()
    }

    @Test
    fun 加载失败显示错误态() {
        api.plansError = java.io.IOException("boom")
        viewModel.retry()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.errorMessageRes != null }

        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }
}
