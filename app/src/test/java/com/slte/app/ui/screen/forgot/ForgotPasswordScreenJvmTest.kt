// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.forgot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.theme.SlteTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 忘记密码页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 钉住：字段渲染、重置提交、返回登录、发码、倒计时形态、重置成功回调、重置中不可重复提交。
 * 真实重置流程由既有 `ForgotPasswordViewModelTest` 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class ForgotPasswordScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(state: ForgotPasswordUiState = ForgotPasswordUiState.Form()): ForgotPasswordViewModel {
        val vm = mockk<ForgotPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    private fun content(
        vm: ForgotPasswordViewModel,
        onBackToLogin: () -> Unit = {},
        onResetSuccess: () -> Unit = {},
    ) {
        composeRule.setContent {
            SlteTheme {
                ForgotPasswordScreen(
                    onBackToLogin = onBackToLogin,
                    onResetSuccess = onResetSuccess,
                    viewModel = vm,
                )
            }
        }
    }

    @Test
    fun 渲染账号验证码新密码与重置按钮() {
        content(viewModel())

        composeRule.onNodeWithText("输入邮箱").assertIsDisplayed()
        composeRule.onNodeWithText("发送").assertIsDisplayed()
        // 「重置密码」同时是卡片标题与按钮文案，所以断言"至少出现一次"。
        assertTrue(composeRule.onAllNodesWithText("重置密码").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("返回登录").assertIsDisplayed()
    }

    @Test
    fun 点击重置密码调用重置() {
        val vm = viewModel()
        content(vm)

        // 卡片标题与按钮文案**都是「重置密码」**，所以列表里有两个节点：
        // 第 0 个是标题、第 1 个才是按钮。用下标取，不要用 onFirst（那是标题，点它无效）。
        val buttons = composeRule.onAllNodesWithText("重置密码")
        assertEquals(2, buttons.fetchSemanticsNodes().size)
        buttons[1].performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.resetPassword() }
    }

    @Test
    fun 点击发送调用发码() {
        val vm = viewModel()
        content(vm)

        composeRule.onNodeWithText("发送").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.sendVerificationCode() }
    }

    @Test
    fun 点击返回登录回调上层() {
        var called = false
        content(viewModel(), onBackToLogin = { called = true })

        composeRule.onNodeWithText("返回登录").performClick()
        composeRule.waitForIdle()

        assertTrue("返回登录入口应回调上层导航", called)
    }

    @Test
    fun 倒计时期间显示秒数且不显示发送按钮() {
        val vm = viewModel(ForgotPasswordUiState.Countdown(ForgotPasswordUiState.Form(), seconds = 30))
        content(vm)

        composeRule.onNodeWithText("发送").assertDoesNotExist()
        composeRule.onNodeWithText("30s").assertIsDisplayed()
    }

    /** 重置中按钮不可再点（onClickEnabled=false），避免重复提交。 */
    @Test
    fun 重置中按钮不可再点() {
        val vm = viewModel(ForgotPasswordUiState.Resetting(ForgotPasswordUiState.Form()))
        content(vm)

        val buttons = composeRule.onAllNodesWithText("重置密码")
        assertEquals(2, buttons.fetchSemanticsNodes().size)
        buttons[1].performClick()
        composeRule.waitForIdle()

        verify(exactly = 0) { vm.resetPassword() }
    }

    /** 重置成功后回调上层（由页面里的 `LaunchedEffect` 触发）。 */
    @Test
    fun 重置成功回调上层导航() {
        var success = false
        content(viewModel(ForgotPasswordUiState.ResetSuccess(ForgotPasswordUiState.Form())), onResetSuccess = { success = true })
        composeRule.waitForIdle()

        assertTrue("重置成功应回调上层导航", success)
    }

    @Test
    fun 邮箱字段可输入并回传ViewModel() {
        val vm = viewModel()
        content(vm)

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("me@example.com")
        composeRule.waitForIdle()

        verify { vm.onEmailChange("me@example.com") }
    }
}
