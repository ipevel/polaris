package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.kernel.SubscribeSource
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/** 订阅源实现：内存 token + 通过 AuthApi 契约下载订阅 YAML（与具体后端无关） */
@Singleton
class SubscribeSourceImpl @Inject constructor(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi
) : SubscribeSource {

    override fun getSubscribeToken(): String? = sessionStore.getSubscribeToken()

    override fun getEmail(): String? = sessionStore.getEmail()

    override suspend fun fetchSubscribeYaml(token: String): ResponseBody? =
        authApi.fetchSubscribeYaml(token)

    override fun saveSubscriptionUpdatedAt() = sessionStore.saveSubscriptionUpdatedAt()
}
