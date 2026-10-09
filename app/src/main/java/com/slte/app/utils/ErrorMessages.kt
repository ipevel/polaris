// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import com.slte.app.R
import com.slte.app.data.remote.ApiException

object ErrorMessages {

    fun forLogin(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapLoginError(e.message)
        else -> R.string.error_network
    }

    fun forRegister(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapRegisterError(e.message)
        else -> R.string.error_network
    }

    fun forForgot(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapForgotError(e.message)
        else -> R.string.error_network
    }

    fun forSendCode(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapSendCodeError(e.message)
        else -> R.string.error_network
    }

    fun forOrder(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapOrderError(e.message)
        else -> R.string.error_order_failed
    }

    /**
     * 套餐（订阅商品）列表加载失败。
     *
     * 与 [forOrder] 分开的原因：套餐页原先复用了订单的映射，于是**套餐列表加载失败时显示的是
     * 「订单操作失败」**——用户在看订阅页，却被告知订单出问题（视觉复核按像素定位到这一处文案泄漏：
     * 套餐错误态与订单错误态的差异只在标题行）。这里给套餐自己的兜底文案，
     * 后端返回的 `stringResId` 仍优先透传，语义不受影响。
     */
    fun forPlans(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: R.string.error_plans_failed
        else -> R.string.error_plans_failed
    }

    fun forSubscribe(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapSubscribeError(e.message)
        else -> R.string.error_network
    }

    fun forServer(e: Throwable?): Int = when (e) {
        is ApiException -> e.stringResId ?: mapServerError(e.message)
        else -> R.string.error_server_load
    }

    fun mapLoginError(backendMessage: String): Int = when {
        backendMessage.contains("不存在", ignoreCase = true) ||
            backendMessage.contains("not found", ignoreCase = true) ||
            backendMessage.contains("未注册", ignoreCase = true) ||
            backendMessage.contains("not registered", ignoreCase = true) -> R.string.error_login_not_found

        backendMessage.contains("密码", ignoreCase = true) ||
            backendMessage.contains("password", ignoreCase = true) ||
            backendMessage.contains("账号", ignoreCase = true) ||
            backendMessage.contains("account", ignoreCase = true) ||
            backendMessage.contains("邮箱", ignoreCase = true) ||
            backendMessage.contains("email", ignoreCase = true) -> R.string.error_login_invalid

        else -> R.string.error_login_failed
    }

    fun mapRegisterError(backendMessage: String): Int = when {
        backendMessage.contains("验证码", ignoreCase = true) ||
            backendMessage.contains("code", ignoreCase = true) ||
            backendMessage.contains("verification", ignoreCase = true) -> R.string.error_code_required

        else -> R.string.error_register_failed
    }

    fun mapForgotError(backendMessage: String): Int = when {
        backendMessage.contains("验证码", ignoreCase = true) ||
            backendMessage.contains("code", ignoreCase = true) ||
            backendMessage.contains("verification", ignoreCase = true) -> R.string.error_code_required

        else -> R.string.error_forgot_failed
    }

    fun mapSendCodeError(backendMessage: String): Int = R.string.error_email_send_failed

    fun isPendingOrderMessage(backendMessage: String?): Boolean = backendMessage != null &&
        (
            backendMessage.contains("未完成", ignoreCase = true) ||
                backendMessage.contains("未支付", ignoreCase = true) ||
                backendMessage.contains("pending", ignoreCase = true) ||
                backendMessage.contains("unpaid", ignoreCase = true)
            )

    fun mapOrderError(backendMessage: String?): Int = when {
        backendMessage == null -> R.string.error_order_failed

        isPendingOrderMessage(backendMessage) -> R.string.purchase_existing_order_message

        backendMessage.contains("优惠", ignoreCase = true) ||
            backendMessage.contains("coupon", ignoreCase = true) -> R.string.error_coupon_invalid

        backendMessage.contains("订单", ignoreCase = true) ||
            backendMessage.contains("order", ignoreCase = true) -> R.string.error_order_failed

        else -> R.string.error_order_failed
    }

    fun mapSubscribeError(backendMessage: String?): Int = when {
        backendMessage == null -> R.string.api_error_subscribe_info

        backendMessage.contains("网络", ignoreCase = true) ||
            backendMessage.contains("network", ignoreCase = true) ||
            backendMessage.contains("连接", ignoreCase = true) -> R.string.error_network

        else -> R.string.api_error_subscribe_info
    }

    fun mapServerError(backendMessage: String?): Int = R.string.error_server_load

    /**
     * 礼品卡兑换失败文案映射：识别后端常见错误描述转本地化提示；
     * 未识别返回 null（调用方决定透传后端原文还是兜底文案）。
     *
     * 三档必须分开，不能全塌到「无效或已过期」：「已使用」要告诉用户这张卡已被用掉
     * （不是他填错了），「不满足使用条件」要指向套餐/使用门槛，否则用户只会反复重填同一个码。
     * 顺序有意义：已使用 → 不满足条件 → 无效/过期，否则「已使用」会被「使用限制」类关键词抢走。
     * 未识别与「unknown / save failed」统一走既有 error_gift_card_generic（语义等同上游 failed）。
     */
    fun giftCardMessageRes(backendMessage: String?): Int? {
        val message = backendMessage ?: return null
        return when {
            message.contains("已使用", ignoreCase = true) ||
                message.contains("被使用", ignoreCase = true) ||
                message.contains("使用过", ignoreCase = true) ||
                message.contains("已兑换", ignoreCase = true) ||
                message.contains("already been used", ignoreCase = true) ||
                message.contains("already used", ignoreCase = true) ||
                message.contains("redeemed", ignoreCase = true) -> R.string.error_gift_card_used

            message.contains("使用限制", ignoreCase = true) ||
                message.contains("使用条件", ignoreCase = true) ||
                message.contains("不满足", ignoreCase = true) ||
                message.contains("limit", ignoreCase = true) ||
                message.contains("not suitable", ignoreCase = true) ||
                message.contains("eligible", ignoreCase = true) ||
                message.contains("condition", ignoreCase = true) -> R.string.error_gift_card_unavailable

            message.contains("不存在", ignoreCase = true) ||
                message.contains("不可用", ignoreCase = true) ||
                message.contains("停用", ignoreCase = true) ||
                message.contains("无效", ignoreCase = true) ||
                message.contains("过期", ignoreCase = true) ||
                message.contains("长度", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ||
                message.contains("gift card does not exist", ignoreCase = true) ||
                message.contains("gift card has expired", ignoreCase = true) ||
                message.contains("invalid", ignoreCase = true) ||
                message.contains("expired", ignoreCase = true) ||
                message.contains("length", ignoreCase = true) -> R.string.error_gift_card_invalid

            message.contains("unknown", ignoreCase = true) ||
                message.contains("save failed", ignoreCase = true) -> R.string.error_gift_card_generic

            else -> null
        }
    }

    fun networkError(): Int = R.string.error_network
}
