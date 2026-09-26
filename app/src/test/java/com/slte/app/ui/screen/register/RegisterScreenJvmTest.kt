// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.register

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 注册页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 钉住四类入口：字段渲染（随"邮箱验证开关"变化）、注册提交、返回登录、倒计时按钮形态。
 * 用 mock VM + `verify` 断言接线；真实注册流程由既有 `RegisterViewModelTest` 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class RegisterScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(state: RegisterUiState = RegisterUiState.Form()): RegisterViewModel {
        val vm = mockk<RegisterViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    private fun content(
        vm: RegisterViewModel,
        emailVerify: Boolean = false,
        inviteForce: Boolean = false,
        onBackToLogin: () -> Unit = {},
    ) {
        composeRule.setContent {
            SlteTheme {
                RegisterScreen(
                    emailVerifyEnabled = emailVerify,
                    inviteForceEnabled = inviteForce,
                    onBackToLogin = onBackToLogin,
                    viewModel = vm,
                )
            }
        }
    }

    @Test
    fun 渲染账号密码邀请码与注册按钮() {
        content(viewModel())

        composeRule.onNodeWithText("输入邮箱").assertIsDisplayed()
        composeRule.onNodeWithText("请输入密码").assertIsDisplayed()
        composeRule.onNodeWithText("创建账号").assertIsDisplayed()
        composeRule.onNodeWithText("返回登录").assertIsDisplayed()
    }

    /** 邮箱验证关闭时不应出现验证码字段；开启时必须出现。这是「面板下发配置驱动 UI」的核心入口。 */
    @Test
    fun 邮箱验证关闭时不显示验证码字段() {
        content(viewModel(), emailVerify = false)

        composeRule.onNodeWithText("发送").assertDoesNotExist()
    }

    @Test
    fun 邮箱验证开启时显示验证码与发送按钮() {
        content(viewModel(), emailVerify = true)

        composeRule.onNodeWithText("发送").assertIsDisplayed()
    }

    /** 邀请码"必填 / 选填"两态文案不同（面板下发的 inviteForceEnabled 驱动）。 */
    @Test
    fun 邀请码必填与选填显示不同标签() {
        content(viewModel(), inviteForce = true)

        composeRule.onNodeWithText("邀请码（必填）").assertIsDisplayed()
    }

    @Test
    fun 点击创建账号调用注册() {
        val vm = viewModel()
        content(vm)

        composeRule.onNodeWithText("注册").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.register() }
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
    fun 点击发送调用发码() {
        val vm = viewModel()
        content(vm, emailVerify = true)

        composeRule.onNodeWithText("发送").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.sendVerificationCode() }
    }

    /** 倒计时期间槽位换成等宽数字条，不再显示「发送验证码」按钮（与 v4 行为一致）。 */
    @Test
    fun 倒计时期间显示秒数且不显示发送按钮() {
        val vm = viewModel(RegisterUiState.Countdown(RegisterUiState.Form(), seconds = 42))
        content(vm, emailVerify = true)

        composeRule.onNodeWithText("发送").assertDoesNotExist()
        composeRule.onNodeWithText("42s").assertIsDisplayed()
    }

    /** 注册中按钮不可再点（onClickEnabled=false），避免重复提交。 */
    @Test
    fun 注册中按钮不可再点() {
        val vm = viewModel(RegisterUiState.Registering(RegisterUiState.Form()))
        content(vm)

        composeRule.onNodeWithText("注册").performClick()
        composeRule.waitForIdle()

        verify(exactly = 0) { vm.register() }
    }

    @Test
    fun 邮箱字段可输入并回传ViewModel() {
        val vm = viewModel()
        content(vm)

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("new@example.com")
        composeRule.waitForIdle()

        verify { vm.onEmailChange("new@example.com") }
    }
}
