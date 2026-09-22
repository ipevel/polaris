// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.utils

import com.slte.app.R
import com.slte.app.data.remote.ApiException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesMappingTest {

    @Test
    fun `异常自带资源ID优先于文案关键词`() {
        assertEquals(
            R.string.api_error_bad_response,
            ErrorMessages.forLogin(ApiException("该邮箱未注册", R.string.api_error_bad_response)),
        )
        assertEquals(
            R.string.error_login_failed,
            ErrorMessages.forRegister(ApiException("验证码错误", R.string.error_login_failed)),
        )
        assertEquals(
            R.string.error_network,
            ErrorMessages.forForgot(ApiException("code invalid", R.string.error_network)),
        )
        assertEquals(
            R.string.error_forgot_failed,
            ErrorMessages.forSendCode(ApiException("该邮箱未注册", R.string.error_forgot_failed)),
        )
        assertEquals(
            R.string.error_coupon_invalid,
            ErrorMessages.forOrder(ApiException("您有未完成的订单", R.string.error_coupon_invalid)),
        )
        assertEquals(
            R.string.error_server_load,
            ErrorMessages.forSubscribe(ApiException("网络异常", R.string.error_server_load)),
        )
        assertEquals(
            R.string.error_network,
            ErrorMessages.forServer(ApiException("任意文案", R.string.error_network)),
        )
    }

    @Test
    fun `登录入口命中账号不存在关键词`() {
        assertEquals(R.string.error_login_not_found, ErrorMessages.forLogin(ApiException("该邮箱不存在")))
        assertEquals(R.string.error_login_not_found, ErrorMessages.forLogin(ApiException("User not found")))
        assertEquals(R.string.error_login_not_found, ErrorMessages.forLogin(ApiException("邮箱未注册")))
        assertEquals(R.string.error_login_not_found, ErrorMessages.forLogin(ApiException("account not registered")))
        assertEquals(R.string.error_login_not_found, ErrorMessages.forLogin(ApiException("该账号不存在")))
    }

    @Test
    fun `登录入口命中凭证错误关键词`() {
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("密码错误")))
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("Password incorrect")))
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("账号被锁定")))
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("account locked")))
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("邮箱格式错误")))
        assertEquals(R.string.error_login_invalid, ErrorMessages.forLogin(ApiException("email is invalid")))
    }

    @Test
    fun `登录入口未命中关键词回退到通用失败`() {
        assertEquals(R.string.error_login_failed, ErrorMessages.forLogin(ApiException("验证码错误")))
        assertEquals(R.string.error_login_failed, ErrorMessages.forLogin(ApiException("The given data was invalid.")))
        assertEquals(R.string.error_login_failed, ErrorMessages.forLogin(ApiException("")))
        assertEquals(R.string.error_login_failed, ErrorMessages.forLogin(ApiException("请稍后重试")))
    }

    @Test
    fun `注册入口命中验证码关键词`() {
        assertEquals(R.string.error_code_required, ErrorMessages.forRegister(ApiException("验证码错误")))
        assertEquals(R.string.error_code_required, ErrorMessages.forRegister(ApiException("code invalid")))
        assertEquals(R.string.error_code_required, ErrorMessages.forRegister(ApiException("verification failed")))
        assertEquals(R.string.error_register_failed, ErrorMessages.forRegister(ApiException("该邮箱已注册")))
        assertEquals(R.string.error_register_failed, ErrorMessages.forRegister(ApiException("The given data was invalid.")))
    }

    @Test
    fun `找回密码入口命中验证码关键词`() {
        assertEquals(R.string.error_code_required, ErrorMessages.forForgot(ApiException("验证码已过期")))
        assertEquals(R.string.error_code_required, ErrorMessages.forForgot(ApiException("Code expired")))
        assertEquals(R.string.error_code_required, ErrorMessages.forForgot(ApiException("verification required")))
        assertEquals(R.string.error_forgot_failed, ErrorMessages.forForgot(ApiException("该邮箱不存在")))
        assertEquals(R.string.error_forgot_failed, ErrorMessages.forForgot(ApiException("")))
    }

    @Test
    fun `发送验证码入口恒为邮件发送失败`() {
        assertEquals(R.string.error_email_send_failed, ErrorMessages.forSendCode(ApiException("验证码错误")))
        assertEquals(R.string.error_email_send_failed, ErrorMessages.forSendCode(ApiException("该邮箱不存在")))
        assertEquals(R.string.error_email_send_failed, ErrorMessages.forSendCode(ApiException("")))
        assertEquals(R.string.error_email_send_failed, ErrorMessages.mapSendCodeError("任意文案"))
    }

    @Test
    fun `订单入口命中未完成或未支付关键词`() {
        assertEquals(
            R.string.purchase_existing_order_message,
            ErrorMessages.forOrder(ApiException("您有未完成的订单")),
        )
        assertEquals(
            R.string.purchase_existing_order_message,
            ErrorMessages.forOrder(ApiException("存在未支付订单")),
        )
        assertEquals(
            R.string.purchase_existing_order_message,
            ErrorMessages.forOrder(ApiException("pending order exists")),
        )
        assertEquals(
            R.string.purchase_existing_order_message,
            ErrorMessages.forOrder(ApiException("unpaid order")),
        )
    }

    @Test
    fun `订单入口命中优惠券关键词`() {
        assertEquals(R.string.error_coupon_invalid, ErrorMessages.forOrder(ApiException("优惠券不可用")))
        assertEquals(R.string.error_coupon_invalid, ErrorMessages.forOrder(ApiException("coupon expired")))
    }

    @Test
    fun `订单入口未知文案回退到订单失败`() {
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(ApiException("该订单不存在")))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(ApiException("order rejected")))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(ApiException("余额不足")))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(ApiException("")))
    }

    @Test
    fun `订阅入口命中网络关键词`() {
        assertEquals(R.string.error_network, ErrorMessages.forSubscribe(ApiException("网络异常")))
        assertEquals(R.string.error_network, ErrorMessages.forSubscribe(ApiException("network unreachable")))
        assertEquals(R.string.error_network, ErrorMessages.forSubscribe(ApiException("连接超时")))
        assertEquals(R.string.api_error_subscribe_info, ErrorMessages.forSubscribe(ApiException("订阅信息缺失")))
        assertEquals(R.string.api_error_subscribe_info, ErrorMessages.forSubscribe(ApiException("")))
    }

    @Test
    fun `服务端入口恒为服务端加载失败`() {
        assertEquals(R.string.error_server_load, ErrorMessages.forServer(ApiException("网络异常")))
        assertEquals(R.string.error_server_load, ErrorMessages.forServer(ApiException("")))
        assertEquals(R.string.error_server_load, ErrorMessages.mapServerError(null))
        assertEquals(R.string.error_server_load, ErrorMessages.mapServerError("任意文案"))
    }

    @Test
    fun `非API异常落到各入口兜底资源`() {
        assertEquals(R.string.error_network, ErrorMessages.forLogin(SocketTimeoutException("timeout")))
        assertEquals(R.string.error_network, ErrorMessages.forRegister(IOException("io")))
        assertEquals(R.string.error_network, ErrorMessages.forForgot(RuntimeException("boom")))
        assertEquals(R.string.error_network, ErrorMessages.forSendCode(Throwable("unknown")))
        assertEquals(R.string.error_network, ErrorMessages.forSubscribe(IOException("io")))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(RuntimeException("boom")))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(IOException("io")))
        assertEquals(R.string.error_server_load, ErrorMessages.forServer(RuntimeException("boom")))
        assertEquals(R.string.error_server_load, ErrorMessages.forServer(IOException("io")))
    }

    @Test
    fun `空输入落到各入口兜底资源`() {
        assertEquals(R.string.error_network, ErrorMessages.forLogin(null))
        assertEquals(R.string.error_network, ErrorMessages.forRegister(null))
        assertEquals(R.string.error_network, ErrorMessages.forForgot(null))
        assertEquals(R.string.error_network, ErrorMessages.forSendCode(null))
        assertEquals(R.string.error_order_failed, ErrorMessages.forOrder(null))
        assertEquals(R.string.error_network, ErrorMessages.forSubscribe(null))
        assertEquals(R.string.error_server_load, ErrorMessages.forServer(null))
    }

    @Test
    fun `映射函数直接接收空值时的兜底`() {
        assertEquals(R.string.error_order_failed, ErrorMessages.mapOrderError(null))
        assertEquals(R.string.api_error_subscribe_info, ErrorMessages.mapSubscribeError(null))
        assertEquals(R.string.purchase_existing_order_message, ErrorMessages.mapOrderError("未支付"))
    }

    @Test
    fun `待支付订单识别对大小写与无关文案的判定`() {
        assertTrue(ErrorMessages.isPendingOrderMessage("未完成"))
        assertTrue(ErrorMessages.isPendingOrderMessage("未支付"))
        assertTrue(ErrorMessages.isPendingOrderMessage("Pending"))
        assertTrue(ErrorMessages.isPendingOrderMessage("UNPAID"))
        assertFalse(ErrorMessages.isPendingOrderMessage("已支付订单"))
        assertFalse(ErrorMessages.isPendingOrderMessage("该订单不存在"))
        assertFalse(ErrorMessages.isPendingOrderMessage(""))
        assertFalse(ErrorMessages.isPendingOrderMessage(null))
    }

    @Test
    fun `通用网络错误入口返回网络资源`() {
        assertEquals(R.string.error_network, ErrorMessages.networkError())
    }
}
