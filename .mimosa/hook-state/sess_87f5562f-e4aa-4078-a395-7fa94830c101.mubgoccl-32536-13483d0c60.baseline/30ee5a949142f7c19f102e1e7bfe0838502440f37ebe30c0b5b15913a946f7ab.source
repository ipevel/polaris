package com.slte.app.ui.screen.register

import com.slte.app.R
import com.slte.app.data.repository.AuthRepository
import com.slte.app.domain.model.EmailCodePurpose
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.domain.usecase.CountdownUseCase
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val countdownUseCase = mockk<CountdownUseCase>(relaxed = true)

    private fun viewModel(): RegisterViewModel {
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        coEvery { authRepository.sendEmailCode(any(), any()) } returns Result.success(Unit)
        return RegisterViewModel(authRepository, countdownUseCase)
    }

    @Test
    fun `邮箱为空时不发验证码`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.sendVerificationCode()
        advanceUntilIdle()

        assertEquals(
            R.string.error_email_required,
            (vm.uiState.value as RegisterUiState.Error).messageRes,
        )
        coVerify(exactly = 0) { authRepository.sendEmailCode(any(), any()) }
    }

    @Test
    fun `验证码用途为注册`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onEmailChange("a@b.c")
        vm.sendVerificationCode()
        advanceUntilIdle()

        coVerify { authRepository.sendEmailCode("a@b.c", EmailCodePurpose.REGISTER) }
    }

    @Test
    fun `注册必填项逐级校验`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.initConfig(emailVerifyEnabled = true, inviteForceEnabled = true)

        vm.register()
        advanceUntilIdle()
        assertEquals(R.string.error_email_required, (vm.uiState.value as RegisterUiState.Error).messageRes)

        vm.onEmailChange("a@b.c")
        vm.register()
        advanceUntilIdle()
        assertEquals(R.string.error_password_required, (vm.uiState.value as RegisterUiState.Error).messageRes)

        vm.onPasswordChange("pw123456")
        vm.register()
        advanceUntilIdle()
        assertEquals(R.string.error_code_required, (vm.uiState.value as RegisterUiState.Error).messageRes)

        vm.onCodeChange("123456")
        vm.register()
        advanceUntilIdle()
        assertEquals(R.string.error_invite_required, (vm.uiState.value as RegisterUiState.Error).messageRes)

        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any()) }
    }

    @Test
    fun `未启用邮箱验证时不要求验证码`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()
        vm.initConfig(emailVerifyEnabled = false, inviteForceEnabled = false)

        vm.onEmailChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.register()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is RegisterUiState.RegisterSuccess)
        coVerify { authRepository.register("a@b.c", "pw123456", null, null) }
    }

    @Test
    fun `注册失败保留输入并提示`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.onEmailChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.register()
        advanceUntilIdle()

        val state = vm.uiState.value as RegisterUiState.Error
        assertEquals("a@b.c", state.form.email)
        assertTrue(state.messageRes != 0)
    }

    @Test
    fun `取消加载回到表单态且保留用户输入`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.success(User(id = "1", displayName = "测试"))
        val vm = viewModel()

        vm.onEmailChange("a@b.c")
        vm.onPasswordChange("pw123456")
        vm.cancelLoading()

        val state = vm.uiState.value as RegisterUiState.Form
        assertEquals("a@b.c", state.email)
        assertEquals("pw123456", state.password)
    }
}
