package com.slte.app.ui.screen.order

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.support.FakeAuthApi
import com.slte.app.ui.theme.SlteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdersScreenTest {
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

    @Test
    fun 有订单时渲染订单行() {
        api.orders = listOf(order(1))
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                OrdersScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("TN-1").assertIsDisplayed()
    }

    @Test
    fun 无订单时显示空态() {
        api.orders = emptyList()
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                OrdersScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.phase == com.slte.app.ui.ContentPhase.Idle }

        composeRule.onNodeWithText("暂无订单").assertIsDisplayed()
    }

    @Test
    fun 加载失败显示错误并可重试() {
        api.ordersError = java.io.IOException("boom")
        viewModel.enterAndRefresh()

        composeRule.setContent {
            SlteTheme {
                OrdersScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.data.value.errorMessageRes != null }

        composeRule.onNodeWithText("重试").assertIsDisplayed()

        api.ordersError = null
        api.orders = listOf(order(2))
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(5_000) { viewModel.data.value.orders.isNotEmpty() }

        composeRule.onNodeWithText("TN-2").assertIsDisplayed()
    }
}
