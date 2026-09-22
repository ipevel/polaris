// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.ServerNode
import com.slte.app.domain.model.ServerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRepositoryCacheTest {
    private val prefs = InMemoryPreferences()
    private val sessionStore = SessionStore(prefs)
    private val authApi = mockk<AuthApi>()
    private val repository = ServerRepository(authApi, sessionStore)

    private val nodes =
        listOf(
            ServerNode(id = 1, name = "香港 01", type = ServerType.SHADOWSOCKS, host = "1.2.3.4", port = 8388, cipher = "aes-128-gcm", password = "secret"),
            ServerNode(id = 2, name = "日本 01", type = ServerType.VMESS, host = "5.6.7.8", port = 8389, uuid = "uuid-2"),
        )

    @Test
    fun `30 分钟内命中缓存不请求 API`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        assertEquals(nodes, repository.fetchServers().getOrNull())
        assertEquals(nodes, repository.fetchServers().getOrNull())

        coVerify(exactly = 1) { authApi.fetchServers() }
    }

    @Test
    fun `恰好 30 分钟时缓存过期并重新请求`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        prefs.edit().putLong("server_nodes_fetched_at", System.currentTimeMillis() - CachePolicy.SERVER_NODES_TTL_MS).apply()
        repository.fetchServers()

        coVerify(exactly = 2) { authApi.fetchServers() }
    }

    @Test
    fun `fetchedAt 为 0 时视为过期并重新请求`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        prefs.edit().putLong("server_nodes_fetched_at", 0L).apply()
        repository.fetchServers()

        coVerify(exactly = 2) { authApi.fetchServers() }
    }

    @Test
    fun `force 绕过缓存`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        repository.fetchServers(force = true)

        coVerify(exactly = 2) { authApi.fetchServers() }
    }

    @Test
    fun `invalidateCache 清空节点缓存并重新请求`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        repository.invalidateCache()

        assertNull(sessionStore.getServerNodes())
        assertEquals(0L, sessionStore.getServerNodesFetchedAt())

        repository.fetchServers()
        coVerify(exactly = 2) { authApi.fetchServers() }
    }

    @Test
    fun `非 force 请求失败时回退本地缓存`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        prefs.edit().putLong("server_nodes_fetched_at", 0L).apply()
        coEvery { authApi.fetchServers() } throws IllegalStateException("boom")

        val result = repository.fetchServers()

        assertTrue(result.isSuccess)
        assertEquals(nodes, result.getOrNull())
    }

    @Test
    fun `force 请求失败时不回退缓存`() = runBlocking {
        coEvery { authApi.fetchServers() } returns nodes

        repository.fetchServers()
        coEvery { authApi.fetchServers() } throws IllegalStateException("boom")

        assertTrue(repository.fetchServers(force = true).isFailure)
    }
}
