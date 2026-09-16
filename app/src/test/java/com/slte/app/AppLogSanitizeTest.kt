package com.slte.app

import com.slte.app.utils.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("the password is required", AppLog.sanitize("the password is required"))
        assertEquals("authorization failed: 401", AppLog.sanitize("authorization failed: 401"))
        assertEquals("token expired", AppLog.sanitize("token expired"))
        assertEquals("file:///data/local/tmp/app.log", AppLog.sanitize("file:///data/local/tmp/app.log"))
        assertEquals("节点 hk-01 延迟 120ms", AppLog.sanitize("节点 hk-01 延迟 120ms"))
        assertEquals("DIRECT 连接 1.2.3.4:443 ok", AppLog.sanitize("DIRECT 连接 1.2.3.4:443 ok"))
    }

    @Test
    fun masksJsonKeyFormSecrets() {
        assertEquals("\"password\":\"***\"", AppLog.sanitize("\"password\":\"secret\""))
        assertNoLeak("\"password\":\"secret\"", "secret")
        assertEquals("\"pwd\":\"***\"", AppLog.sanitize("\"pwd\":\"secret\""))
        assertNoLeak("\"pwd\":\"secret\"", "secret")
        assertEquals("\"passwd\":\"***\"", AppLog.sanitize("\"passwd\":\"secret\""))
        assertNoLeak("\"passwd\":\"secret\"", "secret")
        assertEquals("\"auth_data\":\"***\"", AppLog.sanitize("\"auth_data\":\"xyz\""))
        assertNoLeak("\"auth_data\":\"xyz\"", "xyz")
        assertEquals("\"authorization\":\"***\"", AppLog.sanitize("\"authorization\":\"abc123\""))
        assertNoLeak("\"authorization\":\"abc123\"", "abc123")
        assertEquals(
            "{\"user\":\"u\",\"password\":\"***\"}",
            AppLog.sanitize("{\"user\":\"u\",\"password\":\"p@ss\"}"),
        )
        assertNoLeak("{\"user\":\"u\",\"password\":\"p@ss\"}", "p@ss")
        assertEquals("{\"token\":\"***\",\"x\":1}", AppLog.sanitize("{\"token\":\"abc\",\"x\":1}"))
        assertNoLeak("{\"token\":\"abc\",\"x\":1}", "\"abc\"")
    }

    @Test
    fun masksBearerTokenAfterAuthorizationLabel() {
        assertEquals("Authorization: ***", AppLog.sanitize("Authorization: Bearer eyJabc.def"))
        assertNoLeak("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def", "eyJhbGciOiJIUzI1NiJ9")
        assertEquals("authorization: ***", AppLog.sanitize("authorization: bearer eyJabc.def"))
        assertNoLeak("authorization: bearer eyJabc.def", "eyJabc.def")
        assertEquals("Authorization: ***", AppLog.sanitize("Authorization: Basic dXNlcjpwYXNz"))
        assertNoLeak("Authorization: Basic dXNlcjpwYXNz", "dXNlcjpwYXNz")
    }

    @Test
    fun masksQuotedAndSpacedValues() {
        assertEquals("password: \"***\"", AppLog.sanitize("password: \"mypass\""))
        assertNoLeak("password: \"mypass\"", "mypass")
        assertEquals("token: '***'", AppLog.sanitize("token: 'abc123'"))
        assertNoLeak("token: 'abc123'", "abc123")
        assertEquals("password=\"***\"", AppLog.sanitize("password=\"mypass\""))
        assertNoLeak("password=\"mypass\"", "mypass")
        assertEquals("auth_data: '***'", AppLog.sanitize("auth_data: 'xyz'"))
        assertNoLeak("auth_data: 'xyz'", "xyz")
        assertEquals("token = ***", AppLog.sanitize("token = abc123"))
        assertNoLeak("token = abc123", "abc123")
        assertEquals("token : ***", AppLog.sanitize("token : abc123"))
        assertNoLeak("token : abc123", "abc123")
        assertEquals("{password=***, token=***}", AppLog.sanitize("{password=mypass, token=tok}"))
        assertNoLeak("{password=mypass, token=tok}", "mypass")
        assertEquals("subscribe_token : '***'", AppLog.sanitize("subscribe_token : 'SUB123'"))
        assertNoLeak("subscribe_token : 'SUB123'", "SUB123")
    }

    @Test
    fun masksCredentialsInNonHttpUrls() {
        assertEquals("trojan://***@1.2.3.4:443", AppLog.sanitize("trojan://realpass@1.2.3.4:443"))
        assertNoLeak("trojan://realpass@1.2.3.4:443", "realpass")
        assertEquals("socks5://***@1.2.3.4:1080", AppLog.sanitize("socks5://user:pw@1.2.3.4:1080"))
        assertNoLeak("socks5://user:pw@1.2.3.4:1080", "user:pw")
        assertEquals("ss://***@1.2.3.4:8388", AppLog.sanitize("ss://YWVzOnB3@1.2.3.4:8388"))
        assertNoLeak("ss://YWVzOnB3@1.2.3.4:8388", "YWVzOnB3")
        assertEquals("wss://***/ws", AppLog.sanitize("wss://relay.example.com/ws"))
        assertNoLeak("wss://relay.example.com/ws", "relay.example.com")
        assertEquals("https://***/x", AppLog.sanitize("https://user:pass@1.2.3.4/x"))
        assertNoLeak("https://user:pass@1.2.3.4/x", "user:pass")
        assertEquals(
            "[Proxy] trojan://***@1.2.3.4:443 连接成功",
            AppLog.sanitize("[Proxy] trojan://realpass@1.2.3.4:443 连接成功"),
        )
        assertNoLeak("[Proxy] trojan://realpass@1.2.3.4:443 连接成功", "realpass")
    }

    @Test
    fun sanitizeIsIdempotentSoExportStaysMasked() {
        val inputs =
            listOf(
                "\"password\":\"secret\"",
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def",
                "password: \"mypass\"",
                "trojan://realpass@1.2.3.4:443",
                "ss://YWVzOnB3@1.2.3.4:8388",
                "wss://relay.example.com/ws",
                "https://sub.example.com/sub?token=abc123",
                "url?token=abc&x=1",
                "联系邮箱 x@example.com",
                "tokenless",
            )
        inputs.forEach { input ->
            val once = AppLog.sanitize(input)
            assertEquals(once, AppLog.sanitize(once))
        }
    }

    private fun assertNoLeak(
        input: String,
        secret: String,
    ) {
        val out = AppLog.sanitize(input)
        assertFalse("明文泄漏: $secret 仍在 $out 中", out.contains(secret))
    }

    @Test
    fun masksUrlHostRegardlessOfConfiguredDomains() {
        assertEquals("https://***/api/v1/user/info", AppLog.sanitize("https://api.example.com/api/v1/user/info"))
        assertEquals("http://***/x", AppLog.sanitize("http://127.0.0.1:8080/x"))
        assertEquals("fetch https://***/generate_204 failed", AppLog.sanitize("fetch https://www.gstatic.com/generate_204 failed"))
    }

    @Test
    fun masksTokenInUrlTogetherWithHost() {
        assertEquals("https://***/sub?token=***", AppLog.sanitize("https://sub.example.com/sub?token=abc123"))
    }
}
