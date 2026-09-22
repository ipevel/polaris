// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.BuildConfig
import com.slte.app.data.remote.adapter.xboard.XboardAuthApi
import com.slte.app.data.remote.adapter.xboard.XboardAuthRetrofit
import com.slte.app.data.remote.adapter.xboard.XboardUserPlanRetrofit
import com.slte.app.data.remote.adapter.xboard.XboardUserRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bAuthApi
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bAuthRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bUserPlanRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bUserRetrofit
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.config.ApiFailoverInterceptor
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.utils.ApiErrors
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun subscribeFetchUrl(
    apiBaseUrl: String,
    token: String,
): String? = runCatching {
    (apiBaseUrl.trimEnd('/') + BuildConfig.SUBSCRIBE_PATH)
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.addQueryParameter("token", token)
        ?.build()
        ?.toString()
}.getOrNull()

object BackendAdapterFactory {

    internal val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun createAuthApi(
        backend: ApiBackend,
        isDebug: Boolean,
        authInterceptor: AuthInterceptor,
        dns: okhttp3.Dns,
        remoteConfig: RemoteConfig,
    ): AuthApi = when (backend.type) {
        "xiaov2b" -> {
            val retrofit = buildRetrofit(backend, isDebug, authInterceptor, dns, remoteConfig)
            XiaoV2bAuthApi(
                authApi = retrofit.create(XiaoV2bAuthRetrofit::class.java),
                userApi = retrofit.create(XiaoV2bUserRetrofit::class.java),
                userPlanApi = retrofit.create(XiaoV2bUserPlanRetrofit::class.java),
            )
        }
        "xboard" -> {
            val retrofit = buildRetrofit(backend, isDebug, authInterceptor, dns, remoteConfig)
            XboardAuthApi(
                authApi = retrofit.create(XboardAuthRetrofit::class.java),
                userApi = retrofit.create(XboardUserRetrofit::class.java),
                userPlanApi = retrofit.create(XboardUserPlanRetrofit::class.java),
            )
        }
        else -> throw ApiException("不支持的后端类型: ${backend.type}", ApiErrors.UNSUPPORTED_BACKEND)
    }

    /**
     * 双后端运行时代理：同时实例化 XiaoV2b 与 Xboard 两套适配器，
     * 由 [DualBackendAuthApi] 按 [com.slte.app.data.local.ApiUrlStore.backendType] 每次请求路由。
     */
    fun createDualAuthApi(
        backend: ApiBackend,
        isDebug: Boolean,
        authInterceptor: AuthInterceptor,
        dns: okhttp3.Dns,
        remoteConfig: RemoteConfig,
        apiUrlStore: com.slte.app.data.local.ApiUrlStore,
    ): AuthApi {
        val xiaov2bRetrofit = buildRetrofit(backend.copy(type = "xiaov2b"), isDebug, authInterceptor, dns, remoteConfig)
        val xiaov2bApi =
            XiaoV2bAuthApi(
                authApi = xiaov2bRetrofit.create(XiaoV2bAuthRetrofit::class.java),
                userApi = xiaov2bRetrofit.create(XiaoV2bUserRetrofit::class.java),
                userPlanApi = xiaov2bRetrofit.create(XiaoV2bUserPlanRetrofit::class.java),
            )

        val xboardRetrofit = buildRetrofit(backend.copy(type = "xboard"), isDebug, authInterceptor, dns, remoteConfig)
        val xboardApi =
            XboardAuthApi(
                authApi = xboardRetrofit.create(XboardAuthRetrofit::class.java),
                userApi = xboardRetrofit.create(XboardUserRetrofit::class.java),
                userPlanApi = xboardRetrofit.create(XboardUserPlanRetrofit::class.java),
            )

        return DualBackendAuthApi(
            xiaov2b = xiaov2bApi,
            xboard = xboardApi,
            apiUrlStore = apiUrlStore,
        )
    }

    private fun buildRetrofit(
        backend: ApiBackend,
        isDebug: Boolean,
        authInterceptor: AuthInterceptor,
        dns: okhttp3.Dns,
        remoteConfig: RemoteConfig,
    ): Retrofit {
        // 静态 baseUrl 只决定 Retrofit 建请求时的起始主机；
        // 实际请求主机由 ApiFailoverInterceptor 按运行时面板地址改写。
        // staticBaseUrlHost 供 NO_FAILOVER 请求判断「是否原生面板请求」。
        val staticBaseUrlHost = backend.baseUrl.trimEnd('/').toHttpUrlOrNull()?.host
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(Constants.API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dns(dns)
                .addInterceptor(ApiFailoverInterceptor(remoteConfig, remoteConfig.endpointSelector, staticBaseUrlHost))
                .addInterceptor(authInterceptor)
                .addInterceptor(FormUrlEncodedInterceptor())
                .apply {
                    if (isDebug) {
                        addInterceptor { chain ->
                            val request = chain.request()
                            val safePath = request.url.encodedPath
                            AppLog.d("Polaris-Api", "${request.method} $safePath")
                            val response = chain.proceed(request)
                            AppLog.d("Polaris-Api", "${request.method} ${response.code} $safePath")
                            response
                        }
                    }
                }.build()

        val baseUrl = backend.baseUrl.trimEnd('/') + backend.apiPrefix + "/"
        return Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(Constants.JSON_MEDIA_TYPE))
            .build()
    }
}
