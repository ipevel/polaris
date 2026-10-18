// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import com.slte.app.data.local.CredentialStore
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.LoginResponseDto
import com.slte.app.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * 缓存归属账号：同账号重新登录保留本地缓存（消除「无套餐/空节点」闪动），
 * 换账号或归属未知才清缓存。
 */
class AuthRepositoryCacheOwnerTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val authApi = mockk<AuthApi>(relaxed = true)
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val subscribeRepository = mockk<SubscribeRepository>(relaxed = true)

    private fun repository() = AuthRepository(
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

        verify(exactly = 0) { subscribeRepository.invalidateCache() }
    }

    @Test
    fun `换账号登录清空本地缓存`() = runTest(mainRule.dispatcher) {
        stubLoginSuccess()
        every { sessionStore.cacheOwner() } returns "old@b.c"

        repository().login("new@b.c", "pw")

        verify { subscribeRepository.invalidateCache() }
    }

    @Test
    fun `归属未知时清空本地缓存`() = runTest(mainRule.dispatcher) {
        stubLoginSuccess()
        every { sessionStore.cacheOwner() } returns null

        repository().login("a@b.c", "pw")

        verify { subscribeRepository.invalidateCache() }
    }
}
