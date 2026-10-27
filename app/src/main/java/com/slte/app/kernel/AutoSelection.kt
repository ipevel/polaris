package com.slte.app.kernel

import com.slte.app.kernel.api.KernelApi
import com.slte.app.kernel.api.SelectionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * 当前生效的选择方式与真实落点节点。
 *
 * 自动选择/故障转移时 [node] 是组内当前节点（不是"自动选择"这个组名），
 * 首页的节点名与国旗就靠它实时刷新。
 */
data class LiveSelection(
    val type: SelectionType,
    val node: String?,
)

/** 立即切换选择方式，不等测速跑完。 */
suspend fun KernelApi.applySelection(type: SelectionType): Boolean = when (type) {
    SelectionType.AUTO -> applyAutoSelection()
    SelectionType.FALLBACK -> applyFallbackSelection()
    SelectionType.MANUAL -> false
}

/** 实时监听真实落点节点。 */
fun KernelApi.liveSelectionFlow(intervalMs: Long = LIVE_SELECTION_POLL_MS): Flow<LiveSelection> = flow {
    while (true) {
        val info = selectionInfo()
        emit(LiveSelection(info?.selection ?: SelectionType.MANUAL, info?.node))
        delay(intervalMs)
    }
}.distinctUntilChanged()

/** 常态监听间隔。 */
const val LIVE_SELECTION_POLL_MS = 1_500L

/** 测速/更新订阅期间的监听间隔。 */
const val LIVE_SELECTION_BUSY_POLL_MS = 600L

/** 触发健康检查更新内核缓存（不逐节点真测）。 */
suspend fun KernelApi.refreshSelection() {
    triggerHealthCheckAll()
}
