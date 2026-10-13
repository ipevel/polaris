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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // 覆盖全部 provider。结构组路径的 AIDL 返回即代表该组延迟历史确已写回（见文件头），
    // 面板组路径才是 fire-and-forget。两条路径都必须经 awaitSpeedSettle 收敛，
    // 只有结构组路径能把「内核这一轮确已结束」带进去，用于把剩余 999 判为终态。
    val probe = structuralSpeedTest(clash) { }
    if (probe == null) {
        clash.healthCheckAll()
    }
    val result =
        awaitSpeedSettle(
            clash = clash,
            initial = probe?.delays ?: queryAllGroupDelays(clash, hasTestRun = true),
            kernelRoundDone = probe?.completed == true,
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
    val probe = structuralSpeedTest(clash, onProgress)
    if (probe == null) {
        clash.healthCheckAll()
    }
    awaitSpeedSettle(
        clash = clash,
        initial = probe?.delays ?: queryAllGroupDelays(clash, hasTestRun = true),
        kernelRoundDone = probe?.completed == true,
        onProgress = onProgress,
    )
}

/**
 * 结构组探测结果：一份延迟快照 + 内核这一轮是否确已跑完（见 [structuralSpeedTest]）。
 */
internal data class StructuralProbe(
    val delays: Map<String, Int>,
    val completed: Boolean,
)

/**
 * 只探测结构组（自动选择/故障转移）；返回 null 表示当前不是本地分流结构（调用方回落）。
 *
 * `healthCheck(group)` 的 AIDL 返回**不是** fire-and-forget：native/tunnel.go 在
 * `tunnel.HealthCheck(name)`（内部对全部 provider 做 wg.Wait）之后才 complete，
 * 因此返回时该组 provider 的延迟历史确已写回。`completed=true` 可以作为
 * 「本轮结束」的信号，但只有结构组路径有这个保证（healthCheckAll 没有）。
 *
 * 「26 个成员 458ms 内测完」的真实成因不是 fire-and-forget，而是内核
 * healthcheck.go 里 provider 级 singledo 的 1s 结果缓存（singledo.go 的
 * `time.Since(result.Time) < s.wait`）：那次调用命中的是上一轮刚跑完的缓存，
 * 读到的历史同样是新鲜的。真正需要继续等的只有「一条真实延迟都没有」的情况。
 */
