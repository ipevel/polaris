// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

suspend fun KernelProxy.speedTest(): Map<String, Int> = safe(emptyMap(), "speedTest") {
    val clash = manager.clash()
    if (clash == null) {
        AppLog.d("Polaris-Kernel", "speedTest: clash=null")
        return@safe emptyMap()
    }
    if (selectorGroup() == null) {
        config.ensureProfile()
        clash.loadActiveProfile()
        if (waitForGroups() == null) return@safe emptyMap()
    }

    clash.healthCheckAll()

    var result = queryAllGroupDelays(clash)
    repeat(10) {
        if (result.values.all { it != Constants.DELAY_TIMEOUT }) return@safe result
        delay(500)
        result = queryAllGroupDelays(clash)
    }
    AppLog.d("Polaris-Kernel", "speedTest: result=$result")
    result
}

suspend fun KernelProxy.speedTestProgressive(
    onProgress: (Map<String, Int>) -> Unit,
): Map<String, Int> = safe(emptyMap(), "speedTestProgressive") {
    val clash = manager.clash()
    if (clash == null) {
        AppLog.d("Polaris-Kernel", "speedTestProgressive: clash=null")
        return@safe emptyMap()
    }
    if (selectorGroup() == null) {
        config.ensureProfile()
        clash.loadActiveProfile()
        if (waitForGroups() == null) return@safe emptyMap()
    }

    clash.healthCheckAll()

    var result = queryAllGroupDelays(clash)
    onProgress(result)
    repeat(20) {
        if (result.values.all { it != Constants.DELAY_TIMEOUT }) return@safe result
        delay(PROGRESS_POLL_INTERVAL_MS)
        result = queryAllGroupDelays(clash)
        if (result.isNotEmpty()) onProgress(result)
    }
    result
}

suspend fun KernelProxy.speedTestProgressiveAndCache(
    onProgress: (Map<String, Int>) -> Unit,
): Map<String, Int> {
    val delays = speedTestProgressive(onProgress)
    if (delays.isNotEmpty() && delays.values.any { it != Constants.DELAY_TIMEOUT }) {
        speedResultStore.saveSpeedResults(delays)
    }
    return delays
}

private fun KernelProxy.queryAllGroupDelays(clash: IClashManager): Map<String, Int> = clash
    .queryProxyGroupNames(excludeNotSelectable = false)
    .asSequence()
    .filter { it != "GLOBAL" }
    .flatMap { group ->
        clash.queryProxyGroup(group, ProxySort.Delay).proxies.asSequence()
    }.filter { !it.isGroup && it.name != "DIRECT" && it.name != "REJECT" }
    .fold(mutableMapOf()) { acc, proxy ->
        val delay = normalizeDelay(proxy.delay)

        val existing = acc[proxy.name]
        if (existing == null || existing == Constants.DELAY_TIMEOUT) acc[proxy.name] = delay
        acc
    }

private const val PROGRESS_POLL_INTERVAL_MS = 500L

private const val MAX_SPEED_TEST_ATTEMPTS = 5

suspend fun KernelProxy.speedTestAndCache(): Map<String, Int> = safe(emptyMap(), "speedTestAndCache") {
    val delays = speedTest()

    if (delays.isNotEmpty() && delays.values.any { it != Constants.DELAY_TIMEOUT }) {
        speedResultStore.saveSpeedResults(delays)
    }
    delays
}

suspend fun KernelProxy.warmUp(): Boolean = safe(false, "warmUp") {
    val clash = manager.clash() ?: return@safe false
    config.ensureProfile()
    clash.loadActiveProfile()
    true
}

suspend fun KernelProxy.speedTestUntilReady(maxDurationMs: Long = 60_000L): Map<String, Int> = safe(emptyMap(), "speedTestUntilReady") {
    var delays = speedTestAndCache()
    try {
        withTimeout(maxDurationMs) {
            var attempts = 1
            while (attempts < MAX_SPEED_TEST_ATTEMPTS &&
                (delays.isEmpty() || delays.values.all { it == Constants.DELAY_TIMEOUT })
            ) {
                delay(2000)
                delays = speedTestAndCache()
                attempts++
            }
        }
    } catch (e: TimeoutCancellationException) {
        AppLog.d("Polaris-Kernel", "speedTestUntilReady: ${maxDurationMs}ms 截止，返回当前结果")
    }
    delays
}

fun KernelProxy.cachedSpeedResults(): Map<String, Int>? = speedResultStore.getSpeedResults()

suspend fun KernelProxy.runAutoSpeedTest(): Map<String, Int> = safe(emptyMap(), "runAutoSpeedTest") {
    AppLog.d("Polaris-Kernel", "runAutoSpeedTest: start")
    var delays = speedTestAndCache()

    repeat(5) { attempt ->
        if (delays.isNotEmpty()) return@repeat
        AppLog.d("Polaris-Kernel", "runAutoSpeedTest: 分组未就绪，第 ${attempt + 1} 次重试")
        delay(1000)
        delays = speedTestAndCache()
    }
    AppLog.d("Polaris-Kernel", "runAutoSpeedTest: delays=$delays")
    if (delays.isNotEmpty()) {
        val info = serverInfo()
        if (info?.selection == null ||
            info.selection == SelectionType.AUTO ||
            info.selection == SelectionType.FALLBACK
        ) {
            selectAuto()
        }
    }
    delays
}
