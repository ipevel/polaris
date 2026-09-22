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

    const val API_CALL_TIMEOUT_SECONDS = 45L

    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    const val DEFAULT_PROXY_MODE = "规则"
    const val PROXY_MODE_GLOBAL = "全局"

    const val PROXY_MODE_DIRECT = "直连"
    const val PROXY_MODE_SCRIPT = "脚本"
    const val PLACEHOLDER_DASH = "--"

    const val DELAY_TIMEOUT = 999

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
