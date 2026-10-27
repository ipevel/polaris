// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val SHUTDOWN_TIMEOUT_MS = 2_000L

fun CoroutineScope.cancelAndJoinBlocking(timeoutMs: Long = SHUTDOWN_TIMEOUT_MS) {
    val job = coroutineContext.job

    job.cancel()

    val completed = runBlocking { withTimeoutOrNull(timeoutMs) { job.join() } }
    if (completed == null) {
        Log.w("Scope shutdown timed out after ${timeoutMs}ms")
    }
}

fun cancelAndJoinBlockingAsync(vararg scopes: CoroutineScope) {
    val targets = scopes.distinct()
    if (targets.isEmpty()) return

    Thread {
        targets.forEach { it.cancelAndJoinBlocking() }
    }.apply {
        isDaemon = true
        start()
    }
}