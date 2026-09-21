package com.slte.app.ui.screen.login

import com.slte.app.R
import com.slte.app.data.local.ApiUrlStore
import com.slte.app.data.repository.AuthRepository
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>(relaxed = true)

    private val apiUrlStore = mockk<ApiUrlStore>(relaxed = true)

    private fun viewModel(
        savedEmail: String? = null,
        savedPassword: String? = null,
    ): LoginViewModel {
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { authRepository.savedEmail() } returns savedEmail
        every { authRepository.savedPassword() } returns savedPassword
        every { apiUrlStore.currentUrl } returns null
        return LoginViewModel(authRepository, apiUrlStore)
    }

    @Test
    fun `账号为空时不发请求`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.login()
        advanceUntilIdle()

        assertEquals(LoginUiState.Error(LoginUiState.Form(), R.string.error_email_required), vm.uiState.value)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `密码为空时报密码必填`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onAccountChange("a@b.c")
        vm.login()
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.Error
        assertEquals(R.string.error_password_required, state.messageRes)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `登录成功且勾选记住密码时保存凭证`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.login("a@b.c", "pw123456") } returns Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("https://panel.example.com")
        vm.toggleRememberMe()
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is LoginUiState.LoginSuccess)
        verify { authRepository.saveCredentials("a@b.c", "pw123456") }
    }

    @Test
    fun `未勾选记住密码时清除本地凭证`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.login("a@b.c", "pw123456") } returns Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("https://panel.example.com")
        vm.login()
        advanceUntilIdle()

        verify { authRepository.clearCredentials() }
        verify(exactly = 0) { authRepository.saveCredentials(any(), any()) }
    }

    @Test
    fun `登录失败保留表单并提示`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.login(any(), any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("https://panel.example.com")
        vm.login()
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.Error
        assertEquals("失败后表单内容应保留", "a@b.c", state.form.account)
        assertTrue(state.messageRes != 0)
    }

    @Test
    fun `已保存凭证时预填账号并勾选记住密码`() = runTest(mainRule.dispatcher) {
        val vm = viewModel(savedEmail = "a@b.c", savedPassword = "pw123456")
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.Form
        assertEquals("a@b.c", state.account)
        assertEquals("pw123456", state.password)
        assertTrue(state.rememberMe)
    }

    @Test
    fun `创建账号前拉取注册配置`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.fetchRegisterConfig() } returns
            Result.success(RegisterConfig(emailVerifyEnabled = true, inviteForceEnabled = false))
        val vm = viewModel()

        vm.onPanelUrlChange("https://panel.example.com")
        vm.checkRegisterConfig()
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.RegisterConfigReady
        assertTrue(state.config.emailVerifyEnabled)
    }

    @Test
    fun `登录中重复点击只发一次请求`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.login(any(), any()) } returns Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("https://panel.example.com")
        vm.login()
        vm.login()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.login(any(), any()) }
    }

    @Test
    fun `面板地址未填时不发请求`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.login()
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.Error
        assertEquals(R.string.login_url_required, state.messageRes)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
        coVerify(exactly = 0) { apiUrlStore.setUrl(any()) }
    }

    @Test
    fun `面板地址非法时不发请求`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("bad url with spaces")
        vm.login()
        advanceUntilIdle()

        val state = vm.uiState.value as LoginUiState.Error
        assertEquals(R.string.login_url_invalid, state.messageRes)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
        coVerify(exactly = 0) { apiUrlStore.setUrl(any()) }
    }

    @Test
    fun `登录时保存规范化后的面板地址`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.login("a@b.c", "pw123456") } returns Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()

        vm.onAccountChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.onPanelUrlChange("https://my.panel.dev/")
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is LoginUiState.LoginSuccess)
        coVerify { apiUrlStore.setUrl("https://my.panel.dev") }
    }
}
