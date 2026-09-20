package com.slte.app.data.remote.config

import com.slte.app.data.remote.api.ApiHeaders
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class ApiFailoverInterceptor(
    private val config: FailoverConfig,
    private val selector: EndpointSelector,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_NO_FAILOVER) != null) return chain.proceed(request)
        val retryable = FailoverPolicy.isRetryableMethod(request.method)
        val primary = config.apiBaseUrl
        val candidates = config.apiCandidates(primary)
        var lastError: IOException? = null
        var lastFailureResponse: Response? = null
        var attemptedAny = false

        for ((index, base) in candidates.withIndex()) {
            if (selector.isOpen(base)) continue
            attemptedAny = true
            lastFailureResponse?.close()
            lastFailureResponse = null
            val attemptStart = System.currentTimeMillis()
            try {
                val attempt = chain.proceed(rewriteBaseUrl(request, base) ?: request)
                val latency = System.currentTimeMillis() - attemptStart
                if (FailoverPolicy.isFailureCode(attempt.code)) {
                    selector.recordFailure(base)
                    lastFailureResponse = attempt
                    if (!retryable) return attempt
                    AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} HTTP ${attempt.code}，切换下一个")
                    continue
                }
                val jsonMismatch =
                    FailoverPolicy.isJsonMismatch(
                        attempt.header("Content-Type"),
                        FailoverPolicy.firstByteOf(attempt),
                    )
                if (attempt.isSuccessful && jsonMismatch) {
                    selector.recordFailure(base)
                    lastFailureResponse = attempt
                    if (!retryable) return attempt
                    AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} 200 但响应与 JSON 声明不符，切换下一个")
                    continue
                }
                selector.recordSuccess(base, latency)
                lastFailureResponse?.close()
                return attempt
            } catch (e: IOException) {
                selector.recordFailure(base)
                lastError = e
                if (!retryable) throw e
                AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} 不可用，切换下一个: ${sanitizeLog(e.message ?: "")}")
            }
        }
        if (!attemptedAny) {
            // 所有候选都在熔断窗口内：仍然放行一次主地址请求。
            // 否则用户在这段时间里「任何操作都直接失败」，而且一次请求都没发出去、
            // 拿不到任何真实原因（登录只会显示「请求失败」）。成功即自愈熔断状态；
            // 失败不再累加失败计数，避免用户反复重试把退避越推越长。
            val probeStart = System.currentTimeMillis()
            val probe = chain.proceed(rewriteBaseUrl(request, primary) ?: request)
            if (probe.isSuccessful) {
                selector.recordSuccess(primary, System.currentTimeMillis() - probeStart)
            }
            return probe
        }
        lastFailureResponse?.let { return it }
        throw lastError ?: IOException("所有 API 地址均不可用")
    }

    companion object {

        const val HEADER_NO_FAILOVER = ApiHeaders.NO_FAILOVER_NAME
    }

    private fun rewriteBaseUrl(
        request: Request,
        base: String,
    ): Request? {
        val baseUrl = base.trimEnd('/').toHttpUrlOrNull() ?: return null
        val newUrl =
            request.url
                .newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()
        return request.newBuilder().url(newUrl).build()
    }
}
