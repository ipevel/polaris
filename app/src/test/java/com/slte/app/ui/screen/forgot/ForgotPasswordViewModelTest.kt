// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.forgot

import com.slte.app.R
import com.slte.app.data.repository.AuthRepository
import com.slte.app.domain.model.EmailCodePurpose
import com.slte.app.domain.model.SessionState
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

class ForgotPasswordViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val countdownUseCase = mockk<com.slte.app.domain.usecase.CountdownUseCase>(relaxed = true)

    private fun viewModel(): ForgotPasswordViewModel {
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        coEvery { authRepository.sendEmailCode(any(), any()) } returns Result.success(Unit)
        return ForgotPasswordViewModel(authRepository, countdownUseCase)
    }

    @Test
    fun `邮箱为空时不发验证码`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.sendVerificationCode()
        advanceUntilIdle()

        val state = vm.uiState.value as ForgotPasswordUiState.Error
        assertEquals(R.string.error_email_required, state.messageRes)
        coVerify(exactly = 0) { authRepository.sendEmailCode(any(), any()) }
    }

    @Test
    fun `验证码用途为忘记密码`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onEmailChange("a@b.c")
        vm.sendVerificationCode()
        advanceUntilIdle()

        coVerify { authRepository.sendEmailCode("a@b.c", EmailCodePurpose.FORGOT_PASSWORD) }
    }

    @Test
    fun `重置时验证码与新密码必填`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onEmailChange("a@b.c")
        vm.resetPassword()
        advanceUntilIdle()
        assertEquals(
            R.string.error_code_required,
            (vm.uiState.value as ForgotPasswordUiState.Error).messageRes,
        )

        vm.onCodeChange("123456")
        vm.resetPassword()
        advanceUntilIdle()
        assertEquals(
            R.string.error_new_password_required,
            (vm.uiState.value as ForgotPasswordUiState.Error).messageRes,
        )
        coVerify(exactly = 0) { authRepository.forgotPassword(any(), any(), any()) }
    }

    @Test
    fun `重置成功进入成功态`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.forgotPassword(any(), any(), any()) } returns Result.success(Unit)
        val vm = viewModel()

        vm.onEmailChange("a@b.c")
        vm.onCodeChange("123456")
        vm.onNewPasswordChange("newpass1234")
        vm.resetPassword()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ForgotPasswordUiState.ResetSuccess)
    }

    @Test
    fun `重置失败保留输入并提示`() = runTest(mainRule.dispatcher) {
        coEvery { authRepository.forgotPassword(any(), any(), any()) } returns Result.failure(java.io.IOException("boom"))
        val vm = viewModel()

        vm.onEmailChange("a@b.c")
        vm.onCodeChange("123456")
        vm.onNewPasswordChange("newpass1234")
        vm.resetPassword()
        advanceUntilIdle()

        val state = vm.uiState.value as ForgotPasswordUiState.Error
        assertEquals("a@b.c", state.form.email)
        assertEquals("123456", state.form.verificationCode)
        assertTrue(state.messageRes != 0)
    }
}
