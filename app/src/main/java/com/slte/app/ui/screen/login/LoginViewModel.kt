package com.slte.app.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.AuthRepository
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data class Form(
        val account: String = "",
        val password: String = "",
        val rememberMe: Boolean = false,
    ) : LoginUiState

    data class LoggingIn(
        val form: Form,
    ) : LoginUiState

    data class CheckingRegisterConfig(
        val form: Form,
    ) : LoginUiState

    data class LoginSuccess(
        val form: Form,
        val user: User,
    ) : LoginUiState

    data class RegisterConfigReady(
        val form: Form,
        val config: RegisterConfig,
    ) : LoginUiState

    data class Error(
        val form: Form,
        val messageRes: Int,
    ) : LoginUiState
}

@HiltViewModel
class LoginViewModel
@Inject
constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Form())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var loginJob: Job? = null
    private var registerConfigJob: Job? = null

    private fun currentForm() = when (val s = _uiState.value) {
        is LoginUiState.Form -> s
        is LoginUiState.LoggingIn -> s.form
        is LoginUiState.CheckingRegisterConfig -> s.form
        is LoginUiState.LoginSuccess -> s.form
        is LoginUiState.RegisterConfigReady -> s.form
        is LoginUiState.Error -> s.form
    }

    private val isLoading: Boolean
        get() =
            _uiState.value is LoginUiState.LoggingIn ||
                _uiState.value is LoginUiState.CheckingRegisterConfig

    private fun loadSavedCredentials() {
        val savedEmail = authRepository.savedEmail()
        if (savedEmail != null) {
            val f = currentForm()
            _uiState.value =
                LoginUiState.Form(
                    account = savedEmail,
                    password = authRepository.savedPassword() ?: "",
                    rememberMe = true,
                )
        }
    }

    init {
        loadSavedCredentials()

        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                if (state is SessionState.LoggedOut) {
                    loadSavedCredentials()
                }
            }
        }
    }

    fun onAccountChange(value: String) {
        val f = currentForm()
        _uiState.value = f.copy(account = value)
    }

    fun onPasswordChange(value: String) {
        val f = currentForm()
        _uiState.value = f.copy(password = value)
    }

    fun toggleRememberMe() {
        val f = currentForm()
        val newValue = !f.rememberMe
        if (!newValue) {
            authRepository.clearCredentials()
        }
        _uiState.value = f.copy(rememberMe = newValue)
    }

    fun dismissError() {
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,
            )
    }

    fun login() {
        if (isLoading) return
        val f = currentForm()
        if (f.account.isBlank()) {
            _uiState.value = LoginUiState.Error(f, R.string.error_email_required)
            return
        }
        if (f.password.isBlank()) {
            _uiState.value = LoginUiState.Error(f, R.string.error_password_required)
            return
        }

        _uiState.value = LoginUiState.LoggingIn(f)
        loginJob?.cancel()
        loginJob =
            viewModelScope.launch {
                val f2 = currentForm()
                val result = authRepository.login(f2.account.trim(), f2.password)
                result.fold(
                    onSuccess = { user ->

                        if (f2.rememberMe) {
                            authRepository.saveCredentials(f2.account.trim(), f2.password)
                        } else {
                            authRepository.clearCredentials()
                        }
                        _uiState.value = LoginUiState.LoginSuccess(f2, user)
                    },
                    onFailure = { e ->
                        val f3 = currentForm()
                        val resId =
                            ErrorMessages.forLogin(e)
                        _uiState.value = LoginUiState.Error(f3, resId)
                    },
                )
            }
    }

    fun checkRegisterConfig() {
        if (isLoading) return
        val f = currentForm()
        _uiState.value = LoginUiState.CheckingRegisterConfig(f)
        registerConfigJob?.cancel()
        registerConfigJob =
            viewModelScope.launch {
                val f2 = currentForm()
                val result = authRepository.fetchRegisterConfig()
                result.fold(
                    onSuccess = { config ->
                        _uiState.value = LoginUiState.RegisterConfigReady(f2, config)
                    },
                    onFailure = { e ->
                        val f3 = currentForm()
                        val resId =
                            ErrorMessages.forRegister(e)
                        _uiState.value = LoginUiState.Error(f3, resId)
                    },
                )
            }
    }

    fun onNavigatedToRegister() {
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,
            )
    }

    fun onNavigatedToLoginSuccess() {
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,
            )
    }

    fun cancelLoading() {
        loginJob?.cancel()
        registerConfigJob?.cancel()
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,
            )
    }
}
