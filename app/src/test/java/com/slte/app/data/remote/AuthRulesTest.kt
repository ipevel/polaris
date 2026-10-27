// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {
    @Test
    fun `认证接口路径判定`() {
        assertTrue(AuthRules.isAuthPath("/api/v1/user/info"))
        assertTrue(AuthRules.isAuthPath("/api/v1/user/subscribe"))
        assertFalse(AuthRules.isAuthPath("/api/v1/subscribe/token"))
        assertFalse(AuthRules.isAuthPath("/api/v1/orders"))
        assertFalse(AuthRules.isAuthPath(""))
    }

    @Test
    fun `响应体文本判定登录态失效`() {
        assertTrue(AuthRules.isAuthFailureBodyText("未登录"))
        assertTrue(AuthRules.isAuthFailureBodyText("登陆已过期"))
        assertTrue(AuthRules.isAuthFailureBodyText("登录已过期"))
        assertTrue(AuthRules.isAuthFailureBodyText("UNAUTHORIZED"))
        assertTrue(AuthRules.isAuthFailureBodyText("TOKEN EXPIRED"))
        assertTrue(AuthRules.isAuthFailureBodyText("{\"message\":\"invalid token\"}"))
        assertFalse(AuthRules.isAuthFailureBodyText(""))
        assertFalse(AuthRules.isAuthFailureBodyText(null))
        assertFalse(AuthRules.isAuthFailureBodyText("无套餐"))
        assertFalse(AuthRules.isAuthFailureBodyText("订阅已过期"))
    }

    @Test
    fun `403只有面板JSON且含失效关键词才算会话失效`() {
        assertTrue(AuthRules.isSessionExpiryFor403("未登录"))
        assertTrue(AuthRules.isSessionExpiryFor403("""{"status":"fail","message":"未登录或登陆已过期"}"""))
        assertTrue(AuthRules.isSessionExpiryFor403("""{"message":"invalid token"}"""))

        // 边缘拦截页 / 空体 / 业务错误一律不算：
        // 一次 CDN 拦截就清会话并停 VPN，会把用户彻底锁在外面
        assertFalse(AuthRules.isSessionExpiryFor403("<html><body>Sorry, you have been blocked</body></html>"))
        assertFalse(AuthRules.isSessionExpiryFor403("\n  <!DOCTYPE html>"))
        assertFalse(AuthRules.isSessionExpiryFor403(""))
        assertFalse(AuthRules.isSessionExpiryFor403("   "))
        assertFalse(AuthRules.isSessionExpiryFor403(null))
        assertFalse(AuthRules.isSessionExpiryFor403("""{"msg":"余额不足"}"""))
        assertFalse(AuthRules.isSessionExpiryFor403("无套餐"))
    }

    @Test
    fun `注入token：有token处白名单且无Authorization头`() {
        assertTrue(AuthRules.decide("tok", isAllowedHost = true, hasAuthHeader = false, 200, false, true).attachToken)

        assertFalse(AuthRules.decide(null, true, false, 200, false, true).attachToken)

        assertFalse(AuthRules.decide("tok", false, false, 200, false, true).attachToken)

        assertFalse(AuthRules.decide("tok", true, true, 200, false, true).attachToken)
    }

    @Test
    fun `鉴权路径上的401清会话`() {
        assertTrue(AuthRules.decide("tok", true, false, 401, false, true).clearSession)

        assertTrue(AuthRules.decide("tok", true, false, 401, true, true).clearSession)

        assertFalse(AuthRules.decide(null, true, false, 401, false, true).clearSession)
    }

    @Test
    fun `非鉴权路径的401不清会话`() {
        // 订阅 CDN / WAF / 静态资源被拦一次就掉登录，是线上真实踩过的坑
        assertFalse(AuthRules.decide("tok", true, false, 401, false, false).clearSession)

        assertFalse(AuthRules.decide("tok", true, false, 401, true, false).clearSession)
    }

    @Test
    fun `非可信主机的401不清会话`() {
        // 重定向到了第三方主机，或凭据压根没送到那个主机上
        assertFalse(AuthRules.decide("tok", false, false, 401, false, true).clearSession)

        assertFalse(AuthRules.decide("tok", false, false, 401, true, true).clearSession)
    }

    @Test
    fun `403仅在响应体含失效关键词时清会话`() {
        assertTrue(AuthRules.decide("tok", true, false, 403, isAuthFailureBody = true, isAuthPath = true).clearSession)

        assertFalse(AuthRules.decide("tok", true, false, 403, isAuthFailureBody = false, isAuthPath = true).clearSession)

        // 非鉴权路径的 403 同样不该清会话（WAF 拦截页就是这种）
        assertFalse(AuthRules.decide("tok", true, false, 403, isAuthFailureBody = true, isAuthPath = false).clearSession)
    }

    @Test
    fun `200及非失效状态不清会话`() {
        assertFalse(AuthRules.decide("tok", true, false, 200, false, true).clearSession)
        assertFalse(AuthRules.decide("tok", true, false, 400, true, true).clearSession)
        assertFalse(AuthRules.decide("tok", true, false, 500, false, true).clearSession)
    }

    @Test
    fun `注入与清会话相互独立`() {
        val noToken = AuthRules.decide(null, true, false, 401, false, true)
        assertFalse(noToken.attachToken)
        assertFalse(noToken.clearSession)

        val withHeader = AuthRules.decide("tok", true, true, 401, false, true)
        assertFalse(withHeader.attachToken)
        assertTrue(withHeader.clearSession)

        val notAllowedHost = AuthRules.decide("tok", false, false, 401, false, true)
        assertFalse(notAllowedHost.attachToken)
        // 主机不可信时不再清会话：清完会话用户直接被锁在门外
        assertFalse(notAllowedHost.clearSession)
    }

    @Test
    fun `响应体Unicode转义后仍能判定登录态失效`() {
        // 面板实测返回：{"message":"\u672a\u767b\u5f55\u6216\u767b\u9646\u5df2\u8fc7\u671f"}（未登录或登陆已过期）
        val escaped = "{\"message\": \"\\u672a\\u767b\\u5f55\\u6216\\u767b\\u9646\\u5df2\\u8fc7\\u671f\"}"

        assertTrue(AuthRules.isAuthFailureBodyText(escaped))
        assertTrue(AuthRules.isAuthFailureBody(escaped))
        // 转义后仍不含失效关键词的业务消息不误判
        assertFalse(AuthRules.isAuthFailureBodyText("{\"message\":\"\\u8ba2\\u9605\\u5df2\\u8fc7\\u671f\"}"))
    }

    @Test
    fun `订阅下载携带本会话凭据返回401或403才判为疑似失效`() {
        val subscribePath = BuildConfig.SUBSCRIBE_PATH
        assertTrue(AuthRules.isSubscribePath(subscribePath))
        assertFalse(AuthRules.isSubscribePath("/api/v1/user/info"))

        // 白名单主机直连：Authorization 即本会话凭据
        assertTrue(
            AuthRules.isSubscribeCredentialFailure(403, subscribePath, null, "ad-1", "ad-1", "sub-1"),
        )
        // 自定义订阅域名：仅 token 查询参数匹配
        assertTrue(
            AuthRules.isSubscribeCredentialFailure(401, "/custom/sub", "sub-1", null, "ad-1", "sub-1"),
        )
        // 非 401/403 不算
        assertFalse(
            AuthRules.isSubscribeCredentialFailure(200, subscribePath, "sub-1", null, "ad-1", "sub-1"),
        )
        // 未携带本会话凭据（CDN/WAF 拦截）不算
        assertFalse(
            AuthRules.isSubscribeCredentialFailure(403, subscribePath, "other", null, "ad-1", "sub-1"),
        )
        // 非订阅路径且未匹配订阅 token（用户接口 401/403 由既有门控处理）
        assertFalse(
            AuthRules.isSubscribeCredentialFailure(403, "/api/v1/user/info", null, null, "ad-1", "sub-1"),
        )
    }
}
