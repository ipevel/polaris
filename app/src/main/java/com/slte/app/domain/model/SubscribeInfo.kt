package com.slte.app.domain.model

import kotlinx.serialization.Serializable

/**
 * 订阅信息的领域模型，包含计算属性。
 *
 * @param planName 套餐名称
 * @param planId 套餐 ID（0 表示无套餐）
 * @param transferEnable 总可用流量（字节）
 * @param usedTraffic 已用流量（字节）
 * @param expiredAt 套餐到期时间戳（秒）
 * @param resetDay 每月流量重置日
 */
@Serializable
data class SubscribeInfo(
    val planName: String,
    val transferEnable: Long,
    val usedTraffic: Long,
    val expiredAt: Long,
    val resetDay: Int? = null,
    val planId: Int = 0,
) {
    /** 是否有有效套餐：以套餐 ID 为准；无 planId 时按流量判断 */
    val hasPlan: Boolean get() = planName.isNotBlank() && (planId > 0 || transferEnable > 0L)

    /** 套餐是否已到期 */
    val expired: Boolean get() = expiredAt > 0L && currentTimeSeconds() > expiredAt

    companion object {
        fun currentTimeSeconds(): Long = System.currentTimeMillis() / 1000L
    }
}
