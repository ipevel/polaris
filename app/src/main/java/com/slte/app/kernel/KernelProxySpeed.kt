// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全组测速。
 *
 * 本地分流模式下**每个生成组都是 include-all**（成员 = 全部节点），因此
 * `healthCheckAll()` 会按「组数 × 节点数」重复拨测同一批节点：默认 10 个组时
 * 峰值并发 ≈ 10 × 10（每组 errgroup limit 10），全开 27 个组时 ≈ 310，而 App
 * 侧的收数窗口只有 10s——单组跑完要 `ceil(N/10) × ≤5s`，于是大量节点在读数时
 * 还没测完，`LastDelayForTestUrl` 返回 0xffff、被归一成「超时」。
 *
 * 因此结构组优先：只 await 一个结构组（自动选择 / 故障转移）。它的 provider
 * 同时覆盖内联节点与 proxy-provider（include-all 展开为 Proxies + Use），
 * 一次调用即可覆盖全部节点，且 **AIDL 返回即代表该组全部 provider 的 check
 * 已结束**（native/tunnel/connectivity.go 的 wg.Wait 之后才 complete）——
 * 这是唯一可靠的「测速完成」信号（healthCheckAll 是 fire-and-forget）。
 */
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

    structuralSpeedTest(clash) { }?.let { return@safe it }

    // 非本地分流模式（面板组结构）：保持既有 healthCheckAll + 轮询行为
    clash.healthCheckAll()
    var result = queryAllGroupDelays(clash, hasTestRun = true)
    repeat(10) {
        if (result.values.all { it != Constants.DELAY_TIMEOUT }) return@safe result
        delay(500)
        result = queryAllGroupDelays(clash, hasTestRun = true)
    }
    AppLog.d("Polaris-Kernel", "speedTest: groups=${result.size}")
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

    structuralSpeedTest(clash, onProgress)?.let { return@safe it }

    clash.healthCheckAll()
    var result = queryAllGroupDelays(clash, hasTestRun = true)
    onProgress(result)
    repeat(20) {
        if (result.values.all { it != Constants.DELAY_TIMEOUT }) return@safe result
        delay(PROGRESS_POLL_INTERVAL_MS)
        result = queryAllGroupDelays(clash, hasTestRun = true)
        if (result.isNotEmpty()) onProgress(result)
    }
    result
}

/**
 * 只 await 结构组的探测；返回 null 表示当前不是本地分流结构（调用方回落）。
 *
 * 期间按 [PROGRESS_POLL_INTERVAL_MS] 采样做渐进显示，但**完成判定只认
 * `healthCheck(group)` 的返回**，采样不参与判定。
 */
private suspend fun KernelProxy.structuralSpeedTest(
    clash: IClashManager,
    onProgress: (Map<String, Int>) -> Unit,
): Map<String, Int>? {
    val groupNames = runCatching { clash.queryProxyGroupNames(excludeNotSelectable = false) }
        .getOrDefault(emptyList())
    val structural = STRUCTURAL_GROUP_CANDIDATES.firstOrNull { groupNames.contains(it) }
        ?: return null

    val memberCount =
        runCatching {
            clash.queryProxyGroup(structural, ProxySort.Default).proxies.count { !it.isGroup }
        }.getOrDefault(0)
    val budget = healthCheckBudgetMs(memberCount)
    AppLog.d(
        "Polaris-Kernel",
        "structuralSpeedTest: group=$structural members=$memberCount budget=${budget}ms",
    )

    val completed =
        withTimeoutOrNull(budget) {
            coroutineScope {
                // 渐进采样：只用于 UI 显示，不参与完成判定
                val poll =
                    launch {
                        repeat((budget / PROGRESS_POLL_INTERVAL_MS).toInt()) {
                            val snapshot = queryAllGroupDelays(clash, hasTestRun = true)
                            if (snapshot.isNotEmpty()) onProgress(snapshot)
                            delay(PROGRESS_POLL_INTERVAL_MS)
                        }
                    }
                try {
                    clash.healthCheck(structural)
                } finally {
                    poll.cancel()
                }
            }
            true
        } ?: false

    val result = queryAllGroupDelays(clash, hasTestRun = completed)
    if (result.isNotEmpty()) onProgress(result)
    AppLog.d("Polaris-Kernel", "structuralSpeedTest: completed=$completed members=${result.size}")
    return result
}

