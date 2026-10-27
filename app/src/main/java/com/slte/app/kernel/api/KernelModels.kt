package com.slte.app.kernel.api

import java.util.UUID

enum class KernelBindingState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RETRYING,
    FAILED,
}

enum class KernelVpnState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

data class KernelVpnStatus(
    val state: KernelVpnState = KernelVpnState.STOPPED,
    val reason: String? = null,
)

enum class KernelTunnelMode {
    GLOBAL,
    RULE,
    DIRECT,
    SCRIPT,
}

enum class KernelProxySort {
    DEFAULT,
    DELAY,
}

/** 单节点实测结果；delay 为实测毫秒，0 表示测速失败（统一按超时展示）。 */
data class KernelUrlTestResult(
    val delay: Int,
)

enum class KernelProfileType {
    URL,
}

enum class SelectionType { AUTO, FALLBACK, MANUAL }

data class KernelServerInfo(
    val selection: SelectionType?,
    val node: String?,
)

/** 自动选择/故障转移对应的内核代理组类型；实现方映射到自己的组类型名。 */
enum class SelectionGroupType { AUTO, FALLBACK }

data class KernelProfile(
    val uuid: UUID,
    val name: String,
    val source: String,
    val imported: Boolean,
)

data class KernelProxyGroupSnapshot(
    val type: String,
    val proxies: List<KernelProxySnapshot>,
    val now: String,
)

data class KernelProxySnapshot(
    val name: String,
    val isGroup: Boolean,
    val delay: Int,
    val measured: Boolean,
)
