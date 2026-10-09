// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.data.local.SessionStore
import com.slte.app.support.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthInterceptorTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val apiUrlStore = mockk<com.slte.app.data.local.ApiUrlStore>(relaxed = true)
    private val interceptor = AuthInterceptor(sessionStore, apiUrlStore)

    private fun request(
        url: String = "https://example.com${ApiPaths.AUTH}login",
        authorization: String? = null,
    ): Request {
        val builder = Request.Builder().url(url)
        if (authorization != null) builder.addHeader("Authorization", authorization)
        return builder.build()
    }

    private fun chain(
        request: Request,
        code: Int,
        body: String = "{}",
    ): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        // 用「实际发出的请求」构造响应：真实 OkHttp 的 response.request 就是带上
        // 注入凭据后的那一个。清会话判据里有「凭据确实送到了该主机」这一条，
        // 若这里回填原始 request，注入路径的用例会全部假失败。
        every { chain.proceed(any()) } answers {
            val sent = invocation.args[0] as Request
            Response
                .Builder()
                .request(sent)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body(body.toResponseBody())
                .build()
        }
        return chain
    }

    @Test
    fun `白名单主机无凭据头时注入token`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 200)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == "token-a" }) }
    }

    @Test
    fun `非白名单主机不注入凭据`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(url = "https://evil.example.org/api/v1/user/info"), code = 200)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == null }) }
    }

    @Test
    fun `已有凭据头时不覆盖`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(authorization = "token-existing"), code = 200)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == "token-existing" }) }
    }

    @Test
    fun `未登录时不注入也不清会话`() {
        every { sessionStore.getAuthData() } returns null
        val chain = chain(request(), code = 401)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.header("Authorization") == null }) }
        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `认证接口401且token未变时清会话并发出事件`() = runTest(mainRule.dispatcher) {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 401)
        val events = AtomicInteger()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interceptor.authErrorEvents.collect { events.incrementAndGet() }
        }

        interceptor.intercept(chain)

        verify { sessionStore.clear() }
        assertEquals(1, events.get())
    }

    @Test
    fun `401时token已被更换则不清会话`() = runTest(mainRule.dispatcher) {
        every { sessionStore.getAuthData() } returnsMany listOf("token-a", "token-b")
        val chain = chain(request(), code = 401)
        val events = AtomicInteger()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interceptor.authErrorEvents.collect { events.incrementAndGet() }
        }

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
        assertEquals(0, events.get())
    }

    @Test
    fun `认证接口403且响应体为失效关键词时清会话`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 403, body = """{"msg":"登录已过期"}""")

        interceptor.intercept(chain)

        verify { sessionStore.clear() }
    }

    @Test
    fun `认证接口403但响应体为业务错误时不清会话`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 403, body = """{"msg":"余额不足"}""")

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `认证接口403且响应体为边缘拦截页HTML时不清会话`() {
        // 2026-09-20 事故：面板被 Cloudflare 直连拦截返回 403 HTML，
        // 旧逻辑把它当会话失效 → 清会话 + 停 VPN，用户被彻底锁在外面。
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(
            request(),
            code = 403,
            body = "<html><body>Sorry, you have been blocked</body></html>",
        )

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `认证接口403且响应体为空时不清会话`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 403, body = "")

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `非认证接口的401不清会话`() {
        // 订阅、静态资源、guest 接口都在 /api/v1/user/ 之外：这些路径的 401
        // 只说明该请求被拒，不代表登录态失效（上游 7d0ac2e 的 401 路径门控）。
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(url = "https://example.com${ApiPaths.PREFIX}/guest/comm/config"), code = 401)

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `非白名单主机的401不清会话`() {
        // 订阅 CDN / WAF 也会返回 401：主机不可信时绝不能据此登出用户。
        every { sessionStore.getAuthData() } returns "token-a"
        every { apiUrlStore.isCurrentHost(any()) } returns false
        val chain = chain(request(url = "https://third-party.example.org/api/v1/user/info"), code = 401)

        interceptor.intercept(chain)

        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `自定义面板主机的鉴权401仍清会话`() {
        // 自建面板域名不在 BuildConfig 白名单里，但它就是用户当前登录的面板：
        // 主机可信判据必须把它算进来，否则自建面板的会话永远不会被清理。
        every { sessionStore.getAuthData() } returns "token-a"
        every { apiUrlStore.isCurrentHost("panel.example.org") } returns true
        val chain = chain(request(url = "https://panel.example.org/api/v1/user/info"), code = 401)

        interceptor.intercept(chain)

        verify { sessionStore.clear() }
    }

    @Test
    fun `响应对象原样返回`() {
        every { sessionStore.getAuthData() } returns "token-a"
        val chain = chain(request(), code = 200)

        val response = interceptor.intercept(chain)

        assertNotNull(response)
        assertEquals(200, response.code)
    }
}
