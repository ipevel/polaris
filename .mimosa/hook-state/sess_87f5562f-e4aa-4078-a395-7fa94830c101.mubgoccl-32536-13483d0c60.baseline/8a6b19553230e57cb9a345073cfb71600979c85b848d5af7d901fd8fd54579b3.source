package com.slte.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SubscribeInfo(
    val planName: String,
    val transferEnable: Long,
    val usedTraffic: Long,
    val expiredAt: Long,
    val resetDay: Int? = null,
    val planId: Int = 0,
    val subscribeUrl: String? = null,
) {

    val hasPlan: Boolean get() = planName.isNotBlank() && (planId > 0 || transferEnable > 0L)

    val expired: Boolean get() = expiredAt > 0L && currentTimeSeconds() > expiredAt

    companion object {
        fun currentTimeSeconds(): Long = System.currentTimeMillis() / 1000L
    }
}

internal fun isPlanValid(info: SubscribeInfo?): Boolean = info?.hasPlan == true && !info.expired
