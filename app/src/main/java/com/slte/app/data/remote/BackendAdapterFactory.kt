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
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
        dns: FallbackDns,
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
        dns: FallbackDns,
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
        dns: FallbackDns,
        remoteConfig: RemoteConfig,
    ): Retrofit {
        // 静态 baseUrl 只决定 Retrofit 建请求时的起始主机；
        // 实际请求主机由 ApiFailoverInterceptor 按运行时面板地址改写。
        // staticBaseUrlHost 供 NO_FAILOVER 请求判断「是否原生面板请求」。
        val staticBaseUrlHost = backend.baseUrl.trimEnd('/').toHttpUrlOrNull()?.host
        val client =
            OkHttpClient
                .Builder()
                // 连接超时单独收窄：OkHttp 4.x 没有 happy-eyeballs，多地址只能串行试，
                // 坏地址（被黑洞的 IPv4）必须尽快让位给后面的可用地址。
                // 详见 Constants.API_CONNECT_TIMEOUT_SECONDS 的注释与 2026-09-26 真机实证。
                .connectTimeout(Constants.API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(Constants.API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dns(dns)
                .eventListener(ConnectFailureListener(dns))
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

/**
 * 把"这个地址连不上"反馈给 [FallbackDns]，让**同一进程内**后续 DNS 结果把坏地址排到队尾。
 *
 * 背景（2026-09-26 真机实证，雷电模拟器 + 真实面板）：
 * 面板域名解析出多个 Cloudflare IPv4 与 IPv6，用户所在网络把 **IPv4 全部黑洞**（TCP SYN 无响应）、
 * IPv6 完全正常；而 DNS 先返回 IPv4。OkHttp 4.x 没有 happy-eyeballs，只能按返回顺序串行尝试，
 * 于是每个请求都在坏地址上白等一个 connectTimeout——30 秒的连接超时直接吃光 45 秒 callTimeout，
 * 表现为：**不连 VPN 时登录/流量/个人页全部超时**（连上 VPN 走隧道才恢复），
 * 而"退出登录会停 VPN"，所以退出后就再也登不回来。
 *
 * 这里只上报**地址本身有问题**的两类失败：握手超时（黑洞的典型形态）与连接被拒。
 * TLS 协商失败、HTTP/2 协商失败、代理失败等不是地址问题，不参与降级——
 * 否则一次协议协商抖动就会把好地址压到队尾。
 */
private class ConnectFailureListener(
    private val dns: FallbackDns,
) : EventListener() {

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        e: IOException,
    ) {
        if (e !is SocketTimeoutException && e !is ConnectException) return
        inetSocketAddress.address?.let(dns::markConnectFailed)
    }

    /**
     * 连上了就立刻撤销该地址族的"连不上"记忆。
     *
     * 这条与 [connectFailed] 配对，才敢把记忆落盘跨进程复用：换网络后一次成功即自愈，
     * 不会让用户在错误偏好下多等一个连接超时。
     */
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        inetSocketAddress.address?.let(dns::markConnectSucceeded)
    }
}
