// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.TrafficLogRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficRepository
@Inject
constructor(
    private val authApi: AuthApi,
) {
    /** 当前后端是否提供每日流量明细（能力探测，不发请求）。 */
    fun supportsTrafficLog(): Boolean = authApi.supportsTrafficLog()

    suspend fun fetchTrafficLog(): Result<List<TrafficLogRecord>> = runApi {
        authApi.fetchTrafficLog()
    }
}
