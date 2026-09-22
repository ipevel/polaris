// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.data.local.SiteInfoStore
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
import retrofit2.Response

@Singleton
class SubscribeSourceImpl
@Inject
constructor(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi,
    private val remoteConfig: RemoteConfig,
    private val siteInfoStore: SiteInfoStore,
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
                val response = authApi.fetchSubscribeYaml(url) ?: continue
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "订阅下载失败 host=${hostOf(url)} code=${response.code()}，尝试下一个候选地址")
                    continue
                }
                captureSiteInfoFromHeaders(response)
                return response.body()
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
     * 从订阅响应头提取站点品牌信息（Clash 客户端通用约定）：
     * - `profile-title`：站点名，`base64,<b64>` 前缀表示 base64 编码
     * - `profile-web-page-url`：站点官网
     * 首次订阅登录时拉取并记住，之后每次重新拉取订阅时随响应更新。
     */
    private fun captureSiteInfoFromHeaders(response: Response<ResponseBody>) {
        val title = runCatching { response.headers()["profile-title"] }.getOrNull()
        val name = parseProfileTitle(title)
        val webPageUrl = runCatching { response.headers()["profile-web-page-url"] }.getOrNull()
        if (name == null && webPageUrl.isNullOrBlank()) return
        siteInfoStore.updateFromSubscription(name = name, url = webPageUrl)
        AppLog.d(TAG, "captureSiteInfo: name=${sanitizeLog(name ?: "-")} url=${sanitizeLog(webPageUrl ?: "-")}")
    }

    private fun parseProfileTitle(raw: String?): String? {
        val value = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val decoded =
            if (value.startsWith("base64:", ignoreCase = true) || value.startsWith("base64,", ignoreCase = true)) {
                runCatching {
                    String(java.util.Base64.getMimeDecoder().decode(value.substring(7).trim()), Charsets.UTF_8)
                }.getOrNull() ?: return null
            } else {
                value
            }
        return decoded.trim().takeIf { it.isNotEmpty() && it.length <= 128 }
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
            // 用 RemoteConfig.apiBaseUrl（运行时面板地址，含用户自定义）而非 data 缓存字段
            subscribeFetchUrl(remoteConfig.apiBaseUrl, token)?.let { fallback ->
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
        const val TAG = "Polaris-Subscribe"
    }
}
