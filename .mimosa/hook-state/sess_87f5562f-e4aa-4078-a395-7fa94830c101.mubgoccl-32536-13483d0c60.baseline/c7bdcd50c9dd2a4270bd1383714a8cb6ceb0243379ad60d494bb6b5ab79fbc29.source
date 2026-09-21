package com.slte.app.data.repository

import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.SubscribeInfoDto as ApiSubscribeInfo
import com.slte.app.domain.model.Notice
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.User
import com.slte.app.utils.AppLog
import com.slte.app.utils.FormatUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class SubscribeRepository
@Inject
constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val sessionManager: SessionManager,
) {

    private val _subscribeInfo = MutableStateFlow(sessionStore.getSubscribeInfo())

    val subscribeInfo: StateFlow<SubscribeInfo?> = _subscribeInfo.asStateFlow()

    @Volatile
    private var cachedSubscribeInfo: SubscribeInfo? = null

    @Volatile
    private var subscribeInfoTimestamp: Long = 0L

    @Volatile
    private var cachedUserInfo: User? = null

    @Volatile
    private var userInfoTimestamp: Long = 0L

    private val subscribeMutex = Mutex()
    private val userMutex = Mutex()

    init {
        sessionManager.logoutEvents
            .onEach { invalidateCache() }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    suspend fun fetchSubscribeInfo(force: Boolean = false): Result<SubscribeInfo> {
        if (sessionManager.sessionState.value !is SessionState.LoggedIn) {
            return Result.failure(IllegalStateException("未登录"))
        }
        val now = System.currentTimeMillis()
        val cached = cachedSubscribeInfo
        if (!force && cached != null && CachePolicy.isFresh(subscribeInfoTimestamp, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS)) {
            return Result.success(cached)
        }
        return subscribeMutex.withLock {
            val recheckCached = cachedSubscribeInfo
            if (!force && recheckCached != null && CachePolicy.isFresh(subscribeInfoTimestamp, System.currentTimeMillis(), CachePolicy.SUBSCRIBE_INFO_TTL_MS)) {
                return@withLock Result.success(recheckCached)
            }
            val result =
                runApi {
                    val session =
                        sessionManager.sessionState.value as? SessionState.LoggedIn
                            ?: error("会话已失效")
                    val info = authApi.fetchSubscribeInfo().toDomainSubscribeInfo()
                    val current =
                        sessionManager.sessionState.value as? SessionState.LoggedIn
                            ?: error("会话已失效")
                    check(current.user.authData == session.user.authData) { "会话已切换" }
                    cachedSubscribeInfo = info
                    subscribeInfoTimestamp = System.currentTimeMillis()
                    sessionStore.saveSubscribeInfo(info)
                    _subscribeInfo.value = info
                    info.subscribeUrl?.takeIf { it.isNotBlank() }?.let { fresh ->
                        logSubscribeHostChange(sessionStore.getSubscribeUrl(), fresh)
                        sessionStore.saveSubscribeUrl(fresh)
                    }
                    info
                }
            if (result.isSuccess) {
                result
            } else if (force) {
                result
            } else {
                sessionStore.getSubscribeInfo()?.let { Result.success(it) } ?: result
            }
        }
    }

    fun getCachedSubscribeInfo(): SubscribeInfo? = sessionStore.getSubscribeInfo()

    fun getSubscriptionUpdatedAt(): Long = sessionStore.getSubscriptionUpdatedAt()

    suspend fun fetchUserInfo(force: Boolean = false): Result<User> {
        if (sessionManager.sessionState.value !is SessionState.LoggedIn) {
            return Result.failure(IllegalStateException("未登录"))
        }
        val now = System.currentTimeMillis()
        val cached = cachedUserInfo
        if (!force && cached != null && CachePolicy.isFresh(userInfoTimestamp, now, CachePolicy.USER_INFO_TTL_MS)) {
            return Result.success(cached)
        }
        return userMutex.withLock {
            val recheckCached = cachedUserInfo
            if (!force && recheckCached != null && CachePolicy.isFresh(userInfoTimestamp, System.currentTimeMillis(), CachePolicy.USER_INFO_TTL_MS)) {
                return@withLock Result.success(recheckCached)
            }
            val result =
                runApi {
                    val session =
                        sessionManager.sessionState.value as? SessionState.LoggedIn
                            ?: error("会话已失效")
                    val info = authApi.fetchUserInfo()
                    val current =
                        sessionManager.sessionState.value as? SessionState.LoggedIn
                            ?: error("会话已失效")
                    check(current.user.authData == session.user.authData) { "会话已切换" }
                    val user =
                        session.user.copy(
                            email = info.email,
                            balance = FormatUtils.balance(info.balance),
                            remindExpire = info.remindExpire,
                            remindTraffic = info.remindTraffic,
                        )
                    cachedUserInfo = user
                    userInfoTimestamp = System.currentTimeMillis()
                    sessionStore.saveUserInfo(user)
                    sessionManager.updateUser(user)
                    user
                }
            if (result.isSuccess || force) {
                result
            } else {
                sessionStore.getUserInfo()?.let { Result.success(it) } ?: result
            }
        }
    }

    fun getCachedUserInfo(): User? = sessionStore.getUserInfo()

    fun updateCachedUserInfo(user: User) {
        cachedUserInfo = user
        sessionStore.saveUserInfo(user)
    }

    suspend fun fetchNotices(): Result<List<Notice>> {
        if (sessionManager.sessionState.value !is SessionState.LoggedIn) {
            return Result.failure(IllegalStateException("未登录"))
        }
        return runApi {
            authApi.fetchNotices()
        }
    }

    internal fun invalidateCache() {
        cachedSubscribeInfo = null
        subscribeInfoTimestamp = 0L
        cachedUserInfo = null
        userInfoTimestamp = 0L
        sessionStore.clearDataCache()
        _subscribeInfo.value = null
    }

    private fun ApiSubscribeInfo.toDomainSubscribeInfo() = SubscribeInfo(
        planName = planName,
        planId = planId,
        transferEnable = transferEnable,
        usedTraffic = upload + download,
        expiredAt = expiredAt,
        resetDay = resetDay,
        subscribeUrl = subscribeUrl,
    )
}

internal fun subscribeHostOf(url: String?): String? = url?.trim()?.toHttpUrlOrNull()?.host?.lowercase()

private fun logSubscribeHostChange(
    previous: String?,
    current: String,
) {
    val oldHost = subscribeHostOf(previous) ?: return
    val newHost = subscribeHostOf(current) ?: return
    if (oldHost != newHost) {
        AppLog.w("Polaris-Subscribe", "订阅地址主机变更: $oldHost -> $newHost")
    }
}
