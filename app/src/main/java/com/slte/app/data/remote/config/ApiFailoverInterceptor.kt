package com.slte.app.data.remote.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/**
 * API 地址 failover：主地址连接层失败或返回故障状态码/伪成功响应时，
 * 自动把请求重写到候选地址重试，全部失败才抛错。
 *
 * - 候选顺序由 [EndpointSelector] 按健康度排列（熔断中的地址排后、不派发常规请求）；
 * - 失败会上报选择器累计熔断状态，成功后复位；
 * - 只重写 scheme/host/port，路径与请求头保持不变；
 * - 仅幂等方法（GET/HEAD/OPTIONS）允许重放，避免重复下单/结算。
 */
class ApiFailoverInterceptor(
    private val config: FailoverConfig,
    private val selector: EndpointSelector
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 仅幂等方法允许 failover 重放：POST 在"连接超时但已落库"窗口下重复发送会导致重复下单/结算
        val retryable = FailoverPolicy.isRetryableMethod(request.method)
        // 主地址取当前生效的远程配置（运行期切换后请求立即跟随）；候选含熔断排序
        val primary = config.apiBaseUrl
        val candidates = config.apiCandidates(primary)
        var lastError: IOException? = null
        var lastFailureResponse: Response? = null

        for ((index, base) in candidates.withIndex()) {
            // 熔断退避期内不派发请求（半开期放行，作为恢复探测）
            if (selector.isOpen(base)) continue
            val attemptStart = System.currentTimeMillis()
            try {
                val attempt = chain.proceed(rewriteBaseUrl(request, base) ?: request)
                val latency = System.currentTimeMillis() - attemptStart
                if (FailoverPolicy.isFailureCode(attempt.code)) {
                    selector.recordFailure(base)
                    // 只保留最近一次故障响应：覆盖前关闭旧响应，避免连接泄漏
                    lastFailureResponse?.close()
                    lastFailureResponse = attempt
                    if (!retryable) return attempt
                    AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} HTTP ${attempt.code}，切换下一个")
                    continue
                }
                // HTTP 200 但声明 JSON 却返回非 JSON（劫持页/网关默认页/被篡改内容）：视为伪成功故障
                val jsonMismatch = FailoverPolicy.isJsonMismatch(
                    attempt.header("Content-Type"),
                    FailoverPolicy.firstByteOf(attempt)
                )
                if (attempt.isSuccessful && jsonMismatch) {
                    selector.recordFailure(base)
                    lastFailureResponse?.close()
                    lastFailureResponse = attempt
                    if (!retryable) return attempt
                    AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} 200 但响应与 JSON 声明不符，切换下一个")
                    continue
                }
                selector.recordSuccess(base, latency)
                // 成功返回前关闭已记录的故障响应
                lastFailureResponse?.close()
                return attempt
            } catch (e: IOException) {
                selector.recordFailure(base)
                lastError = e
                if (!retryable) throw e
                AppLog.w("SLTE-Api", "ApiFailover: 候选 ${index + 1} 不可用，切换下一个: ${sanitizeLog(e.message ?: "")}")
            }
        }
        // 故障响应优先于 IOException 返回（保留原始错误码给上层）
        lastFailureResponse?.let { return it }
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
}
