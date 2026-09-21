package com.slte.app.data.remote

import com.slte.app.BuildConfig
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.data.remote.config.RemoteConfigData
import com.slte.app.support.FakeAuthApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscribeSourceImplTest {
    private val sessionStore = SessionStore(InMemoryPreferences())
    private val remoteConfig = mockk<RemoteConfig>()
    private val authApi = CapturingAuthApi()
    private val source = SubscribeSourceImpl(sessionStore, authApi, remoteConfig)

    @Test
    fun `http订阅地址被拒且不发起请求`() = runTest {
        sessionStore.saveSubscribeUrl("http://api.example.com/api/v1/client/subscribe?token=x")

        assertNull(source.fetchSubscribeYaml())
        assertTrue("http 地址不得发起请求", authApi.requestedUrls.isEmpty())
    }

    @Test
    fun `账号下发的订阅地址即使主机不在编译期白名单也被采用`() = runTest {
        sessionStore.saveSubscribeUrl("https://sub.other-host.example/api/v1/client/subscribe?token=fresh")

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf("https://sub.other-host.example/api/v1/client/subscribe?token=fresh"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `http订阅地址被拒时回退到令牌拼接地址`() = runTest {
        sessionStore.saveSubscribeUrl("http://api.example.com/api/v1/client/subscribe?token=x")
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")
        every { remoteConfig.data } returns RemoteConfigData(apiBaseUrl = "https://api.example.com")
        every { remoteConfig.apiBaseUrl } returns "https://api.example.com"

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf("https://api.example.com${BuildConfig.SUBSCRIBE_PATH}?token=tok-123"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `账号下发的https地址被直接使用`() = runTest {
        sessionStore.saveSubscribeUrl("https://api.example.com/api/v1/client/subscribe?token=x")

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf("https://api.example.com/api/v1/client/subscribe?token=x"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `账号下发的任意https主机都被采用`() = runTest {
        sessionStore.saveSubscribeUrl("https://example.com/sub")
        assertNotNull(source.fetchSubscribeYaml())

        sessionStore.saveSubscribeUrl("https://node.example.com/sub")
        assertNotNull(source.fetchSubscribeYaml())

        assertEquals(
            listOf("https://example.com/sub", "https://node.example.com/sub"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `账号地址去除首尾空白后使用`() = runTest {
        sessionStore.saveSubscribeUrl("  https://api.example.com/sub?token=x  ")

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(listOf("https://api.example.com/sub?token=x"), authApi.requestedUrls)
    }

    @Test
    fun `无订阅地址时回退到令牌拼接地址`() = runTest {
        sessionStore.saveSubscribeUrl("   ")
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")
        every { remoteConfig.data } returns RemoteConfigData(apiBaseUrl = "https://api.example.com")
        every { remoteConfig.apiBaseUrl } returns "https://api.example.com"

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf("https://api.example.com${BuildConfig.SUBSCRIBE_PATH}?token=tok-123"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `账号下发地址下载失败时回退到令牌拼接地址`() = runTest {
        sessionStore.saveSubscribeUrl("https://dead.example.com/api/v1/client/subscribe?token=stale")
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")
        every { remoteConfig.data } returns RemoteConfigData(apiBaseUrl = "https://api.example.com")
        every { remoteConfig.apiBaseUrl } returns "https://api.example.com"

        val api = FailingHostAuthApi(failHost = "dead.example.com")
        val source = SubscribeSourceImpl(sessionStore, api, remoteConfig)

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf(
                "https://dead.example.com/api/v1/client/subscribe?token=stale",
                "https://api.example.com${BuildConfig.SUBSCRIBE_PATH}?token=tok-123",
            ),
            api.requestedUrls,
        )
    }

    @Test
    fun `全部候选地址失败时返回null且逐一轮询`() = runTest {
        sessionStore.saveSubscribeUrl("https://dead.example.com/sub")
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")
        every { remoteConfig.data } returns RemoteConfigData(apiBaseUrl = "https://api.example.com")
        every { remoteConfig.apiBaseUrl } returns "https://api.example.com"

        val api = AlwaysFailingAuthApi()
        val source = SubscribeSourceImpl(sessionStore, api, remoteConfig)

        assertNull(source.fetchSubscribeYaml())
        assertEquals("两个候选都应被尝试", 2, api.requestedUrls.size)
    }

    @Test
    fun `账号地址与兜底地址相同时只请求一次`() = runTest {
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")
        every { remoteConfig.data } returns RemoteConfigData(apiBaseUrl = "https://api.example.com")
        every { remoteConfig.apiBaseUrl } returns "https://api.example.com"
        sessionStore.saveSubscribeUrl("https://api.example.com${BuildConfig.SUBSCRIBE_PATH}?token=tok-123")

        assertNotNull(source.fetchSubscribeYaml())
        assertEquals(
            listOf("https://api.example.com${BuildConfig.SUBSCRIBE_PATH}?token=tok-123"),
            authApi.requestedUrls,
        )
    }

    @Test
    fun `getEmail直接读取会话邮箱`() {
        sessionStore.save(authData = "auth", email = "user@example.com", subscribeToken = "tok-123")

        assertEquals("user@example.com", source.getEmail())
    }

    @Test
    fun `订阅更新时间写回会话存储`() {
        source.saveSubscriptionUpdatedAt()

        assertTrue("订阅更新时间应落盘", sessionStore.getSubscriptionUpdatedAt() > 0L)
    }

    private class CapturingAuthApi : FakeAuthApi() {
        val requestedUrls = mutableListOf<String>()

        override suspend fun fetchSubscribeYaml(url: String): ResponseBody? {
            requestedUrls += url
            return "proxies: []".toResponseBody()
        }
    }

    private class FailingHostAuthApi(
        private val failHost: String,
    ) : FakeAuthApi() {
        val requestedUrls = mutableListOf<String>()

        override suspend fun fetchSubscribeYaml(url: String): ResponseBody? {
            requestedUrls += url
            if (url.contains(failHost)) throw java.io.IOException("host unreachable")
            return "proxies: []".toResponseBody()
        }
    }

    private class AlwaysFailingAuthApi : FakeAuthApi() {
        val requestedUrls = mutableListOf<String>()

        override suspend fun fetchSubscribeYaml(url: String): ResponseBody? {
            requestedUrls += url
            throw java.io.IOException("host unreachable")
        }
    }
}
