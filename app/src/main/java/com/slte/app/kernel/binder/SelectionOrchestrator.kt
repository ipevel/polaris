package com.slte.app.kernel.binder

import com.slte.app.kernel.NodeNameResolver
import com.slte.app.kernel.api.KernelClash
import com.slte.app.kernel.api.KernelProxySort
import com.slte.app.kernel.api.KernelServerInfo
import com.slte.app.kernel.api.KernelTunnelMode
import com.slte.app.kernel.api.SelectionGroupType
import com.slte.app.kernel.api.SelectionType
import com.slte.app.utils.AppLog
import kotlinx.coroutines.delay

/**
 * mihomo 分组/选择器编排。只依赖 [KernelClash] 接口与取内核的函数，不依赖任何具体实现类。
 */
internal class SelectionOrchestrator(
    private val kernel: suspend () -> KernelClash?,
) {
    /** 主选择器：优先 select/url-test 组，否则取首个非 GLOBAL 组。 */
    suspend fun primaryGroup(clash: KernelClash): String? {
        val names = clash.queryProxyGroupNames(excludeNotSelectable = false).filterNot { it == "GLOBAL" }
        return names.firstOrNull { group ->
            val type = clash.queryProxyGroup(group, KernelProxySort.DEFAULT).type
            type.equals("Selector", ignoreCase = true) || type.equals("URLTest", ignoreCase = true)
        } ?: names.firstOrNull()
    }

    suspend fun waitForPrimaryGroup(): String? {
        repeat(10) {
            val clash = kernel() ?: return null
            primaryGroup(clash)?.let { return it }
            delay(300)
        }
        return null
    }

    suspend fun selectNode(name: String): Boolean {
        val clash = kernel() ?: return false
        val group = primaryGroup(clash) ?: return false
        val members = clash.queryProxyGroup(group, KernelProxySort.DEFAULT).proxies.filterNot { it.isGroup }
        val resolved = NodeNameResolver.resolve(members.map { it.name }, name)
        if (resolved == null) {
            AppLog.w("SLTE-Kernel", "selectNode: 未匹配 group=$group target=$name members=${members.size}")
            return false
        }
        clash.patchSelector(group, resolved)
        var now = clash.queryProxyGroup(group, KernelProxySort.DEFAULT).now
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_DELAY_MS)
            now = clash.queryProxyGroup(group, KernelProxySort.DEFAULT).now
            if (now == resolved) return true
        }
        AppLog.w("SLTE-Kernel", "selectNode: 切换未生效 group=$group target=$resolved now=$now")
        return now == resolved
    }

    suspend fun nodeNames(): List<String> {
        val clash = kernel() ?: return emptyList()
        return clash.queryProxyGroupNames(excludeNotSelectable = false)
            .asSequence()
            .filterNot { it == "GLOBAL" }
            .flatMap { group -> clash.queryProxyGroup(group, KernelProxySort.DEFAULT).proxies.asSequence() }
            .filterNot { it.isGroup || it.name == "DIRECT" || it.name == "REJECT" }
            .map { it.name }
            .distinct()
            .toList()
    }

    suspend fun selectionInfo(): KernelServerInfo? {
        val clash = kernel() ?: return null
        val selector = primaryGroup(clash) ?: return null
        val now = clash.queryProxyGroup(selector, KernelProxySort.DEFAULT).now
        if (now.isBlank()) return KernelServerInfo(null, null)
        // smart 内核的分组类型是 Smart，需与 URLTest 一并识别为「自动选择」
        val auto =
            findGroupByType(clash, "Smart")
                ?: findGroupByType(clash, "URLTest")
                ?: matchGroupName(clash, "自动", "auto", "url", "smart")
        val fallback = findGroupByType(clash, "Fallback") ?: matchGroupName(clash, "故障", "fallback")
        val selection =
            when (now) {
                auto -> SelectionType.AUTO
                fallback -> SelectionType.FALLBACK
                else -> SelectionType.MANUAL
            }
        return KernelServerInfo(selection, leafOutbound(clash, now))
    }

    suspend fun applyGroupOfType(
        type: String,
        vararg keywords: String,
    ): Boolean {
        val clash = kernel() ?: return false
        val selector = primaryGroup(clash) ?: return false
        val target = findGroupByType(clash, type) ?: matchGroupName(clash, *keywords) ?: return false
        val result = clash.patchSelector(selector, target)
        AppLog.d("SLTE-Kernel", "selectSpecial($type): selector=$selector target=$target result=$result")
        return result
    }

    suspend fun ensureGlobal() {
        val clash = kernel() ?: return
        if (clash.queryTunnelMode() != KernelTunnelMode.GLOBAL) return
        val now = clash.queryProxyGroup("GLOBAL", KernelProxySort.DEFAULT).now
        if (now.isNotBlank() && now != "DIRECT" && now != "REJECT") return
        val target =
            findGroupByType(clash, "Smart")
                ?: findGroupByType(clash, "URLTest")
                ?: matchGroupName(clash, "自动", "auto", "url", "smart")
                ?: return
        clash.patchSelector("GLOBAL", target)
        AppLog.d("SLTE-Kernel", "ensureGlobal: GLOBAL -> $target")
    }

    suspend fun currentOf(groupType: SelectionGroupType): String? {
        val clash = kernel() ?: return null
        val group =
            when (groupType) {
                SelectionGroupType.AUTO ->
                    matchGroupName(clash, "自动", "auto", "url", "smart")
                        ?: findGroupByType(clash, "Smart")
                        ?: findGroupByType(clash, "URLTest")
                SelectionGroupType.FALLBACK -> matchGroupName(clash, "故障", "fallback") ?: findGroupByType(clash, "Fallback")
            } ?: return null
        return leafOutbound(clash, group)
    }

    suspend fun healthCheckAll() {
        kernel()?.healthCheckAll()
    }

    /** 逐层展开嵌套组，返回最终叶子节点。 */
    private suspend fun leafOutbound(
        clash: KernelClash,
        group: String,
    ): String? {
        val groupNames = clash.queryProxyGroupNames(excludeNotSelectable = false).toSet()
        var state = clash.queryProxyGroup(group, KernelProxySort.DEFAULT)
        var selected = state.now
        // smart 组在未选中时 Now() 返回组名本身，按延迟挑最优节点代替
        if (selected == group || selected == "Smart - Select") {
            selected = pickFastest(state.proxies.filterNot { it.isGroup })
                ?: state.proxies.firstOrNull { !it.isGroup }?.name
                ?: return null
            if (selected !in groupNames) return selected
        }
        repeat(groupNames.size.coerceAtMost(8)) {
            if (selected.isBlank()) {
                val candidates = state.proxies.filterNot { it.name == "DIRECT" || it.name == "REJECT" }
                selected =
                    candidates.filterNot { it.isGroup }.minByOrNull { if (it.delay > 0) it.delay else Int.MAX_VALUE }?.name
                        ?: candidates.firstOrNull { it.isGroup }?.name
                        ?: return null
            }
            if (selected !in groupNames) return selected
            state = clash.queryProxyGroup(selected, KernelProxySort.DEFAULT)
            selected = state.now
        }
        return selected.takeIf { it.isNotBlank() && it !in groupNames }
    }

    /** 组内延迟最低的节点（未测速的排最后）。 */
    private fun pickFastest(proxies: List<com.slte.app.kernel.api.KernelProxySnapshot>): String? = proxies.filter { it.measured && it.delay > 0 }.minByOrNull { it.delay }?.name
        ?: proxies.firstOrNull()?.name

    private suspend fun findGroupByType(
        clash: KernelClash,
        type: String,
    ): String? = clash.queryProxyGroupNames(excludeNotSelectable = false)
        .firstOrNull { clash.queryProxyGroup(it, KernelProxySort.DEFAULT).type.equals(type, ignoreCase = true) }

    private suspend fun matchGroupName(
        clash: KernelClash,
        vararg keywords: String,
    ): String? = clash.queryProxyGroupNames(excludeNotSelectable = false)
        .firstOrNull { group -> keywords.any { group.contains(it, ignoreCase = true) } }

    private companion object {
        const val VERIFY_ATTEMPTS = 2
        const val VERIFY_DELAY_MS = 120L
    }
}
