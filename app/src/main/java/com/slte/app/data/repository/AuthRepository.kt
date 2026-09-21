package com.slte.app.data.repository

import com.slte.app.data.local.CredentialStore
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.LoginResponseDto
import com.slte.app.domain.model.EmailCodePurpose
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.SiteInfo
import com.slte.app.domain.model.User
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Singleton
class AuthRepository
@Inject
constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val sessionManager: SessionManager,
    private val subscribeRepository: SubscribeRepository,
) {

    val sessionState: StateFlow<SessionState>
        get() = sessionManager.sessionState

    fun saveCredentials(
        email: String,
        password: String,
    ) = credentialStore.save(email, password)

    fun clearCredentials() = credentialStore.clear()

    fun clearSavedPassword() = credentialStore.clearPassword()

    fun savedEmail(): String? = credentialStore.getSavedEmail()

    fun savedPassword(): String? = credentialStore.getSavedPassword()

    private val revokeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun login(
        email: String,
        password: String,
    ): Result<User> = runApi {
        val response = authApi.login(email.trim(), password)
        val user = response.toDomainUser(email.trim())
        sessionManager.setLoggedIn(user)
        subscribeRepository.invalidateCache()
        user
    }

    suspend fun register(
        email: String,
        password: String,
        emailCode: String? = null,
        inviteCode: String? = null,
    ): Result<User> = runApi {
        val response = authApi.register(email.trim(), password, emailCode, inviteCode)
        val user = response.toDomainUser(email.trim())
        sessionManager.setLoggedIn(user)
        subscribeRepository.invalidateCache()
        user
    }

    suspend fun fetchRegisterConfig(): Result<RegisterConfig> = runApi {
        authApi.fetchRegisterConfig()
    }

    @Volatile
    private var cachedSiteInfo: SiteInfo? = null

    /** 获取站点信息（带内存缓存，失败/无内容时返回默认空 [SiteInfo]） */
    suspend fun fetchSiteInfo(force: Boolean = false): SiteInfo {
        cachedSiteInfo?.let { if (!force) return it }
        val info = try {
            authApi.fetchSiteInfo()
        } catch (_: Exception) {
            SiteInfo()
        }
        cachedSiteInfo = info
        return info
    }

    suspend fun forgotPassword(
        email: String,
        emailCode: String,
        newPassword: String,
    ): Result<Unit> = runApi {
        authApi.forgotPassword(email.trim(), emailCode.trim(), newPassword)
    }

    suspend fun sendEmailCode(
        email: String,
        purpose: EmailCodePurpose = EmailCodePurpose.FORGOT_PASSWORD,
    ): Result<Unit> = runApi {
        authApi.sendEmailCode(email.trim(), purpose)
    }

    suspend fun updateRemindExpire(enabled: Boolean): Result<Unit> = runApi {
        authApi.updateRemindExpire(enabled)
        val current = sessionManager.sessionState.value as? SessionState.LoggedIn
        if (current != null) {
            val updated = current.user.copy(remindExpire = if (enabled) 1 else 0)
            sessionManager.updateUser(updated)
            subscribeRepository.updateCachedUserInfo(updated)
        }
    }

    suspend fun updateRemindTraffic(enabled: Boolean): Result<Unit> = runApi {
        authApi.updateRemindTraffic(enabled)
        val current = sessionManager.sessionState.value as? SessionState.LoggedIn
        if (current != null) {
            val updated = current.user.copy(remindTraffic = if (enabled) 1 else 0)
            sessionManager.updateUser(updated)
            subscribeRepository.updateCachedUserInfo(updated)
        }
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ): Result<Unit> = runApi {
        authApi.changePassword(oldPassword, newPassword)
        val current = sessionManager.sessionState.value as? SessionState.LoggedIn
        if (current != null) {
            val email = current.user.email
            val response =
                try {
                    authApi.login(email.trim(), newPassword)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sessionManager.clearSession()
                    throw e
                }
            val user = response.toDomainUser(email.trim())
            sessionManager.setLoggedIn(user)
            subscribeRepository.invalidateCache()
            if (credentialStore.getSavedEmail() != null) {
                credentialStore.save(email.trim(), newPassword)
            }
        }
    }

    /** 面板后台配置的 Telegram 讨论组链接；失败或未配置返回 null，不阻塞个人页 */
    suspend fun fetchTelegramDiscussLink(): String? = try {
        authApi.fetchTelegramDiscussLink()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Polaris-Auth", "fetchTelegramDiscussLink 失败: ${sanitizeLog(e.message ?: "Unknown")}")
        null
    }

    fun logout() {
        val authData = sessionStore.getAuthData()
        if (authData != null) {
            revokeScope.launch {
                runCatching {
                    val current = sessionStore.getAuthData()
                    if (current == null || current == authData) {
                        authApi.revokeActiveSessions(authData)
                    }
                }.onFailure { e ->
                    AppLog.w("Polaris-Repo", "revokeActiveSessions failed: ${sanitizeLog(e.message ?: "Unknown")}")
                }
            }
        }
        sessionManager.clearSession()
    }

    private fun LoginResponseDto.toDomainUser(email: String) = User(
        id = token,
        displayName = email,
        email = email,
        authData = authData,
        subscribeToken = token,
    )
}
