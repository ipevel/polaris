// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.data.remote.api.ApiHeaders
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiHeadersAndPathsTest {
    private fun requestWith(headerLine: String): Request {
        val (name, value) = headerLine.split(":", limit = 2)
        return Request
            .Builder()
            .url("https://api.example.com")
            .header(name.trim(), value.trim())
            .build()
    }

    @Test
    fun `用户代理头拼装为合法的名称与值`() {
        val request = requestWith(ApiHeaders.USER_AGENT_HEADER)

        assertEquals("User-Agent", ApiHeaders.USER_AGENT_NAME)
        assertEquals("ClashMetaForAndroid/2.11.32", ApiHeaders.USER_AGENT_VALUE)
        assertEquals(
            ApiHeaders.USER_AGENT_HEADER,
            "${ApiHeaders.USER_AGENT_NAME}: ${ApiHeaders.USER_AGENT_VALUE}",
        )
        assertEquals(ApiHeaders.USER_AGENT_VALUE, request.header(ApiHeaders.USER_AGENT_NAME))
    }

    @Test
    fun `无故障转移头固定为常量名与值1`() {
        val request = requestWith(ApiHeaders.NO_FAILOVER_HEADER)

        assertEquals("X-Polaris-No-Failover", ApiHeaders.NO_FAILOVER_NAME)
        assertEquals(
            ApiHeaders.NO_FAILOVER_HEADER,
            "${ApiHeaders.NO_FAILOVER_NAME}: 1",
        )
        assertEquals("1", request.header(ApiHeaders.NO_FAILOVER_NAME))
    }

    @Test
    fun `路径前缀与各端点按统一前缀拼接`() {
        assertEquals("/api/v1", ApiPaths.PREFIX)
        assertEquals("/api/v1/user/", ApiPaths.AUTH)
        assertEquals("/api/v1/guest/comm/config", ApiPaths.GUEST_CONFIG)
        assertTrue(ApiPaths.AUTH.startsWith("${ApiPaths.PREFIX}/"))
        assertTrue(ApiPaths.GUEST_CONFIG.startsWith("${ApiPaths.PREFIX}/"))
    }

    @Test
    fun `认证前缀以斜杠结尾以便按路径前缀判定`() {
        assertTrue(ApiPaths.AUTH.endsWith("/"))

        assertTrue(AuthRules.isAuthPath(ApiPaths.AUTH + "login"))
        assertTrue(AuthRules.isAuthPath(ApiPaths.AUTH + "subscribe"))
        assertFalse(AuthRules.isAuthPath(ApiPaths.GUEST_CONFIG))
        assertFalse(AuthRules.isAuthPath(ApiPaths.PREFIX + "/subscribe/token"))
    }

    @Test
    fun `ApiBackend默认复用统一前缀且可覆盖`() {
        assertEquals(
            ApiPaths.PREFIX,
            ApiBackend(type = "xiaov2b", baseUrl = "https://api.example.com").apiPrefix,
        )
        assertEquals(
            "/custom",
            ApiBackend(type = "xiaov2b", baseUrl = "https://api.example.com", apiPrefix = "/custom").apiPrefix,
        )
    }
}
