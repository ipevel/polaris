// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.github.kr328.clash.core.model.UrlTestResult

/**
 * 单节点离线复核的失败分类。
 *
 * 只有 [OFFLINE] 会让节点页打离线角标；[TIMEOUT] 只是"没回包"，可能是一次抖动，
 * 标成离线会误伤活节点（用户下次看到的就是"这节点坏了"，其实还能用）。
 */
internal enum class KernelUrlTestFailure { TIMEOUT, OFFLINE }

/** 内核分类 → 应用分类；存活（空 kind）返回 null。 */
internal fun urlTestFailureOf(result: UrlTestResult): KernelUrlTestFailure? = when (result.kind) {
    UrlTestResult.KIND_OFFLINE -> KernelUrlTestFailure.OFFLINE
    UrlTestResult.KIND_TIMEOUT -> KernelUrlTestFailure.TIMEOUT
    else -> null
}

/**
 * 对单个节点跑一次真实测速，取失败分类。
 *
 * 只对**已经测速失败**的节点调用：它是最后一次确认，用来区分"真没了"和"这次抖动"。
 * 全量节点都跑一遍会让测速收尾变成两倍耗时，而且毫无必要——活节点的延迟数字
 * 已经说明它活着。
 */
internal suspend fun KernelProxy.urlTestFailureKind(name: String, timeoutMs: Int): KernelUrlTestFailure? = safe(null, "urlTest") {
    val clash = manager.clash() ?: return@safe null

    runCatching { clash.urlTest(name, timeoutMs) }.getOrNull()?.let(::urlTestFailureOf)
}

/** 上次探测确认离线的节点名（按节点名 = 内核 proxy 名存）。 */
fun KernelProxy.cachedOfflineNodes(): Set<String>? = speedResultStore.getOfflineNodes()

fun KernelProxy.saveOfflineNodes(names: Set<String>) = speedResultStore.saveOfflineNodes(names)
