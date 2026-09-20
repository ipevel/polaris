package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.kernel.SubscribeSource
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody

@Singleton
class SubscribeSourceImpl
@Inject
constructor(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi,
    private val remoteConfig: RemoteConfig,
) : SubscribeSource {
    override fun getEmail(): String? = sessionStore.getEmail()

    override suspend fun fetchSubscribeYaml(): ResponseBody? {
        val candidates = candidateSubscribeUrls()
        if (candidates.isEmpty()) {
            AppLog.w(TAG, "无可用订阅地址（既无账号下发地址，也无订阅令牌）")
            return null
        }
        for (url in candidates) {
            try {
                return authApi.fetchSubscribeYaml(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(
                    TAG,
                    "订阅下载失败 host=${hostOf(url)}，尝试下一个候选地址: ${sanitizeLog(e.message ?: "Unknown")}",
                )
            }
        }
        AppLog.w(TAG, "全部 ${candidates.size} 个候选订阅地址均失败")
        return null
    }

    /**
     * 候选订阅地址，按优先级：
     * 1) 账号下发的 subscribe_url（须 https）
     * 2) 兜底：apiBaseUrl + SUBSCRIBE_PATH + ?token=xxx
     *
     * 任一候选失败都会继续尝试下一个。此前只取单个地址，导致面板下发的
     * 地址一旦失效（换域名后旧域名停用）就永远拿不到订阅，且不会回退。
     */
    private fun candidateSubscribeUrls(): List<String> {
        val out = mutableListOf<String>()
        accountSubscribeUrl()?.let { out.add(it) }
        sessionStore.getSubscribeToken()?.let { token ->
            subscribeFetchUrl(remoteConfig.data.apiBaseUrl, token)?.let { fallback ->
                if (fallback !in out) out.add(fallback)
            }
        }
        return out
    }

    private fun accountSubscribeUrl(): String? {
        val raw = sessionStore.getSubscribeUrl()?.trim().orEmpty()
        if (raw.startsWith("https://")) return raw
        if (raw.isNotEmpty()) {
            AppLog.w(TAG, "订阅地址被拒（须 https）: ${sanitizeLog(raw)}")
        }
        return null
    }

    private fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()

    override fun saveSubscriptionUpdatedAt() = sessionStore.saveSubscriptionUpdatedAt()

    private companion object {
        const val TAG = "SLTE-Subscribe"
    }
}
