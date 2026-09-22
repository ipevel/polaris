// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import com.slte.app.data.remote.ApiPaths
import com.slte.app.utils.AppLog
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request

internal class RemoteConfigProber(
    private val selector: EndpointSelector,
) {
    private val speedClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    suspend fun loop(intervalMs: Long) {
        while (true) {
            probeHalfOpen()
            delay(intervalMs)
        }
    }

    suspend fun probeAll(candidates: List<String>): Map<String, Long> {
        if (candidates.isEmpty()) return emptyMap()
        return coroutineScope {
            candidates
                .map { url ->
                    async(Dispatchers.IO) {
                        if (selector.isOpen(url)) return@async null
                        val latency = probeOne(url)
                        if (latency != null) url to latency else null
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    private suspend fun probeHalfOpen() {
        val candidates = selector.halfOpenCandidates()
        if (candidates.isEmpty()) return
        candidates.forEach { url ->
            val latency = probeOne(url)
            if (latency != null) {
                selector.recordProbe(url, latency)
                AppLog.i("Polaris-Config", "RemoteConfig: 半开探测恢复 latency=$latency")
            } else {
                selector.recordFailure(url)
                AppLog.w("Polaris-Config", "RemoteConfig: 半开探测仍失败")
            }
        }
    }

    private suspend fun probeOne(url: String): Long? {
        val start = System.currentTimeMillis()
        return try {
            speedClient
                .newCall(
                    Request.Builder().url(url.trimEnd('/') + PROBE_PATH).build(),
                ).execute()
                .use { resp ->
                    if (resp.code in 200..499) {
                        System.currentTimeMillis() - start
                    } else {
                        null
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val PROBE_PATH = ApiPaths.GUEST_CONFIG
    }
}
