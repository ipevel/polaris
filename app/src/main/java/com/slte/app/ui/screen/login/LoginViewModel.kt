package com.slte.app.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.local.ApiUrlStore
import com.slte.app.data.remote.config.ConfigValidation
import com.slte.app.data.repository.AuthRepository
import com.slte.app.di.IoDispatcher
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

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

    /** 用户确认连接到新的/非默认的面板地址 */
    data class ConfirmPanelUrl(
        val form: Form,
        val normalizedUrl: String,
        val isPrivateHost: Boolean,
        /** 确认后要执行的动作 */
        val pendingAction: PendingAction,
    ) : LoginUiState

    data class Error(
        val form: Form,
        val messageRes: Int,
    ) : LoginUiState
}

enum class PendingAction { LOGIN, CHECK_REGISTER_CONFIG }

@HiltViewModel
class LoginViewModel
@Inject
constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val authRepository: AuthRepository,
    private val apiUrlStore: ApiUrlStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(initialForm())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var loginJob: Job? = null
    private var registerConfigJob: Job? = null
    private var probeJob: Job? = null

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
        is LoginUiState.ConfirmPanelUrl -> s.form
        is LoginUiState.Error -> s.form
    }

    private val isLoading: Boolean
        get() =
            _uiState.value is LoginUiState.LoggingIn ||
                _uiState.value is LoginUiState.CheckingRegisterConfig

    /**
     * 判断面板地址是否需要用户确认：
     * 仅在**替换已保存的地址**时确认（防误改/SSRF）。
     * 首次设置（无已保存地址）不弹确认——地址是用户刚输入的，再确认一次纯属打扰。
     */
    private fun needsPanelUrlConfirmation(rawUrl: String): Pair<String, Boolean>? {
        val normalized = ConfigValidation.normalizePanelUrl(rawUrl) ?: return null
        val saved = apiUrlStore.currentUrl
        // 无已保存地址（首次设置）：不确认
        if (saved.isNullOrBlank()) return null
        // 与已保存地址一致（忽略尾部斜杠），无需确认
        if (normalized == saved.trimEnd('/')) return null
        val host = normalized.toHttpUrlOrNull()?.host ?: return null
        val isPrivate = ConfigValidation.isPrivateOrReservedHost(host)
        return normalized to isPrivate
    }

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
        // 仅用户手动选择过才保持手动值，否则自动探测
        probeBackendType(value)
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

    fun confirmPanelUrl() {
        val s = _uiState.value as? LoginUiState.ConfirmPanelUrl ?: return
        when (s.pendingAction) {
            PendingAction.LOGIN -> performLogin(s.form)
            PendingAction.CHECK_REGISTER_CONFIG -> performCheckRegisterConfig(s.form)
        }
    }

    fun cancelConfirmPanelUrl() {
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

        // 面板地址变更确认
        needsPanelUrlConfirmation(f.panelUrl)?.let { (url, isPrivate) ->
            _uiState.value = LoginUiState.ConfirmPanelUrl(f, url, isPrivate, PendingAction.LOGIN)
            return
        }

        performLogin(f)
    }

    private fun performLogin(f: LoginUiState.Form) {
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

        // 面板地址变更确认
        needsPanelUrlConfirmation(f.panelUrl)?.let { (url, isPrivate) ->
            _uiState.value = LoginUiState.ConfirmPanelUrl(f, url, isPrivate, PendingAction.CHECK_REGISTER_CONFIG)
            return
        }

        performCheckRegisterConfig(f)
    }

    private fun performCheckRegisterConfig(f: LoginUiState.Form) {
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

    /**
     * 探测面板的 `guest/comm/config` 接口，根据响应字段自动识别后端类型：
     * - xboard 特有字段：is_captcha / captcha_type / turnstile_site_key
     * - 否则视为 xiaov2b（V2Board 系）
     * 探测失败或 URL 非法时不改动用户当前选择。
     * 私有/保留地址跳过探测（防止 SSRF 风险）。
     */
    private fun probeBackendType(rawUrl: String) {
        probeJob?.cancel()
        val normalized = ConfigValidation.normalizePanelUrl(rawUrl) ?: return
        // 私有/保留地址不自动探测，防止 SSRF
        val host = normalized.toHttpUrlOrNull()?.host ?: return
        if (ConfigValidation.isPrivateOrReservedHost(host)) return
        probeJob =
            viewModelScope.launch {
                delay(500) // 防抖，避免每次输入都请求
                val detected = withContext(ioDispatcher) {
                    detectBackendType(normalized)
                }
                if (detected != null) {
                    val f = currentForm()
                    if (f.panelUrl == rawUrl) {
                        _uiState.value = f.copy(backendType = detected)
                    }
                }
            }
    }

    /** 返回 "xboard" 或 "xiaov2b"；网络/解析失败返回 null（不改动选择）。 */
    private fun detectBackendType(panelUrl: String): String? {
        val conn = try {
            val base = panelUrl.trimEnd('/')
            (URL("$base/api/v1/guest/comm/config").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
        } catch (_: Exception) {
            return null
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONObject("data") ?: return null
            val isXboard = data.has("is_captcha") ||
                data.has("captcha_type") ||
                data.has("turnstile_site_key") ||
                data.has("recaptcha_v3_site_key")
            if (isXboard) "xboard" else "xiaov2b"
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
