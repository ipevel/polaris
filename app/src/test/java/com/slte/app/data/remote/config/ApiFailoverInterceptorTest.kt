package com.slte.app.data.remote.config

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiFailoverInterceptorTest {
    private lateinit var server1: MockWebServer
    private lateinit var server2: MockWebServer
    private lateinit var selector: EndpointSelector
    private lateinit var client: OkHttpClient

    private val primary: String get() = server1.url("/").toString()
    private val backup: String get() = server2.url("/").toString()

    private fun failoverConfig() = object : FailoverConfig {
        override val apiBaseUrl: String = primary

        override fun apiCandidates(primary: String): List<String> = listOf(primary, backup)
    }

    private fun ok() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{}")

    @Before
    fun setup() {
        server1 = MockWebServer().apply { start() }
        server2 = MockWebServer().apply { start() }
        selector = EndpointSelector()
        client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(ApiFailoverInterceptor(failoverConfig(), selector))
                .build()
    }

    @After
    fun teardown() {
        server1.shutdown()
        server2.shutdown()
    }

    @Test
    fun `GET主地址500时failover到备选`() {
        server1.enqueue(MockResponse().setResponseCode(500))
        server2.enqueue(ok())
        client
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(1, server1.requestCount)
        assertEquals(1, server2.requestCount)
    }

    @Test
    fun `POST故障码不自动重试`() {
        server1.enqueue(MockResponse().setResponseCode(500))
        val body = "{}".toRequestBody(null)
        client
            .newCall(
                Request
                    .Builder()
                    .url(server1.url("/api/v1/user/order"))
                    .post(body)
                    .build(),
            ).execute()
            .use { assertEquals(500, it.code) }

        assertEquals(0, server2.requestCount)
    }

    @Test
    fun `订阅YAML响应不误判为伪成功`() {
        server1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("proxies:\n  - name: HK01\n    type: ss\n"),
        )
        client
            .newCall(Request.Builder().url(server1.url("/api/v1/client/subscribe")).build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(1, server1.requestCount)
        assertEquals(0, server2.requestCount)
    }

    @Test
    fun `声明JSON返回HTML视为劫持并failover`() {
        server1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("<html><body>captive portal</body></html>"),
        )
        server2.enqueue(ok())
        client
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(1, server1.requestCount)
        assertEquals(1, server2.requestCount)
    }

    @Test
    fun `连续失败触发熔断后跳过该地址`() {
        repeat(3) {
            server1.enqueue(MockResponse().setResponseCode(500))
            server2.enqueue(ok())
            client
                .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
                .execute()
                .use { assertEquals(200, it.code) }
        }

        server2.enqueue(ok())
        client
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(3, server1.requestCount)
        assertEquals(4, server2.requestCount)
    }

    @Test
    fun `候选列表重复时全部故障不抛异常`() {
        val dupConfig =
            object : FailoverConfig {
                override val apiBaseUrl: String = primary

                override fun apiCandidates(primary: String): List<String> = listOf(primary, primary)
            }
        val dupClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(ApiFailoverInterceptor(dupConfig, selector))
                .build()
        server1.enqueue(MockResponse().setResponseCode(500))
        server1.enqueue(MockResponse().setResponseCode(500))
        dupClient
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(500, it.code) }
        assertEquals(2, server1.requestCount)
    }

    @Test
    fun `单候选故障时直接返回故障响应`() {
        val singleConfig =
            object : FailoverConfig {
                override val apiBaseUrl: String = primary

                override fun apiCandidates(primary: String): List<String> = listOf(primary)
            }
        val singleClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(ApiFailoverInterceptor(singleConfig, selector))
                .build()
        server1.enqueue(MockResponse().setResponseCode(500))
        singleClient
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(500, it.code) }
        assertEquals(1, server1.requestCount)
    }

    @Test
    fun `全部候选熔断后仍尝试主地址一次`() {
        val singleConfig =
            object : FailoverConfig {
                override val apiBaseUrl: String = primary

                override fun apiCandidates(primary: String): List<String> = listOf(primary)
            }
        val singleClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(ApiFailoverInterceptor(singleConfig, selector))
                .build()

        repeat(3) {
            server1.enqueue(MockResponse().setResponseCode(500))
            singleClient
                .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
                .execute()
                .use { assertEquals(500, it.code) }
        }
        assertEquals(3, server1.requestCount)

        // 唯一候选已进入熔断窗口：仍必须真的发一次请求，否则用户「点什么都是请求失败」
        // 且拿不到任何真实原因（2026-09-20 事故里的第二次症状）
        server1.enqueue(ok())
        singleClient
            .newCall(Request.Builder().url(server1.url("/api/v1/user/info")).build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(4, server1.requestCount)
    }
}
