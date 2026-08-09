package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 认证拦截器：注入 Authorization 头，认证失效自动清会话 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore
) : Interceptor {

    private val _authErrorEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 认证失效事件流 */
    val authErrorEvents: SharedFlow<Unit> = _authErrorEvents.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.getAuthData()
        val authenticated = if (token != null && request.header("Authorization") == null) {
            request.newBuilder()
                .addHeader("Authorization", token)
                .build()
        } else {
            request
        }
        val response = chain.proceed(authenticated)

        // 仅当请求携带的 token 仍是当前会话时清会话；
        // 401 视为认证失败；403 仅在认证类接口(/api/v1/user/ 前缀)且响应体为空时
        // 视为会话失效，其余 403 属业务状态(如无套餐订阅返回 403 空文本)，不自动登出
        val authFailed403 = response.code == 403 &&
            request.url.encodedPath.startsWith("/api/v1/user/") &&
            response.peekBody(1).string().isEmpty()
        if ((response.code == 401 || authFailed403) &&
            token != null && token == sessionStore.getAuthData()
        ) {
            sessionStore.clear()
            _authErrorEvents.tryEmit(Unit)
        }

        return response
    }
}
