package com.slte.app.data.remote

import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FormUrlEncodedInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val jsonMedia = "application/json".toMediaType()
    private val jsonUtf8Media = "application/json; charset=UTF-8".toMediaType()
    private val textMedia = "text/plain".toMediaType()

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(FormUrlEncodedInterceptor())
                .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `登录JSON体被改写成表单编码`() {
        val recorded =
            execute(
                path = "/api/v1/passport/auth/login",
                body = """{"email":"a@b.c","password":"pw123456"}""",
                contentType = jsonMedia,
            )

        val contentType = recorded.getHeader("Content-Type")
        assertTrue(
            "实际 Content-Type=$contentType",
            contentType != null && contentType.startsWith("application/x-www-form-urlencoded"),
        )
        assertEquals(
            mapOf("email" to "a@b.c", "password" to "pw123456"),
            fields(recorded),
        )
    }

    @Test
    fun `带charset的JSON内容类型同样被改写`() {
        val recorded =
            execute(
                path = "/api/v1/passport/auth/register",
                body = """{"email":"a@b.c","password":"pw123456"}""",
                contentType = jsonUtf8Media,
            )

        assertEquals(mapOf("email" to "a@b.c", "password" to "pw123456"), fields(recorded))
    }

    @Test
    fun `字段与null省略规则符合面板契约`() {
        val recorded =
            execute(
                path = "/api/v1/user/order/save",
                body = """{"plan_id":2,"period":"month_price","coupon_code":null}""",
                contentType = jsonMedia,
            )

        assertEquals(
            mapOf("plan_id" to "2", "period" to "month_price"),
            fields(recorded),
        )
    }

    @Test
    fun `嵌套对象与数组按Laravel语法展开`() {
        val recorded =
            execute(
                path = "/api/v1/user/ticket/save",
                body = """{"ticket":{"level":1},"tags":["a","b"],"plain":3}""",
                contentType = jsonMedia,
            )

        assertEquals(
            mapOf(
                "ticket[level]" to "1",
                "tags[0]" to "a",
                "tags[1]" to "b",
                "plain" to "3",
            ),
            fields(recorded),
        )
    }

    @Test
    fun `中文与特殊字符按UTF8正确编码`() {
        val recorded =
            execute(
                path = "/api/v1/user/ticket/save",
                body = """{"message":"你好 世界&测试=1","subject":"标题"}""",
                contentType = jsonMedia,
            )

        assertEquals(
            mapOf("message" to "你好 世界&测试=1", "subject" to "标题"),
            fields(recorded),
        )
    }

    @Test
    fun `GET请求无请求体不受影响`() {
        val recorded =
            execute(
                method = "GET",
                path = "/api/v1/guest/comm/config",
                body = null,
                contentType = null,
            )

        assertEquals("", recorded.body.readUtf8())
        assertNull(recorded.getHeader("Content-Type"))
    }

    @Test
    fun `非对象JSON体原样透传`() {
        val recorded =
            execute(
                path = "/api/v1/guest/comm/config",
                body = "[1,2]",
                contentType = jsonMedia,
            )

        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("[1,2]", recorded.body.readUtf8())
    }

    @Test
    fun `非JSON体原样透传`() {
        val recorded =
            execute(
                path = "/api/v1/guest/comm/config",
                body = "plain text",
                contentType = textMedia,
            )

        assertEquals("text/plain", recorded.getHeader("Content-Type"))
        assertEquals("plain text", recorded.body.readUtf8())
    }

    @Test
    fun `非法JSON体原样透传且不抛异常`() {
        val recorded =
            execute(
                path = "/api/v1/guest/comm/config",
                body = "{not json",
                contentType = jsonMedia,
            )

        assertEquals("{not json", recorded.body.readUtf8())
    }

    private fun execute(
        method: String = "POST",
        path: String,
        body: String?,
        contentType: MediaType?,
    ): RecordedRequest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"),
        )
        val builder = Request.Builder().url(server.url(path))
        if (body == null) {
            builder.method(method, null)
        } else {
            builder.method(method, body.toRequestBody(contentType))
        }
        client.newCall(builder.build()).execute().use { assertEquals(200, it.code) }
        return server.takeRequest()
    }

    /** 把表单体解回字段表；URLDecoder 会把 OkHttp 的空格 `+` 还原成空格 */
    private fun fields(recorded: RecordedRequest): Map<String, String> {
        val raw = recorded.body.readUtf8()
        if (raw.isEmpty()) return emptyMap()
        return raw.split("&").associate { pair ->
            val separator = pair.indexOf('=')
            decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
