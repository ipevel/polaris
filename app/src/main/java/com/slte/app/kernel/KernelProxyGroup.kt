package com.slte.app.kernel

import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants

suspend fun KernelProxy.selectNode(name: String): Boolean = safe(false, "selectNode") {
    val clash = manager.clash() ?: return@safe false
    val group = selectorGroup() ?: return@safe false

    val proxy =
        clash
            .queryProxyGroup(group, ProxySort.Default)
            .proxies
            .firstOrNull { it.name == name } ?: return@safe false

    val result = clash.patchSelector(group, proxy.name)
    AppLog.d("Polaris-Kernel", "selectNode: group=$group proxy=${proxy.name} result=$result")
    patchGlobalIfGlobal(proxy.name)
    result
}

suspend fun KernelProxy.selectAuto(): Boolean = safe(false, "selectAuto") {
    val result = selectSpecialGroup("URLTest", "自动", "auto", "url")
    autoGroupName()?.let { patchGlobalIfGlobal(it) }
    result
}

suspend fun KernelProxy.selectFallback(): Boolean = safe(false, "selectFallback") {
    val result = selectSpecialGroup("Fallback", "故障", "fallback")
    fallbackGroupName()?.let { patchGlobalIfGlobal(it) }
    result
}

suspend fun KernelProxy.serverInfo(): KernelServerInfo? = safe(null, "serverInfo") {
    val clash = manager.clash() ?: return@safe null
    val selector = selectorGroup() ?: return@safe null
    val state = clash.queryProxyGroup(selector, ProxySort.Default)
    val now = state.now
    AppLog.d("Polaris-Kernel", "serverInfo: selector=$selector now=$now type=${state.type}")
    if (now.isBlank()) return@safe KernelServerInfo(null, null)

    val autoGroup = autoGroupName()
    val fallbackGroup = fallbackGroupName()

    val selection =
        when (now) {
            autoGroup -> SelectionType.AUTO
            fallbackGroup -> SelectionType.FALLBACK
            else -> SelectionType.MANUAL
        }
    val node =
        if (state.proxies.any { !it.isGroup && it.name == now }) {
            now
        } else {
            clash.queryProxyGroup(now, ProxySort.Default).now.ifBlank { null }
        }
    AppLog.d("Polaris-Kernel", "serverInfo: selection=$selection node=$node")
    KernelServerInfo(selection, node)
}

/** 策略组内的一个候选成员（可能是节点，也可能是另一个策略组）。 */
data class KernelProxyMember(
    val name: String,
    val isGroup: Boolean,
    val delay: Int?,
)

/** 一个策略组的快照，用于「可分流的选择」界面。 */
data class KernelProxyGroupInfo(
    val name: String,
    val type: String,
    val now: String?,
    val selectable: Boolean,
    val members: List<KernelProxyMember>,
)

/**
 * 读取内核中全部策略组（排除 GLOBAL）。
 *
 * 此前的 selectorGroup() 只返回第一个组，多策略组订阅（如按应用分组的
 * 电报/AI/油管/奈飞/GitHub 等）无法分别调整，用户表现为"没地方调规则"。
 */
suspend fun KernelProxy.proxyGroups(): List<KernelProxyGroupInfo> = safe(emptyList(), "proxyGroups") {
    val clash = manager.clash() ?: return@safe emptyList()
    clash
        .queryProxyGroupNames(excludeNotSelectable = false)
        .filter { it != GLOBAL_GROUP }
        .mapNotNull { groupName ->
            runCatching {
                val group = clash.queryProxyGroup(groupName, ProxySort.Default)
                KernelProxyGroupInfo(
                    name = groupName,
                    type = group.type,
                    now = group.now.takeIf { it.isNotBlank() },
                    selectable = group.type.equals(GROUP_TYPE_SELECTOR, ignoreCase = true),
                    members =
                    group.proxies
                        .filter { it.name != groupName }
                        .map { proxy ->
                            if (proxy.name.contains("**")) {
                                AppLog.d("Polaris-Kernel", "proxyGroups: member name 含粗体标记=${proxy.name}")
                            }
                            KernelProxyMember(
                                name = proxy.name,
                                isGroup = proxy.isGroup,
                                delay = normalizeDelay(proxy.delay),
                            )
                        },
                )
            }.getOrNull()
        }
}

/** 在指定策略组内切换选中项。仅 Selector 类型支持手动切换。 */
suspend fun KernelProxy.selectInGroup(
    groupName: String,
    proxyName: String,
): Boolean = safe(false, "selectInGroup") {
    val clash = manager.clash() ?: return@safe false
    val group = clash.queryProxyGroup(groupName, ProxySort.Default)
    if (!group.type.equals(GROUP_TYPE_SELECTOR, ignoreCase = true)) {
        AppLog.w("Polaris-Kernel", "selectInGroup: 组 $groupName 类型 ${group.type} 不支持手动切换")
        return@safe false
    }
    if (group.proxies.none { it.name == proxyName }) {
        AppLog.w("Polaris-Kernel", "selectInGroup: 组成员不存在 $proxyName")
        return@safe false
    }
    val result = clash.patchSelector(groupName, proxyName)
    AppLog.d("Polaris-Kernel", "selectInGroup: $groupName -> $proxyName result=$result")
    result
}

/** 触发指定策略组的延迟测试，返回成员名到延迟的映射（超时为 DELAY_TIMEOUT）。 */
suspend fun KernelProxy.testGroup(groupName: String): Map<String, Int> = safe(emptyMap(), "testGroup") {
    val clash = manager.clash() ?: return@safe emptyMap()
    clash.healthCheck(groupName)
    clash
        .queryProxyGroup(groupName, ProxySort.Delay)
        .proxies
        .associate { it.name to normalizeDelay(it.delay) }
}

