package com.slte.app.kernel

import okhttp3.ResponseBody

interface SubscribeSource {
    fun getEmail(): String?

    suspend fun fetchSubscribeYaml(): ResponseBody?

    fun saveSubscriptionUpdatedAt()
}
