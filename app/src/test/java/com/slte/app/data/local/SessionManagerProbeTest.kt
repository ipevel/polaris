package com.slte.app.data.local

import com.slte.app.data.remote.AuthInterceptor
import com.slte.app.data.remote.FallbackDns
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.SessionProbeResult
import com.slte.app.data.remote.config.CrispManager
import com.slte.app.domain.model.SessionNotice
import com.slte.app.domain.model.SessionState
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.api.KernelApi
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerProbeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val authInterceptor = mockk<AuthInterceptor>(relaxed = true)
    private val authApi = mockk<AuthApi>(relaxed = true)
    private val crispManager = mockk<CrispManager>(relaxed = true)
    private val kernelManager = mockk<KernelApi>(relaxed = true)
    private val kernelConfig = mockk<KernelConfig>(relaxed = true)
    private val fallbackDns = mockk<FallbackDns>(relaxed = true)

    private val suspectEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun manager(): SessionManager {
        every { authInterceptor.sessionSuspectEvents } returns suspectEvents
        every { authInterceptor.authErrorEvents } returns MutableSharedFlow()
        return SessionManager(
            sessionStore = sessionStore,
            authInterceptor = authInterceptor,
            authApi = authApi,
            crispManager = crispManager,
            kernelManager = kernelManager,
            kernelConfig = kernelConfig,
            fallbackDns = fallbackDns,
        )
    }

    fun stubLoggedInSession() {
        every { sessionStore.hasSession() } returns true
        every { sessionStore.getAuthData() } returns "ad-1"
        every { sessionStore.getEmail() } returns "a@b.c"
        every { sessionStore.getSubscribeToken() } returns "sub-1"
        every { sessionStore.getUserInfo() } returns null
    }

    @Test
    fun `冷启动探测确认失效后清会话并提示重新登录`() = runTest(mainRule.dispatcher) {
        stubLoggedInSession()
        coEvery { authApi.probeSession() } returns SessionProbeResult.INVALID
        val notices = mutableListOf<SessionNotice>()
        val manager = manager()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.sessionNotices.collect { notices += it }
        }

        advanceUntilIdle()

        assertTrue(manager.sessionState.value is SessionState.LoggedOut)
        verify { sessionStore.clear() }
        assertTrue(notices.contains(SessionNotice.EXPIRED))
    }

    @Test
    fun `订阅疑似失效事件经探测确认后清会话`() = runTest(mainRule.dispatcher) {
        stubLoggedInSession()
        coEvery { authApi.probeSession() } returnsMany
            listOf(SessionProbeResult.VALID, SessionProbeResult.INVALID)
        var now = 1_000L
        val manager = manager()
        manager.nowMillis = { now }

        advanceUntilIdle()
        assertTrue("冷启动探测有效时应保持登录", manager.sessionState.value is SessionState.LoggedIn)

        now += 60_000L
        suspectEvents.tryEmit(Unit)
        advanceUntilIdle()

        assertTrue(manager.sessionState.value is SessionState.LoggedOut)
        coVerify(exactly = 2) { authApi.probeSession() }
        verify { sessionStore.clear() }
    }

    @Test
    fun `探测在最短间隔内不重复发起`() = runTest(mainRule.dispatcher) {
        stubLoggedInSession()
        coEvery { authApi.probeSession() } returns SessionProbeResult.VALID
        val manager = manager()
        manager.nowMillis = { 1_000L }

        advanceUntilIdle()

        suspectEvents.tryEmit(Unit)
        suspectEvents.tryEmit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authApi.probeSession() }
    }

    @Test
    fun `探测结果未知时保持登录`() = runTest(mainRule.dispatcher) {
        stubLoggedInSession()
        coEvery { authApi.probeSession() } returns SessionProbeResult.UNKNOWN
        val manager = manager()

        advanceUntilIdle()

        assertTrue(manager.sessionState.value is SessionState.LoggedIn)
        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `探测期间已重新登录则不清新会话`() = runTest(mainRule.dispatcher) {
        every { sessionStore.hasSession() } returns true
        every { sessionStore.getAuthData() } returnsMany listOf("ad-1", "ad-1", "ad-2")
        every { sessionStore.getEmail() } returns "a@b.c"
        every { sessionStore.getSubscribeToken() } returns "sub-1"
        every { sessionStore.getUserInfo() } returns null
        coEvery { authApi.probeSession() } returns SessionProbeResult.INVALID
        val manager = manager()

        advanceUntilIdle()

        assertTrue(manager.sessionState.value is SessionState.LoggedIn)
        verify(exactly = 0) { sessionStore.clear() }
    }

    @Test
    fun `无会话时不发起探测`() = runTest(mainRule.dispatcher) {
        every { sessionStore.hasSession() } returns false
        val manager = manager()

        advanceUntilIdle()

        assertTrue(manager.sessionState.value is SessionState.LoggedOut)
        coVerify(exactly = 0) { authApi.probeSession() }
    }
}
