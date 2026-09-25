// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

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
    /** 语义分类，渲染层据此给标签，不再做字符串匹配。 */
    val kind: KernelProxyMemberKind = KernelProxyMemberKind.NODE,
)

/**
 * 成员的语义类型。收敛此前散落在两处（`V5NodesScreen.groupExitLabel`、
 * `RoutingGroups.RoutingReservedNames`）的字符串匹配。
 */
enum class KernelProxyMemberKind { NODE, GROUP, DIRECT, REJECT }

/** 成员类型判定（纯函数，可单测）。 */
internal fun memberKindOf(
    name: String,
    isGroup: Boolean,
): KernelProxyMemberKind = when {
    name == OUTBOUND_DIRECT -> KernelProxyMemberKind.DIRECT
    name == OUTBOUND_REJECT -> KernelProxyMemberKind.REJECT
    isGroup -> KernelProxyMemberKind.GROUP
    else -> KernelProxyMemberKind.NODE
}

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
                            KernelProxyMember(
                                name = proxy.name,
                                isGroup = proxy.isGroup,
                                delay = resolveDelay(proxy.delay, proxy.tested),
                                kind = memberKindOf(proxy.name, proxy.isGroup),
                            )
                        },
                )
            }.getOrNull()
        }
}

/**
 * 在指定策略组内切换选中项。仅 Selector 类型支持手动切换。
 *
 * **必须回读确认**：内核 `PatchSelector` 在 `Selector.Set()` 失败时只打日志、
 * 仍然 `return true`（`native/tunnel/proxies.go`），所以 AIDL 返回值**不能**作为
 * 成功依据——否则成员名不匹配、profile 重载后组被替换等情况下，用户看到的就是
 * "点了没反应、勾选不动，也没有任何错误提示"。这里以「回读 `now` 是否等于目标」
 * 为唯一成功判据。
 */
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
    val after = clash.queryProxyGroup(groupName, ProxySort.Default).now
    val ok = after == proxyName
    AppLog.d(
        "Polaris-Kernel",
        "selectInGroup: $groupName -> $proxyName result=$result after=$after ok=$ok",
    )
    ok
}

/** 触发指定策略组的延迟测试，返回成员名到延迟的映射（超时为 DELAY_TIMEOUT）。 */
suspend fun KernelProxy.testGroup(groupName: String): Map<String, Int> = safe(emptyMap(), "testGroup") {
    val clash = manager.clash() ?: return@safe emptyMap()
    clash.healthCheck(groupName)
    clash
        .queryProxyGroup(groupName, ProxySort.Delay)
        .proxies
        // healthCheck 返回时该组已完成探测（见 KernelProxySpeed 的说明），
        // 所以这里未取到真实值的成员一定是「测过但失败」。
        .associate { it.name to resolveDelay(it.delay, hasTestRun = true) }
}

private const val GROUP_TYPE_SELECTOR = "Selector"

/**
 * 节点页「节点选择」卡的主组切换：成功后同步全局模式下的 GLOBAL。
 *
 * 与 [selectInGroup] 分开是刻意的——分流组出口不是全局出口，在分流组里改
 * GLOBAL 会让「某个分类选了日本」顺带改掉全局出口（语义污染）。而节点页的
 * 主选择组本身就是全局出口的语义，所以这里额外同步。
 *
 * [groupName] 由 UI 传入当前**实际展示**的主组名（`primaryGroupOf` 可能回退到
 * 第一个 Selectable 组），不要在这里硬编码 `PrimaryGroupName`，否则本地分流关闭
 * 或面板改名时会把 patch 打到用户没在看的那一组（表现为"点了没用"）。
 */
suspend fun KernelProxy.selectPrimary(
    groupName: String,
    name: String,
): Boolean {
    val ok = selectInGroup(groupName, name)
    if (ok) patchGlobalIfGlobal(name)
    return ok
}

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

    resolveDelay(proxy.delay, hasTestRun = true)
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

/**
 * 延迟三态归一（纯函数，可单测）。
 *
 * 内核 `LastDelayForTestUrl` 对「从未测过」与「测过但不存活」都返回 0xffff，
 * 只有 `Proxy.Tested` 能把两者分开（native/tunnel/proxies.go:delayTested）：
 * - `1..65534`            → 真实延迟，原样返回
 * - 0xffff 且 hasTestRun  → 测过但失败 → [Constants.DELAY_TIMEOUT]（「超时」）
 * - 其余（含 0/负数）     → 从未测过 → [Constants.DELAY_PENDING]（「未测」）
 *
 * hasTestRun=false 用于「本会话还没跑过测速」的读数：此时 0xffff 不能断言是超时。
 */
internal fun resolveDelay(
    raw: Int,
    hasTestRun: Boolean,
): Int = when {
    raw in 1 until Constants.DELAY_INVALID_MAX -> raw
    raw >= Constants.DELAY_INVALID_MAX && hasTestRun -> Constants.DELAY_TIMEOUT
    else -> Constants.DELAY_PENDING
}

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