private const val GROUP_TYPE_SELECTOR = "Selector"

private const val GLOBAL_GROUP = "GLOBAL"

suspend fun KernelProxy.groupByTypeCurrentNode(type: String): String? = safe(null, "groupByTypeCurrentNode") {
    val clash = manager.clash() ?: return@safe null
    val group = queryGroupByTypeName(type) ?: return@safe null
    clash.queryProxyGroup(group, ProxySort.Default).now.ifBlank { null }
}

suspend fun KernelProxy.groupByTypeDelay(type: String): Int? = safe(null, "groupByTypeDelay") {
    val clash = manager.clash() ?: return@safe null
    val group = queryGroupByTypeName(type) ?: return@safe null

    clash.healthCheck(group)
    val state = clash.queryProxyGroup(group, ProxySort.Delay)
    val proxy =
        state.proxies.firstOrNull { it.name == state.now }
            ?: state.proxies.firstOrNull { !it.isGroup }
            ?: return@safe null

    normalizeDelay(proxy.delay)
}

internal suspend fun KernelProxy.queryGroupByTypeName(type: String): String? {
    val clash = manager.clash() ?: return null
    for (name in clash.queryProxyGroupNames(excludeNotSelectable = false)) {
        val group = clash.queryProxyGroup(name, ProxySort.Default)
        if (group.type.equals(type, ignoreCase = true)) return name
    }
    return null
}

suspend fun KernelProxy.ensureGlobalSelection() = safe(Unit, "ensureGlobalSelection") {
    val clash = manager.clash() ?: return@safe
    if (clash.queryTunnelState().mode != TunnelState.Mode.Global) return@safe
    val now = clash.queryProxyGroup("GLOBAL", ProxySort.Default).now
    AppLog.d("Polaris-Kernel", "ensureGlobalSelection: GLOBAL now=$now")
    if (now.isBlank() || now == "DIRECT" || now == "REJECT") {
        val target = autoGroupName() ?: run {
            AppLog.w("Polaris-Kernel", "ensureGlobalSelection: 未找到可挂载的策略组，GLOBAL 保持 $now（全局模式将直连）")
            return@safe
        }
        val result = clash.patchSelector("GLOBAL", target)
        // 回读核验：patch 失败时全局模式会静默保持直连
        val after = clash.queryProxyGroup("GLOBAL", ProxySort.Default).now
        AppLog.d("Polaris-Kernel", "ensureGlobalSelection: GLOBAL $now -> $target result=$result after=$after")
    }
}

internal suspend fun KernelProxy.waitForGroups(): String? {
    repeat(10) {
        val group = selectorGroup()
        if (group != null) return group
        kotlinx.coroutines.delay(300)
    }
    return null
}

internal fun KernelProxy.normalizeDelay(delay: Int): Int = if (delay <= 0 || delay >= Constants.DELAY_INVALID_MAX) Constants.DELAY_TIMEOUT else delay

internal suspend fun KernelProxy.selectorGroup(): String? {
    val clash = manager.clash() ?: return null
    val groups = clash.queryProxyGroupNames(excludeNotSelectable = false)
    groups.forEach { group ->

        if (group == "GLOBAL") return@forEach
        val type = clash.queryProxyGroup(group, ProxySort.Default).type
        AppLog.d("Polaris-Kernel", "selectorGroup: $group type=$type")
        if (type.equals("Selector", ignoreCase = true) || type.equals("URLTest", ignoreCase = true)) {
            return group
        }
    }
    return groups.firstOrNull { it != "GLOBAL" }
}

internal suspend fun KernelProxy.selectSpecialGroup(
    type: String,
    vararg nameKeywords: String,
): Boolean {
    val clash = manager.clash() ?: return false
    val selector = selectorGroup() ?: return false
    val target = queryGroupByTypeName(type) ?: nameMatch(*nameKeywords) ?: return false

    val result = clash.patchSelector(selector, target)
    AppLog.d("Polaris-Kernel", "selectSpecial($type): selector=$selector target=$target result=$result")
    return result
}

internal suspend fun KernelProxy.patchGlobalIfGlobal(target: String) {
    val clash = manager.clash() ?: return
    if (clash.queryTunnelState().mode != TunnelState.Mode.Global) return
    val result = clash.patchSelector("GLOBAL", target)
    AppLog.d("Polaris-Kernel", "patchGlobalIfGlobal: GLOBAL -> $target result=$result")
}

/**
 * 全局模式下挂载到 GLOBAL 组的目标策略组。
 * 优先 URLTest / 名称含「自动」的组；V2Board/Xboard 面板常见配置只有
 * Selector 组（如「节点选择」），此时回落到首个 Selector/URLTest 组——
 * 缺了这一层兜底，此类面板全局模式下 GLOBAL 停在 DIRECT，全部流量直连。
 */
internal suspend fun KernelProxy.autoGroupName(): String? = queryGroupByTypeName("URLTest")
    ?: nameMatch("自动", "auto", "url")
    ?: selectorGroup()

internal suspend fun KernelProxy.fallbackGroupName(): String? = queryGroupByTypeName("Fallback") ?: nameMatch("故障", "fallback")

internal suspend fun KernelProxy.nameMatch(vararg keywords: String): String? {
    val clash = manager.clash() ?: return null
    return clash
        .queryProxyGroupNames(excludeNotSelectable = false)
        .firstOrNull { group -> keywords.any { group.contains(it, ignoreCase = true) } }
}
