package com.slte.app.data.repository

import com.slte.app.BuildConfig
import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.ServerNode
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import javax.inject.Singleton

internal object CachePolicy {
    const val SUBSCRIBE_INFO_TTL_MS = 30_000L

    const val USER_INFO_TTL_MS = 30_000L

    const val SERVER_NODES_TTL_MS = 30 * 60_000L

    fun isFresh(
        cachedAtMs: Long,
        nowMs: Long,
        ttlMs: Long,
    ): Boolean = cachedAtMs > 0L && nowMs - cachedAtMs < ttlMs
}

@Singleton
class ServerRepository
@Inject
constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) {

    suspend fun fetchServers(force: Boolean = false): Result<List<ServerNode>> = runApi {
        val cached = sessionStore.getServerNodes()
        val fetchedAt = sessionStore.getServerNodesFetchedAt()
        if (!force && cached != null && CachePolicy.isFresh(fetchedAt, System.currentTimeMillis(), CachePolicy.SERVER_NODES_TTL_MS)) {
            debugLog("fetchServers: 缓存未过期，直接返回 ${cached.size} 个节点")
            return@runApi cached
        }

        debugLog("fetchServers: 开始请求 API...")
        val nodes = authApi.fetchServers()
        debugLog("fetchServers: 返回 ${nodes.size} 个节点")
        if (nodes.isNotEmpty()) {
            sessionStore.saveServerNodes(nodes)
        }
        nodes
    }.recoverCatching { e ->
        if (force) throw e
        val cached = sessionStore.getServerNodes()
        if (cached != null) {
            AppLog.w("SLTE-Repo", "fetchServers: 网络失败,使用本地缓存 ${cached.size} 个节点: ${sanitizeLog(e.message ?: "")}")
            cached
        } else {
            throw e
        }
    }

    fun getCachedServers(): List<ServerNode>? = sessionStore.getServerNodes()

    fun invalidateCache() {
        sessionStore.clearServerNodes()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Repo", message)
        }
    }
}
