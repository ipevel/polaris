package com.slte.app.utils

import com.slte.app.R
import okhttp3.MediaType.Companion.toMediaType

/** API 层错误消息的字符串资源 ID，供 UI 层通过 Context 解析为本地化文本。 */
object ApiErrors {
    val EMPTY_DATA = R.string.api_error_empty_data
    val REGISTER_CONFIG = R.string.api_error_register_config
    val USER_INFO = R.string.api_error_user_info
    val SUBSCRIBE_INFO = R.string.api_error_subscribe_info
    val INVITE_INFO = R.string.api_error_invite_info
    val CREATE_ORDER = R.string.api_error_create_order
    val ORDER_DETAIL = R.string.api_error_order_detail
    val COUPON_INVALID = R.string.error_coupon_invalid
    val CHECKOUT = R.string.api_error_checkout
    val NETWORK = R.string.error_network
    val UNSUPPORTED_BACKEND = R.string.api_error_unsupported_backend
}

object Constants {
    /** 默认用户显示名，API 未返回真实用户名时作为兜底 */
    const val DEFAULT_USER_NAME = "用户"


    /** 网络请求超时（秒） */
    const val API_TIMEOUT_SECONDS = 30L

    /** Retrofit Kotlin Serialization 所需 MediaType */
    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    /** 未连接内核时的兜底代理模式 */
    const val DEFAULT_PROXY_MODE = "规则"
    const val PROXY_MODE_GLOBAL = "全局"
    /** 服务器行“当前策略”文案：跟随内核真实选择 */
    const val SELECTION_AUTO = "自动选择"
    const val SELECTION_FALLBACK = "故障转移"
    const val SELECTION_MANUAL = "手动选择"
    const val PLACEHOLDER_DASH = "--"
}

/**
 * TGS 动态贴纸资源路径。
 * 文件位于 src/main/assets/stickers/，运行时本地加载，无需网络。
 */
object Stickers {
    const val LOGIN = "stickers/login.tgs"
    const val FORGOT_PASSWORD = "stickers/forgot.tgs"
    const val REGISTER = "stickers/register.tgs"
    const val EMPTY = "stickers/empty.tgs"
}
