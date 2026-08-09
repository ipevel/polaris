package com.slte.app

import com.slte.app.utils.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogSanitizeTest {
    @Test
    fun masksSensitiveVariants() {
        assertEquals("token=***", AppLog.sanitize("token=abc123"))
        assertEquals("token=***", AppLog.sanitize("TOKEN=abc123"))
        assertEquals("password=***", AppLog.sanitize("password=secret"))
        assertEquals("pwd=***", AppLog.sanitize("pwd=secret"))
        assertEquals("Bearer ***", AppLog.sanitize("Bearer eyJhbGciOiJIUzI1NiJ9.abc.def"))
        assertEquals("Authorization: ***", AppLog.sanitize("Authorization: eyJ0eXAiOiJKV1Q.abc.def"))
        assertEquals("auth_data=***", AppLog.sanitize("auth_data=abc"))
        assertEquals("url?token=***&x=1", AppLog.sanitize("url?token=abc&x=1"))
        assertEquals("\"token\":\"***\"", AppLog.sanitize("\"token\":\"abc123\""))
        assertEquals("subscribe_token=***", AppLog.sanitize("subscribe_token=xyz"))
        assertEquals("token: ***", AppLog.sanitize("token: abc"))
        assertEquals("联系邮箱 x***@example.com", AppLog.sanitize("联系邮箱 x@example.com"))
    }

    @Test
    fun keepsOrdinaryTextUnchanged() {
        assertEquals("hello world", AppLog.sanitize("hello world"))
        assertEquals("my_tokens_list", AppLog.sanitize("my_tokens_list"))
        assertEquals("已连接 5 个节点", AppLog.sanitize("已连接 5 个节点"))
        assertEquals("tokenless", AppLog.sanitize("tokenless"))
    }
}
