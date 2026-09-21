package com.slte.app.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.local.ApiUrlStore
import com.slte.app.data.remote.config.ConfigValidation
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

        val panelUrl: String = "",
        val backendType: String = "",
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
    private val apiUrlStore: ApiUrlStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(initialForm())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var loginJob: Job? = null
    private var registerConfigJob: Job? = null

    /** 面板地址初始值：仅回显已保存的用户地址，App 不预填任何内置地址 */
    private fun initialForm() = LoginUiState.Form(
        panelUrl = apiUrlStore.currentUrl.orEmpty(),
        backendType = apiUrlStore.backendType,
    )

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

                    panelUrl = f.panelUrl,
                    backendType = f.backendType,
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

    fun onPanelUrlChange(value: String) {
        val f = currentForm()
        _uiState.value = f.copy(panelUrl = value)
    }

    fun onBackendTypeChange(type: String) {
        val f = currentForm()
        _uiState.value = f.copy(backendType = type)
    }

    fun toggleRememberMe() {
        val f = currentForm()
        val newValue = !f.rememberMe
        if (!newValue) {
            authRepository.clearCredentials()
        }
        _uiState.value = f.copy(rememberMe = newValue)
    }

    /** 校验面板地址：必填且须为合法 https 地址；非法时返回错误文案资源 */
    private fun panelUrlErrorRes(raw: String): Int? = when {
        raw.isBlank() -> R.string.login_url_required
        ConfigValidation.normalizePanelUrl(raw) == null -> R.string.login_url_invalid
        else -> null
    }

    /** 保存面板地址和后端类型（与账号密码一起在提交时写入持久化，随后请求立即生效） */
    private suspend fun persistPanelSettings(raw: String, backendType: String) {
        apiUrlStore.setUrl(ConfigValidation.normalizePanelUrl(raw))
        apiUrlStore.setBackendType(backendType)
    }

    fun dismissError() {
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,

                panelUrl = f.panelUrl,
                backendType = f.backendType,
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
        panelUrlErrorRes(f.panelUrl)?.let { resId ->
            _uiState.value = LoginUiState.Error(f, resId)
            return
        }

        _uiState.value = LoginUiState.LoggingIn(f)
        loginJob?.cancel()
        loginJob =
            viewModelScope.launch {
                val f2 = currentForm()
                persistPanelSettings(f2.panelUrl, f2.backendType)
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
        panelUrlErrorRes(f.panelUrl)?.let { resId ->
            _uiState.value = LoginUiState.Error(f, resId)
            return
        }
        _uiState.value = LoginUiState.CheckingRegisterConfig(f)
        registerConfigJob?.cancel()
        registerConfigJob =
            viewModelScope.launch {
                val f2 = currentForm()
                persistPanelSettings(f2.panelUrl, f2.backendType)
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

                panelUrl = f.panelUrl,
                backendType = f.backendType,
            )
    }

    fun onNavigatedToLoginSuccess() {
        val f = currentForm()
        _uiState.value =
            LoginUiState.Form(
                account = f.account,
                password = f.password,
                rememberMe = f.rememberMe,

                panelUrl = f.panelUrl,
                backendType = f.backendType,
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

                panelUrl = f.panelUrl,
                backendType = f.backendType,
            )
    }
}
