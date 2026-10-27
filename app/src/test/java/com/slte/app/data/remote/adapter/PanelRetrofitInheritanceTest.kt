package com.slte.app.data.remote.adapter

import com.slte.app.data.remote.adapter.xboard.XboardAuthApi
import com.slte.app.data.remote.adapter.xboard.XboardAuthRetrofit
import com.slte.app.data.remote.adapter.xboard.XboardUserPlanRetrofit
import com.slte.app.data.remote.adapter.xboard.XboardUserRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bAuthApi
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bAuthRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bUserPlanRetrofit
import com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bUserRetrofit
import com.slte.app.data.remote.api.SessionProbeResult
import com.slte.app.utils.Constants
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 面板接口继承共享接口后，Retrofit 是否仍能解析方法、路径注解与返回类型。
 *
 * 其余单测都用 mockk 顶替 Retrofit 接口，接口继承/类型别名这类问题只会在真实创建服务时暴露，
 * 这里用真 Retrofit + MockWebServer 走一遍（配置与 [com.slte.app.data.remote.BackendAdapterFactory] 保持一致）。
 */
class PanelRetrofitInheritanceTest {
    private lateinit var server: MockWebServer

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    fun retrofit(): Retrofit = Retrofit
        .Builder()
        .baseUrl(server.url("/api/v1/"))
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(json.asConverterFactory(Constants.JSON_MEDIA_TYPE))
        .build()

    fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `v2board 继承来的端点按共享路径与共享模型解析`() {
        server.enqueue(jsonResponse("""{"data":{"trade_no":"TN-1","total_amount":5000,"refund_amount":9}}"""))

        val retrofit = retrofit()
        val api =
            XiaoV2bAuthApi(
                authApi = retrofit.create(XiaoV2bAuthRetrofit::class.java),
                userApi = retrofit.create(XiaoV2bUserRetrofit::class.java),
                userPlanApi = retrofit.create(XiaoV2bUserPlanRetrofit::class.java),
            )

        val order = runBlocking { api.getOrderDetail("TN-1") }

        assertEquals("TN-1", order.tradeNo)
        assertEquals(5000, order.totalAmount)
        assertEquals(9, order.refundAmount)
        assertEquals("/api/v1/user/order/detail?trade_no=TN-1", server.takeRequest().path)
    }

    @Test
    fun `Xboard 继承来的端点按共享路径与共享模型解析`() {
        server.enqueue(jsonResponse("""{"status":"success","data":{"trade_no":"TN-2","total_amount":700,"surplus_credit":7}}"""))

        val retrofit = retrofit()
        val api =
            XboardAuthApi(
                authApi = retrofit.create(XboardAuthRetrofit::class.java),
                userApi = retrofit.create(XboardUserRetrofit::class.java),
                userPlanApi = retrofit.create(XboardUserPlanRetrofit::class.java),
            )

        val order = runBlocking { api.getOrderDetail("TN-2") }

        assertEquals("TN-2", order.tradeNo)
        assertEquals("Xboard 的退款金额取 surplus_credit", 7, order.refundAmount)
        assertEquals("/api/v1/user/order/detail?trade_no=TN-2", server.takeRequest().path)
    }

    @Test
    fun `面板专属端点各自解析到自己的路径`() {
        server.enqueue(jsonResponse("""{"data":true}"""))
        server.enqueue(jsonResponse("""{"status":"success","data":true}"""))

        val retrofit = retrofit()
        val xiaoUser = retrofit.create(XiaoV2bUserRetrofit::class.java)
        val xboardUser = retrofit.create(XboardUserRetrofit::class.java)

        runBlocking {
            xiaoUser.redeemGiftCard(
                com.slte.app.data.remote.adapter.xiaov2b.XiaoV2bGiftCardRedeemRequest("SLTE2026"),
            )
            xboardUser.redeemGiftCard(
                com.slte.app.data.remote.adapter.xboard.XboardGiftCardRedeemRequest("SLTE2026"),
            )
        }

        assertEquals("/api/v1/user/redeemgiftcard", server.takeRequest().path)
        assertEquals("/api/v1/user/gift-card/redeem", server.takeRequest().path)
    }

    @Test
    fun `会话探测端点走共享 userinfo 路径并原样返回状态码`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"未登录或登陆已过期"}"""),
        )

        val retrofit = retrofit()
        val api =
            XiaoV2bAuthApi(
                authApi = retrofit.create(XiaoV2bAuthRetrofit::class.java),
                userApi = retrofit.create(XiaoV2bUserRetrofit::class.java),
                userPlanApi = retrofit.create(XiaoV2bUserPlanRetrofit::class.java),
            )

        val result = runBlocking { api.probeSession() }

        assertEquals(SessionProbeResult.INVALID, result)
        assertEquals("/api/v1/user/info", server.takeRequest().path)
    }

    @Test
    fun `面板节点列表与用户信息解析到各自模型`() {
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":1,"name":"HK-01","type":"vless","host":"h.example.com","port":443,"server_port":8443,"uuid":"u-1","tls":1}]}""",
            ),
        )
        server.enqueue(jsonResponse("""{"data":{"email":"a@b.c","balance":1200,"remind_expire":true}}"""))
        server.enqueue(jsonResponse("""{"data":{"email":"a@b.c","balance":1200,"remind_expire":1}}"""))

        val retrofit = retrofit()
        val xiaoUser = retrofit.create(XiaoV2bUserRetrofit::class.java)
        val xboardUser = retrofit.create(XboardUserRetrofit::class.java)

        runBlocking {
            val node = xiaoUser.fetchServers().data.orEmpty().single().toServerNode()
            assertEquals("HK-01", node.name)
            assertEquals(8443, node.serverPort)

            val xboardRemind = xboardUser.fetchUserInfo().data!!.toDomainUserInfo()
            val xiaoRemind = xiaoUser.fetchUserInfo().data!!.toDomainUserInfo()
            assertEquals("布尔与 0/1 都归一化为 1", 1, xboardRemind.remindExpire)
            assertEquals(1, xiaoRemind.remindExpire)
        }
    }
}
