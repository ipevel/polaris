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

    private fun buildRetrofit(
        backend: ApiBackend,
        isDebug: Boolean,
        authInterceptor: AuthInterceptor,
        dns: okhttp3.Dns,
        remoteConfig: RemoteConfig,
    ): Retrofit {
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(Constants.API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dns(dns)
                .addInterceptor(ApiFailoverInterceptor(remoteConfig, remoteConfig.endpointSelector))
                .addInterceptor(authInterceptor)
                .addInterceptor(FormUrlEncodedInterceptor())
                .apply {
                    if (isDebug) {
                        addInterceptor { chain ->
                            val request = chain.request()
                            val safePath = request.url.encodedPath
                            AppLog.d("SLTE-Api", "${request.method} $safePath")
                            val response = chain.proceed(request)
                            AppLog.d("SLTE-Api", "${request.method} ${response.code} $safePath")
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
