package com.slte.app.data.repository

import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.SubscribeInfoDto
import com.slte.app.data.remote.api.dto.UserInfoDto
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.User
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscribeRepositoryCacheTest {
    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher(scheduler))

    private val prefs = InMemoryPreferences()
    private val sessionStore = SessionStore(prefs)
    private val authApi = mockk<AuthApi>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val logoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val user =
        User(
            id = "jwt-token",
            displayName = "user@example.com",
            email = "user@example.com",
            authData = "jwt-token",
            subscribeToken = "sub-token",
        )
    private val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedIn(user))

    private val subscribeDto =
        SubscribeInfoDto(
            planId = 7,
            planName = "高级套餐",
            expiredAt = 1_800_000_000L,
            transferEnable = 100L,
            upload = 1L,
            download = 2L,
            resetDay = 1,
            subscribeUrl = "https://example.com/sub",
        )

    private val localInfo =
        SubscribeInfo(
            planName = "本地套餐",
            transferEnable = 100L,
            usedTraffic = 1L,
            expiredAt = 0L,
            planId = 7,
        )

    private val userDto = UserInfoDto(email = "user@example.com", balance = 1234)

    private lateinit var repository: SubscribeRepository

    @Before
    fun setUp() {
        every { sessionManager.sessionState } returns sessionState
        every { sessionManager.logoutEvents } returns logoutEvents
        repository = SubscribeRepository(authApi, sessionStore, sessionManager)
    }

    @Test
    fun `订阅信息 30 秒内复用缓存`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto

        assertTrue(repository.fetchSubscribeInfo().isSuccess)
        assertTrue(repository.fetchSubscribeInfo().isSuccess)

        coVerify(exactly = 1) { authApi.fetchSubscribeInfo() }
    }

    @Test
    fun `用户信息 30 秒内复用缓存`() = runTest(scheduler) {
        coEvery { authApi.fetchUserInfo() } returns userDto

        assertTrue(repository.fetchUserInfo().isSuccess)
        assertTrue(repository.fetchUserInfo().isSuccess)

        coVerify(exactly = 1) { authApi.fetchUserInfo() }
    }

    @Test
    fun `未登录时不请求也不命中缓存`() = runTest(scheduler) {
        sessionState.value = SessionState.LoggedOut

        assertTrue(repository.fetchSubscribeInfo().isFailure)
        assertTrue(repository.fetchUserInfo().isFailure)

        coVerify(exactly = 0) { authApi.fetchSubscribeInfo() }
        coVerify(exactly = 0) { authApi.fetchUserInfo() }
    }

    @Test
    fun `只有本地写入的用户缓存时不命中时间戳为零的缓存`() = runTest(scheduler) {
        coEvery { authApi.fetchUserInfo() } returns userDto

        repository.updateCachedUserInfo(user.copy(balance = "999.00"))

        assertEquals(user.copy(balance = "12.34"), repository.fetchUserInfo().getOrNull())
        coVerify(exactly = 1) { authApi.fetchUserInfo() }
    }

    @Test
    fun `拉取后本地写入用户信息在 30 秒内命中缓存`() = runTest(scheduler) {
        coEvery { authApi.fetchUserInfo() } returns userDto

        repository.fetchUserInfo()
        val updated = user.copy(balance = "99.00")
        repository.updateCachedUserInfo(updated)

        assertEquals(updated, repository.fetchUserInfo().getOrNull())
        coVerify(exactly = 1) { authApi.fetchUserInfo() }
    }

    @Test
    fun `force 绕过订阅与用户缓存`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto
        coEvery { authApi.fetchUserInfo() } returns userDto

        repository.fetchSubscribeInfo()
        repository.fetchSubscribeInfo(force = true)
        repository.fetchUserInfo()
        repository.fetchUserInfo(force = true)

        coVerify(exactly = 2) { authApi.fetchSubscribeInfo() }
        coVerify(exactly = 2) { authApi.fetchUserInfo() }
    }

    @Test
    fun `invalidateCache 清空内存缓存与本地数据`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto
        coEvery { authApi.fetchUserInfo() } returns userDto

        repository.fetchSubscribeInfo()
        repository.fetchUserInfo()
        assertNotNull(sessionStore.getSubscribeInfo())
        assertNotNull(sessionStore.getUserInfo())

        repository.invalidateCache()

        assertNull(sessionStore.getSubscribeInfo())
        assertNull(sessionStore.getUserInfo())

        repository.fetchSubscribeInfo()
        repository.fetchUserInfo()

        coVerify(exactly = 2) { authApi.fetchSubscribeInfo() }
        coVerify(exactly = 2) { authApi.fetchUserInfo() }
    }

    @Test
    fun `登出事件触发 invalidateCache`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto

        repository.fetchSubscribeInfo()
        assertNotNull(sessionStore.getSubscribeInfo())

        advanceUntilIdle()
        assertEquals(1, logoutEvents.subscriptionCount.value)

        logoutEvents.tryEmit(Unit)
        advanceUntilIdle()

        assertNull(sessionStore.getSubscribeInfo())

        repository.fetchSubscribeInfo()

        coVerify(exactly = 2) { authApi.fetchSubscribeInfo() }
    }

    @Test
    fun `订阅流以本地缓存为初始值`() = runTest(scheduler) {
        sessionStore.saveSubscribeInfo(localInfo)

        val fresh = SubscribeRepository(authApi, sessionStore, sessionManager)

        assertEquals(localInfo, fresh.subscribeInfo.value)
    }

    @Test
    fun `拉取成功后订阅流同步最新值`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto

        assertNull(repository.subscribeInfo.value)

        repository.fetchSubscribeInfo()

        assertEquals("高级套餐", repository.subscribeInfo.value?.planName)
        assertEquals(3L, repository.subscribeInfo.value?.usedTraffic)
    }

    @Test
    fun `invalidateCache 清空订阅流`() = runTest(scheduler) {
        coEvery { authApi.fetchSubscribeInfo() } returns subscribeDto
        repository.fetchSubscribeInfo()
        assertNotNull(repository.subscribeInfo.value)

        repository.invalidateCache()

        assertNull(repository.subscribeInfo.value)
    }
}
