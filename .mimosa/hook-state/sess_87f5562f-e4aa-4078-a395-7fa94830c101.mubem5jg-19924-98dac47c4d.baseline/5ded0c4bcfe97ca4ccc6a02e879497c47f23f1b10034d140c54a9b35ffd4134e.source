package com.slte.app.data.local

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.AuthInterceptor
import com.slte.app.data.remote.FallbackDns
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.User
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class SessionManager
@Inject
constructor(
    private val sessionStore: SessionStore,
    private val authInterceptor: AuthInterceptor,
    private val kernelManager: KernelManager,
    private val kernelConfig: KernelConfig,
    private val fallbackDns: FallbackDns,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _logoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvents: SharedFlow<Unit> = _logoutEvents.asSharedFlow()

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        restoreSession()
        observeAuthErrors()
    }

    private fun restoreSession() {
        if (sessionStore.hasSession()) {
            val authData = sessionStore.getAuthData()
            val email = sessionStore.getEmail()
            val subscribeToken = sessionStore.getSubscribeToken()
            if (authData != null && email != null && subscribeToken != null) {
                val user =
                    sessionStore.getUserInfo()?.takeIf { it.subscribeToken == subscribeToken }
                        ?: User(
                            id = subscribeToken,
                            displayName = email,
                            email = email,
                            authData = authData,
                            subscribeToken = subscribeToken,
                        )
                _sessionState.value = SessionState.LoggedIn(user)
            } else {
                sessionStore.clear()
                _sessionState.value = SessionState.LoggedOut
            }
        } else {
            sessionStore.clear()
            _sessionState.value = SessionState.LoggedOut
        }
    }

    private fun observeAuthErrors() {
        scope.launch {
            authInterceptor.authErrorEvents.collect {
                clearSession(expired = true)
            }
        }
    }

    fun setLoggedIn(user: User) {
        _sessionState.value = SessionState.LoggedIn(user)
        sessionStore.save(user.authData, user.email, user.subscribeToken)
        sessionStore.saveUserInfo(user)
    }

    fun updateUser(user: User) {
        if (_sessionState.value is SessionState.LoggedIn) {
            _sessionState.value = SessionState.LoggedIn(user)
        }
    }

    fun clearSession(expired: Boolean = false) {
        val email = sessionStore.getEmail()
        scope.launch {
            kernelConfig.deleteAccountProfiles(email)
            kernelManager.stopVpn()
            fallbackDns.clearCache()
        }
        _sessionState.value = SessionState.LoggedOut
        sessionStore.clear()
        _logoutEvents.tryEmit(Unit)
        if (expired) _sessionExpiredEvents.tryEmit(Unit)
    }
}
