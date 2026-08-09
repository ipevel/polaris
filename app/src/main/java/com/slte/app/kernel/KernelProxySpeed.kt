package com.slte.app.kernel

import com.github.kr328.clash.core.model.ProxySort
import com.slte.app.utils.Constants
import kotlinx.coroutines.delay
import com.slte.app.utils.AppLog

/**
 * KernelProxy 测速扩展：节点测速、预热、自动测速。
 * 与 KernelProxy 同包，访问其 internal 成员。
 */

/** 节点测速：健康检查后返回 节点名 → 延迟毫秒（超时=999） */
suspend fun KernelProxy.speedTest(): Map<String, Int> = safe(emptyMap()) {
    val clash = manager.clash()
    if (clash == null) {
        AppLog.d("SLTE-Kernel", "speedTest: clash=null")
        return@safe emptyMap()
    }
    var group = selectorGroup()
    if (group == null) {
        // 未连接 VPN 时内核未加载配置：导入订阅 + 后台加载后即可测速
        config.ensureProfile()
        clash.loadActiveProfile()
        group = waitForGroups()
    }
    if (group == null) return@safe emptyMap()

    clash.healthCheck(group)

    val result = clash.queryProxyGroup(group, ProxySort.Delay)
        .proxies
        .asSequence()
        .filter { !it.isGroup && it.name != "DIRECT" && it.name != "REJECT" }
        .map { proxy ->
            proxy.name to normalizeDelay(proxy.delay)
        }
        .toMap()
    AppLog.d("SLTE-Kernel", "speedTest: group=$group result=$result")
    result
}

/** 测速并把结果写入本地缓存（行业惯例：结果持久化，重启后仍可排序展示） */
suspend fun KernelProxy.speedTestAndCache(): Map<String, Int> = safe(emptyMap()) {
    val delays = speedTest()
    // 全 999（热身未完成）时不覆盖已有缓存
    if (delays.isNotEmpty() && delays.values.any { it != 999 }) {
        speedResultStore.saveSpeedResults(delays)
    }
    delays
}

/** 应用启动时预热内核：导入并加载活动配置（分组/节点立即可用，无需等点击连接） */
suspend fun KernelProxy.warmUp(): Boolean = safe(false) {
    val clash = manager.clash() ?: return@safe false
    config.ensureProfile()
    clash.loadActiveProfile()
    true
}

/** 测速并等待真实延迟（非 999）：支付/更新后的内核热身，最多等待约 [maxDurationMs] */
suspend fun KernelProxy.speedTestUntilReady(maxDurationMs: Long = 30_000L): Map<String, Int> =
    safe(emptyMap()) {
        val deadline = System.currentTimeMillis() + maxDurationMs
        var delays = speedTestAndCache()
        while (delays.isEmpty() || delays.values.all { it == 999 }) {
            if (System.currentTimeMillis() >= deadline) break
            delay(2000)
            delays = speedTestAndCache()
        }
        delays
    }

/** 读取上次测速结果缓存（无网络请求） */
fun KernelProxy.cachedSpeedResults(): Map<String, Int>? = speedResultStore.getSpeedResults()

/** 启动自动测速：测速 + 缓存 + 自动选择（手动选择优先） */
suspend fun KernelProxy.runAutoSpeedTest(): Map<String, Int> = safe(emptyMap()) {
    AppLog.d("SLTE-Kernel", "runAutoSpeedTest: start")
    var delays = speedTestAndCache()
    // 内核配置加载是异步的：连接广播先于策略组就绪，分组为空时重试
    repeat(5) { attempt ->
        if (delays.isNotEmpty()) return@repeat
        AppLog.d("SLTE-Kernel", "runAutoSpeedTest: 分组未就绪，第 ${attempt + 1} 次重试")
        delay(1000)
        delays = speedTestAndCache()
    }
    AppLog.d("SLTE-Kernel", "runAutoSpeedTest: delays=$delays")
    if (delays.isNotEmpty()) {
        val info = serverInfo()
        if (info?.selection == null ||
            info.selection == Constants.SELECTION_AUTO ||
            info.selection == Constants.SELECTION_FALLBACK
        ) {
            selectAuto()
        }
    }
    delays
}
