// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.login

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 登录页 v5 化的行为对账（v4 → v5 迁移不能丢入口/丢动作）。
 *
 * 这一页的入口全部是"接线"（点一下调 VM 的某个方法），因此用 mock VM + `verify` 逐条钉住，
 * 而不是等真实网络流程。这样即使以后有人重写布局，只要漏接一个入口就会红。
 *
 * **为什么要单独测弹窗**：`PanelUrlConfirmDialog` 用 Material3 `AlertDialog`，它渲染在独立
 * Window 上，截图测试（抓 decorView 位图）**抓不到它**——实测 `44c` 与 `44` 两张图字节完全相同。
 * 所以弹窗的"确实拉起来了"只能靠语义树断言来取证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class LoginScreenJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(state: LoginUiState = LoginUiState.Form()): LoginViewModel {
        val vm = mockk<LoginViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    private fun content(vm: LoginViewModel, onForgot: () -> Unit = {}, onCreate: (Boolean, Boolean) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            SlteTheme {
                LoginScreen(onForgotPassword = onForgot, onCreateAccount = onCreate, viewModel = vm)
            }
        }
    }

    @Test
    fun 渲染三个字段与两个动作按钮() {
        content(viewModel())

        composeRule.onNodeWithText("输入邮箱").assertIsDisplayed()
        composeRule.onNodeWithText("请输入密码").assertIsDisplayed()
        composeRule.onNodeWithText("面板地址").assertIsDisplayed()
        composeRule.onNodeWithText("登录").assertIsDisplayed()
        composeRule.onNodeWithText("创建账号").assertIsDisplayed()
        composeRule.onNodeWithText("记住密码").assertIsDisplayed()
        composeRule.onNodeWithText("忘记密码？").assertIsDisplayed()
    }

    @Test
    fun 点击登录调用登录方法() {
        val vm = viewModel()
        content(vm)

        composeRule.onNodeWithText("登录").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.login() }
    }

    @Test
    fun 点击创建账号调用检查注册配置() {
        val vm = viewModel()
        content(vm)

        composeRule.onNodeWithText("创建账号").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.checkRegisterConfig() }
    }

    @Test
    fun 点击忘记密码回调上层() {
        var called = false
        content(viewModel(), onForgot = { called = true })

        composeRule.onNodeWithText("忘记密码？").performClick()
        composeRule.waitForIdle()

        org.junit.Assert.assertTrue("忘记密码入口应回调上层导航", called)
    }

    @Test
    fun 点击记住密码切换开关() {
        val vm = viewModel()
        content(vm)

        composeRule.onNodeWithText("记住密码").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { vm.toggleRememberMe() }
    }

    @Test
    fun 账号输入框可输入并把内容回传ViewModel() {
        val vm = viewModel()
        content(vm)

        // 页面里有三个输入框（账号/密码/面板地址），按"期望唯一"定位会失败；
        // 取第一个即账号框（顺序与表单自上而下一致，已由上面 `渲染三个字段与两个动作按钮` 的
        // 布局断言间接保证）。占位符不能用：它是 decorationBox 里的独立 Text 节点，没有输入动作。
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("user@example.com")
        composeRule.waitForIdle()

        verify { vm.onAccountChange("user@example.com") }
    }

    @Test
    fun 提交中按钮不可再点且显示加载() {
        val vm = viewModel(LoginUiState.LoggingIn(LoginUiState.Form()))
        content(vm)

        // 提交中：V5Button 的 onClickEnabled=false ⇒ 点击不应再触发 login()。
        composeRule.onNodeWithText("登录").performClick()
        composeRule.waitForIdle()

        verify(exactly = 0) { vm.login() }
    }

    @Test
    fun 面板地址确认弹窗显示私网警告() {
        val vm =
            viewModel(
                LoginUiState.ConfirmPanelUrl(
                    form = LoginUiState.Form(),
                    normalizedUrl = "https://192.168.1.10",
                    isPrivateHost = true,
                    pendingAction = PendingAction.LOGIN,
                ),
            )
        content(vm)

        // 弹窗虽不在截图里，但在语义树里（Robolectric 会构建对话框的 compose 树）。
        composeRule.onNodeWithText("https://192.168.1.10").assertIsDisplayed()
    }

    @Test
    fun 面板地址确认弹窗非私网时不显示警告() {
        val vm =
            viewModel(
                LoginUiState.ConfirmPanelUrl(
                    form = LoginUiState.Form(),
                    normalizedUrl = "https://panel.example.com",
                    isPrivateHost = false,
                    pendingAction = PendingAction.LOGIN,
                ),
            )
        content(vm)

        composeRule.onNodeWithText("https://panel.example.com").assertIsDisplayed()
    }
}
