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

        for ((index, base) in candidates.withIndex()) {
            if (selector.isOpen(base)) continue
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
