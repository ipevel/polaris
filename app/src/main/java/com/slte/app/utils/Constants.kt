// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import com.slte.app.R
import okhttp3.MediaType.Companion.toMediaType

object ApiErrors {
    val EMPTY_DATA = R.string.api_error_empty_data
    val REGISTER_CONFIG = R.string.api_error_register_config
    val USER_INFO = R.string.api_error_user_info
    val INVITE_INFO = R.string.api_error_invite_info
    val CREATE_ORDER = R.string.api_error_create_order
    val ORDER_DETAIL = R.string.api_error_order_detail
    val COUPON_INVALID = R.string.error_coupon_invalid
    val GIFT_CARD = R.string.error_gift_card_generic
    val CHECKOUT = R.string.api_error_checkout
    val NETWORK = R.string.error_network

    val SERIALIZATION = R.string.api_error_bad_response

    val UNSUPPORTED_BACKEND = R.string.api_error_unsupported_backend

    /** HTTP 失败应答不是面板 JSON 时（拦截页 / 网关错误页）的可定位文案 */
    fun forHttpStatus(code: Int): Int = when {
        code == 403 -> R.string.api_error_edge_blocked
        code == 429 -> R.string.api_error_rate_limited
        code in 500..599 -> R.string.api_error_server_unavailable
        else -> NETWORK
    }

    /** 给用户看的失败描述：保留状态码，便于把「请求失败」这类模糊报错定位到具体环节 */
    fun httpFailureMessage(code: Int): String = "服务器返回 HTTP $code，且响应不是面板 JSON（疑似 CDN/WAF 拦截或网关错误）"
}

object Constants {

    const val API_TIMEOUT_SECONDS = 30L

    /**
     * API **连接**（TCP 握手）超时，与读写超时分开。
     *
     * 必须显著小于 [API_CALL_TIMEOUT_SECONDS]。OkHttp 4.x **没有 happy-eyeballs**：
     * 一个域名解析出多个地址（IPv4/IPv6）时只能按顺序串行尝试，每个地址都要等满 connectTimeout
     * 才会换下一个。2026-09-26 真机实证：面板域名解析出 **2 个 Cloudflare IPv4 + IPv6**，
     * 网络把这 2 个 IPv4 全部黑洞（SYN 无响应）、IPv6 正常，而系统 DNS 先返回 IPv4——
     * 原 30 秒的连接超时直接吃光 45 秒 callTimeout，登录/流量/个人页全部必然超时。
     *
     * 取值 5 秒的依据：2 个坏地址 × 5 秒 + IPv6 成功 ≈ 11 秒，仍落在流量页 15 秒的等待上限内，
     * 冷启动第一次请求就能成功。取更小的值会误伤慢速移动网络的正常握手；取更大则冷启动首请求
     * 又会撞上上层超时。故障地址只影响**排序**（见 FallbackDns.markConnectFailed），
     * 即使误判也不会把可用地址丢掉。
     */
    const val API_CONNECT_TIMEOUT_SECONDS = 5L

    const val API_CALL_TIMEOUT_SECONDS = 45L

    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    const val DEFAULT_PROXY_MODE = "规则"
    const val PROXY_MODE_GLOBAL = "全局"

    const val PROXY_MODE_DIRECT = "直连"
    const val PROXY_MODE_SCRIPT = "脚本"
    const val PLACEHOLDER_DASH = "--"

    const val DELAY_TIMEOUT = 999

    /**
     * 从未测过（内核侧 tested=false / 无数据）。与 [DELAY_TIMEOUT] 区分：
     * 之前两者都被归一成 999，UI 只能把「没测完」显示成「超时」。
     */
    const val DELAY_PENDING = 0

    const val DELAY_INVALID_MAX = 65535
}

object Stickers {
    const val LOGIN = "stickers/login.tgs"
    const val FORGOT_PASSWORD = "stickers/forgot.tgs"
    const val REGISTER = "stickers/register.tgs"
    const val EMPTY = "stickers/empty.tgs"
    const val ERROR = "stickers/error.tgs"
    const val UPDATE = "stickers/update.tgs"
    const val FORCE_UPDATE = "stickers/force_update.tgs"
    const val INVITE = "stickers/invite.tgs"
    const val GIFT_CARD = "stickers/gift_card.tgs"
}
