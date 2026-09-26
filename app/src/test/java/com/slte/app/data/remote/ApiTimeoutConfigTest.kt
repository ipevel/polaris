// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.utils.Constants
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * API 超时配置的不变量。
 *
 * 为什么值得单独钉住：OkHttp 4.x **没有 happy-eyeballs**，一个域名解析出多个地址时
 * 只能按顺序串行尝试，每个地址都要等满 connectTimeout 才换下一个。因此
 * `connectTimeout` 必须显著小于 `callTimeout`，否则一个被黑洞的地址就会吃光整个请求预算——
 * 2026-09-26 真机实证：面板全部 IPv4 被黑洞、IPv6 正常时，30s 连接超时 + 45s callTimeout
 * 让登录/流量/个人页**全部必然失败**（详见 Constants.API_CONNECT_TIMEOUT_SECONDS 注释）。
 */
class ApiTimeoutConfigTest {

    @Test
    fun `连接超时与整体超时留出足够的地址轮换空间`() {
        assertTrue(
            "连接超时必须小于读写/整体超时",
            Constants.API_CONNECT_TIMEOUT_SECONDS < Constants.API_TIMEOUT_SECONDS,
        )
        val attempts = Constants.API_CALL_TIMEOUT_SECONDS / Constants.API_CONNECT_TIMEOUT_SECONDS
        assertTrue(
            "一次请求内至少要能试 4 个地址（Cloudflare 常见 2-4 个 A 记录 + AAAA），当前只有 $attempts 次",
            attempts >= 4,
        )
    }
}