private suspend fun KernelProxy.structuralSpeedTest(
    clash: IClashManager,
    onProgress: (Map<String, Int>) -> Unit,
): StructuralProbe? {
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
    return StructuralProbe(result, completed)
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
 * `queryAllGroupDelays(hasTestRun = true)` 会把「尚无记录」和「测得但不可达」
 * 都渲染成 [Constants.DELAY_TIMEOUT]（999），单看这份快照无法区分二者。
 *
 * - 已有真实延迟且全部收敛 → 立即完成
 * - 内核已确认本轮结束（[kernelRoundDone]，见 [structuralSpeedTest]）且已有
 *   真实延迟 → 剩余的 999 是终态超时，不必再等满 [SPEED_SETTLE_MAX_POLLS]
 * - 尚无任何真实延迟 → 至少等满 [SPEED_SETTLE_MIN_POLLS] 次轮询再认输，
 *   否则无法区分「历史还没写回」与「节点真的挂了」
 * - 结果为空 → 继续等（空结果不得判为完成）
 *
 * 回归现场：29 个成员里 26 个真实延迟、3 个真超时，快照在 146ms 就已是终态
 * （命中的是内核 singledo 的 1s 缓存结果），旧判据却要等 999 全部变真实，
 * 结果白等满 30s。反向回归：内核异常即时返回时（缓存为空 / providers 为空）
 * 读到的是一份「一条真实延迟都没有」的快照，那时 [kernelRoundDone] 不足以判
 * 完成，仍走 [SPEED_SETTLE_MIN_POLLS] 认输路径，不会把整页判成超时。
 */
internal fun speedSettleAction(
    delays: Map<String, Int>,
    polls: Int,
    kernelRoundDone: Boolean = false,
): SpeedSettleAction {
    if (delays.isEmpty()) return SpeedSettleAction.WAIT
    val settled = delays.values.count { it > Constants.DELAY_PENDING && it < Constants.DELAY_TIMEOUT }
    return when {
        settled == delays.size -> SpeedSettleAction.DONE
        kernelRoundDone && settled > 0 && polls >= SPEED_SETTLE_CONFIRM_POLLS ->
            SpeedSettleAction.DONE
        settled == 0 && polls >= SPEED_SETTLE_MIN_POLLS -> SpeedSettleAction.GIVE_UP
        else -> SpeedSettleAction.WAIT
    }
}

/**
 * 轮询内核的延迟历史，直到 [speedSettleAction] 判定收敛或触达硬上限。
 *
 * 两条测速路径（本地分流结构组 / 面板组结构）都必须经此返回，
 * 不得把「healthCheck 已下发」当成「测速已完成」；[kernelRoundDone] 只在
 * 结构组路径上为 true（内核确认这一轮已结束）。
 */
private suspend fun KernelProxy.awaitSpeedSettle(
    clash: IClashManager,
    initial: Map<String, Int>,
    kernelRoundDone: Boolean = false,
    onProgress: ((Map<String, Int>) -> Unit)? = null,
): Map<String, Int> {
    var result = initial
    var polls = 0
    while (polls < SPEED_SETTLE_MAX_POLLS) {
        when (speedSettleAction(result, polls, kernelRoundDone)) {
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

/**
 * 渐进测速并落盘。
 *
 * @param force 忽略 [SpeedTestGate] 的去重窗口：用户手动测速、配置刚变更时必须真测
 * @param onProgress 每次采样回调（尾随 lambda）
 */
suspend fun KernelProxy.speedTestProgressiveAndCache(
    force: Boolean = false,
    onProgress: (Map<String, Int>) -> Unit,
): Map<String, Int> = SpeedTestGate.run(force = force, cached = { cachedSpeedResults().orEmpty() }) {
    val delays = speedTestProgressive(onProgress)
    val storeable = storeableDelays(speedResultStore.getSpeedResults(), delays)
    if (storeable.isNotEmpty()) {
        speedResultStore.saveSpeedResults(storeable)
    }
    delays
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

/**
 * 内核确认本轮结束后的确认窗口（次）：2 × 500ms = 1s。
 *
 * 到达该窗口即可把仍为 999 的节点判为真实超时（终态）：运行日志里 29 个成员
 * 只有 3 个真超时，旧判据为此白等了 33.4s。留 1s 是给最后几条历史写回留余量。
 */
internal const val SPEED_SETTLE_CONFIRM_POLLS = 2

/** 收敛轮询的硬上限（次）：6 × 60 = 30s 封顶，防止个别节点拖住整页。 */
internal const val SPEED_SETTLE_MAX_POLLS = 60

internal const val HEALTH_CHECK_MIN_BUDGET_MS = 15_000L

internal const val HEALTH_CHECK_MAX_BUDGET_MS = 300_000L

private const val MAX_SPEED_TEST_ATTEMPTS = 5

/**
 * 进程内的测速闸门：**同时只跑一轮，窗口内复用结果**。
 *
 * 三个入口此前各自独立下发结构组探测——连接后自动测速（MainViewModel）、
 * 节点页自动测速（ServerViewModel）、订阅更新后测速（SubscriptionUpdater）。
 * 内核侧的 singledo 只有 1s 结果缓存窗口，窗口之外的并发调用会真的把同一批
 * 节点再拨测一遍：运行日志里 45 秒内出现 4 轮 × 29 节点
 * （08:24:40.205 / 08:24:41.669 / 08:24:52.025 / 08:24:55.353）。
 *
 * - 窗口内已有一次测速 → 直接返回 [cached]，不再拨测
 * - [force] = true → 忽略窗口（配置刚变更、节点集合已变时旧结果无意义）
 * - 空结果不占窗口：内核没起来时不能让一次失败压住后续重试
 *
 * 锁在整个探测期间持有，因此并发入口是「排队后复用」而不是「一起拨测」。
 */
internal object SpeedTestGate {
    /** 去重窗口：比内核 singledo 的 1s 宽，覆盖连接后与进节点页这类连续触发。 */
    internal const val REUSE_WINDOW_MS = 15_000L

    private const val NOT_STARTED = Long.MIN_VALUE

    private val lock = Mutex()

    private var lastStartedAtMs = NOT_STARTED

    /** 可注入时钟（毫秒、单调），便于单测。 */
    internal var nowMs: () -> Long = { System.nanoTime() / 1_000_000L }

    /**
     * 串行执行一次测速；窗口内已跑过则返回 [cached]。
     *
     * @param force 忽略去重窗口，必须真测
     * @param cached 复用窗口命中时的返回值
     * @param block 真正的测速实现
     */
    suspend fun run(
        force: Boolean = false,
        cached: () -> Map<String, Int>,
        block: suspend () -> Map<String, Int>,
    ): Map<String, Int> = lock.withLock {
        val now = nowMs()
        if (!force && lastStartedAtMs != NOT_STARTED && now - lastStartedAtMs < REUSE_WINDOW_MS) {
            AppLog.d("Polaris-Kernel", "SpeedTestGate: ${now - lastStartedAtMs}ms 内已测过，复用结果")
            return@withLock cached()
        }
        lastStartedAtMs = now
        val result = block()
        if (result.isEmpty()) {
            // 空结果不算一轮（内核未就绪 / 无策略组），别压住后续重试
            lastStartedAtMs = NOT_STARTED
        }
        result
    }

    /** 单测用：清掉窗口状态。 */
    internal fun reset() {
        lastStartedAtMs = NOT_STARTED
    }
}

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

/**
 * 订阅更新后的后台测速：空结果时最多重试 [MAX_SPEED_TEST_ATTEMPTS] 次。
 *
 * @param force 配置刚变更（内核重载过 profile）时必须真测，见 [SpeedTestGate]
 */
suspend fun KernelProxy.speedTestUntilReady(
    maxDurationMs: Long = 60_000L,
    force: Boolean = false,
): Map<String, Int> = safe(emptyMap(), "speedTestUntilReady") {
    SpeedTestGate.run(force = force, cached = { cachedSpeedResults().orEmpty() }) {
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
}

fun KernelProxy.cachedSpeedResults(): Map<String, Int>? = speedResultStore.getSpeedResults()

suspend fun KernelProxy.runAutoSpeedTest(): Map<String, Int> = safe(emptyMap(), "runAutoSpeedTest") {
    SpeedTestGate.run(cached = { cachedSpeedResults().orEmpty() }) {
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
}
