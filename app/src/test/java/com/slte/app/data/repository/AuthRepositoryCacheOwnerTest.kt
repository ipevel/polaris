package com.slte.app.data.repository

import com.slte.app.data.local.CredentialStore
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.LoginResponseDto
import com.slte.app.domain.repository.SessionRepository
import com.slte.app.domain.repository.SubscribeRepository
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AuthRepositoryCacheOwnerTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val authApi = mockk<AuthApi>(relaxed = true)
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val sessionManager = mockk<SessionRepository>(relaxed = true)
    private val subscribeRepository = mockk<SubscribeRepository>(relaxed = true)

    private fun repository() = AuthRepositoryImpl(
        authApi = authApi,
        sessionStore = sessionStore,
        credentialStore = credentialStore,
        sessionManager = sessionManager,
        subscribeRepository = subscribeRepository,
    )

    private fun stubLoginSuccess() {
        coEvery { authApi.login(any(), any()) } returns LoginResponseDto(token = "t-1", authData = "ad-1")
    }

    @Test
    fun `同账号重新登录保留本地缓存`() = runTest(mainRule.dispatcher) {
        stubLoginSuccess()
        every { sessionStore.cacheOwner() } returns "a@b.c"

        repository().login("A@B.C", "pw")

        coVerify(exactly = 0) { subscribeRepository.invalidateCache() }
    }

    @Test
    fun `换账号登录清空本地缓存`() = runTest(mainRule.dispatcher) {
        stubLoginSuccess()
        every { sessionStore.cacheOwner() } returns "old@b.c"

        repository().login("new@b.c", "pw")

        coVerify { subscribeRepository.invalidateCache() }
    }

    @Test
    fun `归属未知时清空本地缓存`() = runTest(mainRule.dispatcher) {
        stubLoginSuccess()
        every { sessionStore.cacheOwner() } returns null

        repository().login("a@b.c", "pw")

        coVerify { subscribeRepository.invalidateCache() }
    }
}
