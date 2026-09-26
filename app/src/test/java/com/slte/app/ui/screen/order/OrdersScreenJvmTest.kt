// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.order

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slte.app.data.remote.api.dto.OrderInfoDto
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class OrdersScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val api = FakeAuthApi()
    private val viewModel = OrdersViewModel(OrderRepository(api))

    private fun order(id: Int) = OrderInfoDto(
        id = id,
        tradeNo = "TN-$id",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = 3,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )

    /** 待支付订单（`status = 0` ⇒ `OrderStatus.PENDING`）。 */
    private fun pendingOrder(id: Int) = OrderInfoDto(
        id = id,
        tradeNo = "TN-$id",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = 0,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )

    private fun content() {
        composeRule.setContent {
            SlteTheme {
                OrdersScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun 有订单时渲染订单行() {
        api.orders = listOf(order(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("TN-1").assertIsDisplayed()
    }

    @Test
    fun 无订单时显示空态() {
        api.orders = emptyList()
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.phase == ContentPhase.Idle }

        composeRule.onNodeWithText("暂无订单").assertIsDisplayed()
    }

    @Test
    fun 加载失败显示错误并可重试() {
        api.ordersError = java.io.IOException("boom")
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.errorMessageRes != null }

        composeRule.onNodeWithText("重试").assertIsDisplayed()

        api.ordersError = null
        api.orders = listOf(order(2))
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("TN-2").assertIsDisplayed()
    }

    @Test
    fun 订单明细渲染套餐名金额日期与订单号() {
        api.ordersError = null
        api.orders = listOf(order(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("进阶套餐").assertIsDisplayed()
        composeRule.onNodeWithText("订单号").assertIsDisplayed()
        composeRule.onNodeWithText("创建于").assertIsDisplayed()
    }

    /**
     * 待支付订单才有「取消订单 / 继续支付」两个按钮；已完成订单没有。
     *
     * 这是迁移最容易丢的一组入口（两个按钮在 `if (isPending)` 分支里），必须钉住。
     */
    @Test
    fun 待支付订单显示取消与支付按钮而已完成订单不显示() {
        api.ordersError = null
        api.orders = listOf(pendingOrder(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("取消").assertIsDisplayed()
        composeRule.onNodeWithText("支付").assertIsDisplayed()
        composeRule.onNodeWithText("待支付").assertIsDisplayed()
    }

    @Test
    fun 已完成订单不显示操作按钮() {
        api.ordersError = null
        api.orders = listOf(order(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertDoesNotExist()
        composeRule.onNodeWithText("支付").assertDoesNotExist()
    }

    @Test
    fun 同一订单号重复只渲染一行() {
        api.ordersError = null
        api.orders = listOf(order(1), order(1))
        viewModel.enterAndRefresh()
        content()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        assertEquals(1, composeRule.onAllNodesWithText("TN-1").fetchSemanticsNodes().size)
    }
}
