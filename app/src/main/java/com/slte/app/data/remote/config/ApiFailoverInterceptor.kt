package com.slte.app.data.remote.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/**
 * API 地址 failover：主地址连接层失败（超时/断网/被墙）时，
 * 自动把请求重写到 RemoteConfig 中的下一个候选地址重试，全部失败才抛错。
 * 只重写 scheme/host/port，路径与请求头保持不变。
 */
class ApiFailoverInterceptor(
    private val remoteConfig: RemoteConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastError: IOException? = null
        // 主地址取当前生效的远程配置（运行期切换后请求立即跟随，与订阅 profile source 保持一致）；
        // 请求原始 URL 为 DI 时的固定 base，因此每个候选都按 scheme/host/port 重写，相同 host 重写无副作用
        val primary = remoteConfig.data.apiBaseUrl
        // 仅幂等方法允许 failover 重放：POST 在"连接超时但已落库"窗口下重复发送会导致重复下单/结算
        val retryable = chain.request().method in RETRYABLE_METHODS
        for (base in remoteConfig.apiCandidates(primary)) {
            try {
                val request = chain.request()
                return chain.proceed(rewriteBaseUrl(request, base) ?: request)
            } catch (e: IOException) {
                lastError = e
                if (!retryable) throw e
                AppLog.w("SLTE-Api", "ApiFailover: $base 不可用，切换下一个: ${sanitizeLog(e.message ?: "")}")
            }
        }
        throw lastError ?: IOException("所有 API 地址均不可用")
    }

    private fun rewriteBaseUrl(request: Request, base: String): Request? {
        val baseUrl = base.trimEnd('/').toHttpUrlOrNull() ?: return null
        val newUrl = request.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()
        return request.newBuilder().url(newUrl).build()
    }

    private companion object {
        /** 允许 failover 重放的幂等方法 */
        val RETRYABLE_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}
