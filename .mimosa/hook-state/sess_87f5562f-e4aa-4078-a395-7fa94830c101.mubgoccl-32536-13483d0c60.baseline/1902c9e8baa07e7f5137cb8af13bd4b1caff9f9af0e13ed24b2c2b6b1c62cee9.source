package com.slte.app.data.remote

import com.slte.app.data.local.ApiUrlStore
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

    /**
     * 403 是否代表「会话失效」。
     *
     * 只有**面板自身**的 JSON 应答且带失效关键词才算。边缘拦截页（Cloudflare / WAF /
     * CDN 返回的 HTML）与空体一律不算：一次 CDN 拦截就清会话并停 VPN，用户会被彻底
     * 锁在外面（2026-09-20 线上事故：面板 WAF 直连拦截 → 点工单被登出 → 之后登录全失败）。
     */
    fun isSessionExpiryFor403(body: String?): Boolean {
        val text = body?.trimStart() ?: return false
        if (text.isEmpty() || text.startsWith("<")) return false
        return isAuthFailureBodyText(text)
    }

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
    private val apiUrlStore: ApiUrlStore,
) : Interceptor {
    private val _authErrorEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val authErrorEvents: SharedFlow<Unit> = _authErrorEvents.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.getAuthData()

        // 自定义面板主机也要注入 token：不在 BuildConfig 白名单里，否则登录必失败
        val canAttachToken = AllowedHosts.isAllowedHost(request.url.host) || apiUrlStore.isCurrentHost(request.url.host)
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
                    AppLog.w("Polaris-Api", "非白名单主机，已跳过凭据注入: ${request.url.encodedPath}")
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
        if (response.code == 401) {
            return true
        }
        if (response.code != 403) {
            return false
        }
        // 403 分三类：
        //   - 面板 JSON + 失效关键词（未登录 / 登陆已过期）→ 真·会话失效，清会话；
        //   - JSON 业务错误（如 {"msg":"余额不足"}）→ 网络没有「未登录」关键字，不清；
        //   - 边缘拦截页（Cloudflare/WAF/CDN 的 HTML）或空体 → 只是被拦截，
        //     不是会话问题，绝不清会话、更不停 VPN（否则一次拦截就把用户锁在外面）。
        return AuthRules.isSessionExpiryFor403(peekBody(response))
    }

    private fun peekBody(response: Response): String? = try {
        response.peekBody(MAX_PEEK_BYTES).string()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_PEEK_BYTES = 4096L
    }
}
