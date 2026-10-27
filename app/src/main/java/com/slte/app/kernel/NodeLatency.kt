package com.slte.app.kernel

import com.slte.app.kernel.api.KernelClash
import com.slte.app.kernel.api.KernelProxySort
import com.slte.app.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一批测速增量结果。
 *
 * @property fresh 本次新拿到结果的节点（key 为内核节点名）
 * @property all 到目前为止已知的全部结果
 * @property finished 是否已收尾
 */
data class NodeLatencyUpdate(
    val fresh: Map<String, Int>,
    val all: Map<String, Int>,
    val finished: Boolean,
)

/**
 * 节点测速：逐节点真实 urlTest，谁先出结果先推谁；读缓存会拿到健康检查历史值，故不采用。
 * 拨号失败与没回包统一按 [Constants.DELAY_TIMEOUT] 收尾，界面统一显示"超时"。
 */
fun KernelProxy.nodeLatencyStream(
    maxWaitMs: Long = LATENCY_MAX_WAIT_MS,
    concurrency: Int = LATENCY_CONCURRENCY,
): Flow<NodeLatencyUpdate> = flow {
    val clash = manager.awaitClash()
    if (clash == null) {
        emit(NodeLatencyUpdate(emptyMap(), emptyMap(), finished = true))
        return@flow
    }
    if (manager.selectorGroup() == null) {
        config.ensureProfile()
        clash.loadActiveProfile()
        if (manager.waitForSelectorGroup() == null) {
            emit(NodeLatencyUpdate(emptyMap(), emptyMap(), finished = true))
            return@flow
        }
    }

    val names = clash.testableProxyNames()
    if (names.isEmpty()) {
        emit(NodeLatencyUpdate(emptyMap(), emptyMap(), finished = true))
        return@flow
    }

    val known = mutableMapOf<String, Int>()
    withTimeoutOrNull(maxWaitMs) {
        names.chunked(concurrency).forEach { batch ->
            val results =
                coroutineScope {
                    batch.map { name -> async { name to measure(clash, name) } }.awaitAll()
                }
            results.forEach { (name, delay) ->
                known[name] = delay
                speedResultStore.saveSpeedResults(known.toMap())
                emit(NodeLatencyUpdate(fresh = mapOf(name to delay), all = known.toMap(), finished = false))
            }
        }
    }
    emit(NodeLatencyUpdate(emptyMap(), known.toMap(), finished = true))
}.flowOn(faultReporter.ioDispatcher)

/** 单节点实测：存活返回毫秒数；拨号失败与没回包统一按超时计。 */
private suspend fun measure(
    clash: KernelClash,
    name: String,
): Int = runCatching { clash.urlTest(name, Constants.NODE_URLTEST_TIMEOUT_MS) }
    .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
    .getOrNull()
    ?.delay
    ?.takeIf { it > 0 }
    ?: Constants.DELAY_TIMEOUT

/** 可测速的节点名：去掉组、DIRECT/REJECT 与 GLOBAL。 */
private fun KernelClash.testableProxyNames(): List<String> = queryProxyGroupNames(excludeNotSelectable = false)
    .asSequence()
    .filter { it != "GLOBAL" }
    .flatMap { group -> queryProxyGroup(group, KernelProxySort.DELAY).proxies.asSequence() }
    .filter { !it.isGroup && it.name != "DIRECT" && it.name != "REJECT" }
    .map { it.name }
    .distinct()
    .toList()

/** 测速并发上限：够快，又不至于把内核 IPC 打满。 */
const val LATENCY_CONCURRENCY = 8

/** 一次测速的最长等待时间；超时就用已有结果收尾。 */
const val LATENCY_MAX_WAIT_MS = 45_000L
