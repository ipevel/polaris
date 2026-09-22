// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class FetchedConfig(
    val url: String,
    val raw: String,
    val version: String,
    val latencyMs: Long,
    val notModified: Boolean = false,
)

internal data class RaceResult(
    val chosen: FetchedConfig?,
)

internal object ConfigRace {
    suspend fun race(
        urls: List<String>,
        fetch: suspend (String) -> FetchedConfig?,
    ): RaceResult {
        if (urls.isEmpty()) return RaceResult(null)
        return coroutineScope {
            val results = urls.map { url -> async { url to fetch(url) } }.awaitAll()
            val valid = results.mapNotNull { (url, cfg) -> cfg?.copy(url = url) }
            val chosen = ConfigValidation.pickBest(valid)
            RaceResult(chosen = chosen)
        }
    }
}
