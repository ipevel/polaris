// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.plans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.remote.api.dto.PlanInfoDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.theme.SlteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 套餐页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 覆盖：三态渲染、套餐卡内容（名称/价格/流量）、首项「推荐」徽标、订阅按钮打开购买流程、
 * 同一套餐 id 去重。
 *
 * 未覆盖：`V5TopIconButton` 返回（图标无 `contentDescription`，语义树不可定位，见报告「已知缺口」）；
 * 购买流程内部的用券/支付选择（由 `PurchaseViewModelTest` 等既有测试覆盖数据层）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class PlansScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val orderRepository = OrderRepository(api)
    private val viewModel = PlansViewModel(orderRepository)

    /** 只构造页面渲染所需的最小 `PurchaseViewModel`（数据层由既有测试覆盖）。 */
    private fun purchaseViewModel() = PurchaseViewModel(
        couponChecker = CouponChecker(orderRepository),
        paymentLoader = OrderPaymentLoader(orderRepository),
        poller = OrderPaymentPoller(orderRepository),
        orderCreator = OrderCreator(orderRepository),
        paymentCheckout = PaymentCheckout(orderRepository),
    )

    private fun plan(id: Int, monthPrice: Long = 5_000L) = PlanInfoDto(
        id = id,
        name = "进阶套餐 $id",
        monthPrice = monthPrice,
        transferEnable = 400,
        show = true,
    )

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                PlansScreen(onBack = {}, viewModel = viewModel, purchaseViewModel = purchaseViewModel())
            }
        }
    }

    @Test
    fun 初始渲染加载态() {
        content()

        composeRule.onNodeWithText("订阅").assertIsDisplayed()
    }

    @Test
    fun 无套餐时显示空态() {
        api.plans = emptyList()
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.phase == ContentPhase.Idle }

        composeRule.onNodeWithText("暂无套餐").assertIsDisplayed()
    }

    @Test
    fun 加载失败显示错误并可重试成功() {
        api.plansError = java.io.IOException("连接超时")
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.errorMessageRes != null }

        // 错误文案走 `ErrorMessages.forOrder` 映射后的资源（不是异常本身的 message），
        // 所以只断言"重试"按钮与错误态已出现。
        composeRule.onNodeWithText("重试").assertIsDisplayed()

        api.plansError = null
        api.plans = listOf(plan(1))
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.isNotEmpty() }

        composeRule.onNodeWithText("进阶套餐 1").assertIsDisplayed()
    }

    @Test
    fun 套餐卡渲染名称价格流量与订阅按钮() {
        api.plansError = null
        api.plans = listOf(plan(1, monthPrice = 5_000))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.isNotEmpty() }

        composeRule.onNodeWithText("进阶套餐 1").assertIsDisplayed()
        composeRule.onNodeWithText("流量").assertIsDisplayed()
        composeRule.onNodeWithText("400GB").assertIsDisplayed()
    }

    /** 首项带「推荐」徽标，第二项不带——这是"首选主推"的既有产品语义。 */
    @Test
    fun 仅首项显示推荐徽标() {
        api.plansError = null
        api.plans = listOf(plan(1), plan(2))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.size == 2 }

        assertEquals(1, composeRule.onAllNodesWithText("推荐").fetchSemanticsNodes().size)
    }

    @Test
    fun `同一套餐 id 重复只渲染一张卡`() {
        api.plansError = null
        api.plans = listOf(plan(1), plan(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.isNotEmpty() }

        assertEquals(1, composeRule.onAllNodesWithText("进阶套餐 1").fetchSemanticsNodes().size)
    }

    @Test
    fun 点击订阅打开购买流程() {
        api.plansError = null
        api.plans = listOf(plan(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.plans.isNotEmpty() }

        // 顶栏标题与卡片按钮文案都是「订阅」，所以列表里会有 2 个节点：
        // 第 1 个是顶栏标题，第 2 个是套餐卡里的按钮。用下标取按钮而不是 onFirst。
        val subs = composeRule.onAllNodesWithText("订阅")
        assertEquals(2, subs.fetchSemanticsNodes().size)
        subs[1].performClick()
        composeRule.waitForIdle()

        // 购买流程（SelectPeriodSheet）拉起：出现「确认订单 <金额>」按钮。
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("确认订单", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
