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

    // 直连模式（或配置本身没有策略组）下没有可测的对象：structuralSpeedTest 会返回
    // null，awaitSpeedSettle 又会把「空结果」当成 WAIT 空转满 60 次轮询（30s）。
    // 这里提前返回，避免订阅刷新后的后台测速白跑。
    val groupNames =
        runCatching { clash.queryProxyGroupNames(excludeNotSelectable = false) }
            .getOrDefault(emptyList())
    if (groupNames.isEmpty()) {
        AppLog.d("Polaris-Kernel", "speedTest: 无策略组，跳过")
        return@safe emptyMap()
    }

    // 本地分流模式只探测结构组（自动选择/故障转移）；面板组结构走 healthCheckAll
    // 覆盖全部 provider。两条路径的 healthCheck 都是 fire-and-forget——返回只代表
    // 任务已下发，延迟历史可能一条都还没写回，因此都必须经 awaitSpeedSettle 等到
    // 真实结果。此前本地分流分支在此直接 return，收敛轮询只在面板组结构下生效。
    val structural = structuralSpeedTest(clash) { }
    if (structural == null) {
        clash.healthCheckAll()
    }
    val result =
        awaitSpeedSettle(
            clash,
            structural ?: queryAllGroupDelays(clash, hasTestRun = true),
        )
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

    // 同 speedTest：无策略组（直连模式）时直接返回，否则 awaitSpeedSettle 空转 30s，
    // 节点页还会据此弹出「测速失败」提示。
    val groupNames =
        runCatching { clash.queryProxyGroupNames(excludeNotSelectable = false) }
            .getOrDefault(emptyList())
    if (groupNames.isEmpty()) {
        AppLog.d("Polaris-Kernel", "speedTestProgressive: 无策略组，跳过")
        return@safe emptyMap()
    }

    // 同 speedTest：本地分流分支不得短路，否则渐进测速同样读到空历史
    val structural = structuralSpeedTest(clash, onProgress)
    if (structural == null) {
        clash.healthCheckAll()
    }
    awaitSpeedSettle(
        clash,
        structural ?: queryAllGroupDelays(clash, hasTestRun = true),
        onProgress,
    )
}

/**
 * 只探测结构组（自动选择/故障转移）；返回 null 表示当前不是本地分流结构（调用方回落）。
 *
 * 本函数只负责**下发**探测并返回一份初始快照，收敛判定交给调用方的
 * [awaitSpeedSettle]：`healthCheck(group)` 是 fire-and-forget，返回即返回，
 * 此刻读到的延迟历史通常还是空的。把它当完成信号就会重演「26 个成员 458ms
 * 内测完、全部读成 999」的回归。
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

/** 收敛轮询的动作（纯函数 [speedSettleAction] 的判定结果）。 */
internal enum class SpeedSettleAction {
    /** 全部节点都拿到真实延迟，立即返回。 */
    DONE,

    /** 一条真实延迟都没有，且已等满最小沉降窗口：认作节点确实不可用。 */
    GIVE_UP,

    /** 继续轮询。 */
    WAIT,
}

/**
 * 测速收敛判定（纯函数，可单测）。
 *
 * 内核的 `healthCheck` 是 fire-and-forget：调用返回只表示探测任务已下发，
 * 延迟历史可能一条都还没写回。此时立刻去读，读到的全是
 * [Constants.DELAY_TIMEOUT]（999）——`queryAllGroupDelays` 在 `hasTestRun=true`
 * 时会把「尚无记录」也渲染成超时，于是节点页整片变红。
 *
 * 回归现场：26 个成员的探测在 458ms 内"完成"（healthCheck 立即返回，
 * 预算是 22500ms），全部读成 999，被误读为"节点全挂"。
 *
 * - 已有真实延迟 → 等全部收敛，避免把仍在途的节点固化成超时
 * - 尚无真实延迟 → 至少等满 [SPEED_SETTLE_MIN_POLLS] 次轮询再认输，
 *   否则无法区分"历史还没写回"与"节点真的挂了"
 * - 结果为空 → 继续等（空结果不得判为完成）
 */
internal fun speedSettleAction(
    delays: Map<String, Int>,
    polls: Int,
): SpeedSettleAction {
    if (delays.isEmpty()) return SpeedSettleAction.WAIT
    val settled = delays.values.count { it > Constants.DELAY_PENDING && it < Constants.DELAY_TIMEOUT }
    return when {
        settled == delays.size -> SpeedSettleAction.DONE
        settled == 0 && polls >= SPEED_SETTLE_MIN_POLLS -> SpeedSettleAction.GIVE_UP
        else -> SpeedSettleAction.WAIT
    }
}

/**
 * 轮询内核的延迟历史，直到 [speedSettleAction] 判定收敛或触达硬上限。
 *
 * 两条测速路径（本地分流结构组 / 面板组结构）都必须经此返回，
 * 不得把「healthCheck 已下发」当成「测速已完成」。
 */
private suspend fun KernelProxy.awaitSpeedSettle(
    clash: IClashManager,
    initial: Map<String, Int>,
    onProgress: ((Map<String, Int>) -> Unit)? = null,
): Map<String, Int> {
    var result = initial
    var polls = 0
    while (polls < SPEED_SETTLE_MAX_POLLS) {
        when (speedSettleAction(result, polls)) {
            SpeedSettleAction.DONE -> return result
            SpeedSettleAction.GIVE_UP -> {
                AppLog.w(
                    "Polaris-Kernel",
                    "awaitSpeedSettle: $polls 次轮询后仍无真实延迟，按不可用返回 $result.size 个节点",
                )
                return result
            }
            SpeedSettleAction.WAIT -> Unit
        }
        delay(SPEED_SETTLE_POLL_INTERVAL_MS)
        result = queryAllGroupDelays(clash, hasTestRun = true)
        if (result.isNotEmpty()) onProgress?.invoke(result)
        polls++
    }
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

/** 收敛轮询间隔：与渐进采样同频，实时反映内核写回历史的进度。 */
private const val SPEED_SETTLE_POLL_INTERVAL_MS = 500L

/**
 * 收敛轮询的最小沉降窗口（次）：一条真实延迟都没拿到时，至少等这么久再认输。
 *
 * 取 6 次 × 500ms = 3s，对应回归日志里真实测速的耗时（约 3.2s）。短于此，
 * 无法区分「历史还没写回」与「节点真的挂了」；长于此只会拖慢启动。
 */
internal const val SPEED_SETTLE_MIN_POLLS = 6

/** 收敛轮询的硬上限（次）：6 × 60 = 30s 封顶，防止个别节点拖住整页。 */
internal const val SPEED_SETTLE_MAX_POLLS = 60

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
