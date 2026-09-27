// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import com.slte.app.data.local.SessionStore
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.TrafficLogRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficRepository
@Inject
constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) {
    /** 当前后端是否提供每日流量明细（能力探测，不发请求）。 */
    fun supportsTrafficLog(): Boolean = authApi.supportsTrafficLog()

    suspend fun fetchTrafficLog(): Result<List<TrafficLogRecord>> = runApi {
        authApi.fetchTrafficLog()
    }

    /** 上次成功拉取的每日流量明细（磁盘缓存）；登录后先渲染它，再静默刷新。 */
    fun getCachedTrafficLog(): List<TrafficLogRecord>? = sessionStore.getTrafficLog()

    fun saveTrafficLog(records: List<TrafficLogRecord>) = sessionStore.saveTrafficLog(records)
}