/** 结构组候选：由内核生成本地分流时固定存在（routing_table.go GroupNameAuto/Fallback）。 */
private val STRUCTURAL_GROUP_CANDIDATES = listOf("自动选择", "故障转移")

/**
 * 单组健康检查的等待预算（毫秒）。来源：healthcheck.go 的 `errgroup.SetLimit(10)`
 * 与 `timeout == 0 → 5000ms`；生成层刻意不写 timeout（见 Go 单测
 * TestBuildHealthCheckBudgetDefaults），所以这里按 (成员数/10)×5s 估算再留 1.5 倍余量。
 */
internal fun healthCheckBudgetMs(memberCount: Int): Long {
    if (memberCount <= 0) return HEALTH_CHECK_MIN_BUDGET_MS
    val batches = (memberCount + 9) / 10
    val estimate = batches.toLong() * 5_000L * 3L / 2L
    return estimate.coerceIn(HEALTH_CHECK_MIN_BUDGET_MS, HEALTH_CHECK_MAX_BUDGET_MS)
}

/** 测速结果的收敛判定（纯函数，可单测）。空结果不得判为完成。 */
internal enum class SpeedTestOutcome { EMPTY, PARTIAL, COMPLETE }

internal fun speedTestOutcome(
    delays: Map<String, Int>,
    completed: Boolean,
): SpeedTestOutcome {
    if (delays.isEmpty()) return SpeedTestOutcome.EMPTY
    if (delays.values.all { it == Constants.DELAY_TIMEOUT || it <= Constants.DELAY_PENDING }) {
        return SpeedTestOutcome.EMPTY
    }
    return if (completed) SpeedTestOutcome.COMPLETE else SpeedTestOutcome.PARTIAL
}

/**
 * 只保留可确信的真实延迟后再落盘（纯函数，可单测）。
 *
 * 此前只要有一个节点非 999 就把整张表（含大批 999）写入，下次进页面又被
 * `withCachedDelays` 回填成「超时」→ 截断产生的假超时被固化、跨会话复现。
 * 这里同时保留上一轮的好值，避免「本轮超时把已知好节点抹掉」。
 */
internal fun storeableDelays(
    previous: Map<String, Int>?,
    fresh: Map<String, Int>,
): Map<String, Int> {
    fun usable(m: Map<String, Int>) = m.filterValues { it > Constants.DELAY_PENDING && it < Constants.DELAY_TIMEOUT }
    return usable(previous.orEmpty()) + usable(fresh)
}

suspend fun KernelProxy.speedTestProgressiveAndCache(
    onProgress: (Map<String, Int>) -> Unit,
): Map<String, Int> {
    val delays = speedTestProgressive(onProgress)
    val storeable = storeableDelays(speedResultStore.getSpeedResults(), delays)
    if (storeable.isNotEmpty()) {
        speedResultStore.saveSpeedResults(storeable)
    }
    return delays
}

private fun KernelProxy.queryAllGroupDelays(
    clash: IClashManager,
    hasTestRun: Boolean,
): Map<String, Int> = clash
    .queryProxyGroupNames(excludeNotSelectable = false)
    .asSequence()
    .filter { it != "GLOBAL" }
    .flatMap { group ->
        clash.queryProxyGroup(group, ProxySort.Delay).proxies.asSequence()
    }.filter { !it.isGroup && it.name != OUTBOUND_DIRECT && it.name != OUTBOUND_REJECT }
    .fold(mutableMapOf()) { acc, proxy ->
        val delay = resolveDelay(proxy.delay, proxy.tested || hasTestRun)

        val existing = acc[proxy.name]
        if (existing == null || existing == Constants.DELAY_TIMEOUT || existing == Constants.DELAY_PENDING) {
            acc[proxy.name] = delay
        }
        acc
    }

private const val PROGRESS_POLL_INTERVAL_MS = 500L

internal const val HEALTH_CHECK_MIN_BUDGET_MS = 15_000L

internal const val HEALTH_CHECK_MAX_BUDGET_MS = 300_000L

private const val MAX_SPEED_TEST_ATTEMPTS = 5

suspend fun KernelProxy.speedTestAndCache(): Map<String, Int> = safe(emptyMap(), "speedTestAndCache") {
    val delays = speedTest()

    val storeable = storeableDelays(speedResultStore.getSpeedResults(), delays)
    if (storeable.isNotEmpty()) {
        speedResultStore.saveSpeedResults(storeable)
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
