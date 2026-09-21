package com.slte.app.data.remote

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
        assertTrue(AuthRules.decide("tok", isAllowedHost = true, hasAuthHeader = false, 200, false).attachToken)

        assertFalse(AuthRules.decide(null, true, false, 200, false).attachToken)

        assertFalse(AuthRules.decide("tok", false, false, 200, false).attachToken)

        assertFalse(AuthRules.decide("tok", true, true, 200, false).attachToken)
    }

    @Test
    fun `401始终清会话`() {
        assertTrue(AuthRules.decide("tok", true, false, 401, false).clearSession)

        assertTrue(AuthRules.decide("tok", true, false, 401, true).clearSession)

        assertFalse(AuthRules.decide(null, true, false, 401, false).clearSession)
    }

    @Test
    fun `403仅在响应体含失效关键词时清会话`() {
        assertTrue(AuthRules.decide("tok", true, false, 403, isAuthFailureBody = true).clearSession)

        assertFalse(AuthRules.decide("tok", true, false, 403, isAuthFailureBody = false).clearSession)
    }

    @Test
    fun `200及非失效状态不清会话`() {
        assertFalse(AuthRules.decide("tok", true, false, 200, false).clearSession)
        assertFalse(AuthRules.decide("tok", true, false, 400, true).clearSession)
        assertFalse(AuthRules.decide("tok", true, false, 500, false).clearSession)
    }

    @Test
    fun `注入与清会话相互独立`() {
        val noToken = AuthRules.decide(null, true, false, 401, false)
        assertFalse(noToken.attachToken)
        assertFalse(noToken.clearSession)

        val withHeader = AuthRules.decide("tok", true, true, 401, false)
        assertFalse(withHeader.attachToken)
        assertTrue(withHeader.clearSession)

        val notAllowedHost = AuthRules.decide("tok", false, false, 401, false)
        assertFalse(notAllowedHost.attachToken)
        assertTrue(notAllowedHost.clearSession)
    }
}
