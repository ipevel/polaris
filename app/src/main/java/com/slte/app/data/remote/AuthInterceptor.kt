package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.config.AllowedHosts
import com.slte.app.utils.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

internal object AuthRules {

    const val AUTH_API_PREFIX = ApiPaths.AUTH

    data class Decision(

        val attachToken: Boolean,

        val clearSession: Boolean,
    )

    fun isAuthPath(encodedPath: String): Boolean = encodedPath.startsWith(AUTH_API_PREFIX)

    fun isAuthFailureBodyText(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        return AUTH_FAILURE_KEYWORDS.any { lower.contains(it) }
    }

    fun isAuthFailureBody(body: String?): Boolean = body.isNullOrBlank() || isAuthFailureBodyText(body)

    fun decide(
        token: String?,
        isAllowedHost: Boolean,
        hasAuthHeader: Boolean,
        responseCode: Int,
        isAuthFailureBody: Boolean,
    ): Decision {
        val attachToken = token != null && isAllowedHost && !hasAuthHeader
        val authFailed =
            responseCode == 401 ||
                (responseCode == 403 && isAuthFailureBody)
        val clearSession = authFailed && token != null
        return Decision(attachToken = attachToken, clearSession = clearSession)
    }

    internal val AUTH_FAILURE_KEYWORDS: List<String> =
        listOf(
            "未登录",
            "登陆已过期",
            "登录已过期",
            "unauthorized",
            "unauthenticated",
            "token expired",
            "invalid token",
        )
}

@Singleton
class AuthInterceptor
@Inject
constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    private val _authErrorEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val authErrorEvents: SharedFlow<Unit> = _authErrorEvents.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.getAuthData()

        val canAttachToken = AllowedHosts.isAllowedHost(request.url.host)
        val decision =
            AuthRules.decide(
                token = token,
                isAllowedHost = canAttachToken,
                hasAuthHeader = request.header("Authorization") != null,
                responseCode = 0,
                isAuthFailureBody = false,
            )
        val authenticated =
            if (decision.attachToken && token != null) {
                request
                    .newBuilder()
                    .addHeader("Authorization", token)
                    .build()
            } else {
                if (token != null && !canAttachToken) {
                    AppLog.w("SLTE-Api", "非白名单主机，已跳过凭据注入: ${request.url.encodedPath}")
                }
                request
            }
        val response = chain.proceed(authenticated)

        val clearSession =
            AuthRules.decide(
                token = token,
                isAllowedHost = canAttachToken,
                hasAuthHeader = request.header("Authorization") != null,
                responseCode = response.code,
                isAuthFailureBody = isAuthFailureResponse(response),
            ).clearSession
        if (clearSession && token == sessionStore.getAuthData()) {
            sessionStore.clear()
            _authErrorEvents.tryEmit(Unit)
        }

        return response
    }

    private fun isAuthFailureResponse(response: Response): Boolean {
        if (!AuthRules.isAuthPath(response.request.url.encodedPath)) {
            return false
        }
        return isAuthFailureBody(response)
    }

    private fun isAuthFailureBody(response: Response): Boolean {
        val body =
            try {
                response.peekBody(MAX_PEEK_BYTES).string()
            } catch (_: Exception) {
                return false
            }
        return AuthRules.isAuthFailureBody(body)
    }

    private companion object {
        const val MAX_PEEK_BYTES = 4096L
    }
}
