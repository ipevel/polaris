package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.kernel.SubscribeSource
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import javax.inject.Singleton
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
        val url =
            accountSubscribeUrl()
                ?: sessionStore.getSubscribeToken()?.let { subscribeFetchUrl(remoteConfig.data.apiBaseUrl, it) }
                ?: return null
        return authApi.fetchSubscribeYaml(url)
    }

    private fun accountSubscribeUrl(): String? {
        val raw = sessionStore.getSubscribeUrl()?.trim().orEmpty()
        if (raw.startsWith("https://")) return raw
        if (raw.isNotEmpty()) {
            AppLog.w("SLTE-Subscribe", "订阅地址被拒（须 https）: ${sanitizeLog(raw)}")
        }
        return null
    }

    override fun saveSubscriptionUpdatedAt() = sessionStore.saveSubscriptionUpdatedAt()
}
