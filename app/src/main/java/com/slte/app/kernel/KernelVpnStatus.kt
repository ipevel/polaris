// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
//
// 移植自上游 shgnx/slte 的 c73c4af6（SLTE 2.0.0「修复内核生命周期与 VPN 连接状态」）。

package com.slte.app.kernel

/** 内核 VPN 生命周期状态，逐一对齐 kernel-service 的 `KernelState.State`。 */
enum class KernelVpnState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

/** [KernelVpnState.RUNNING] 才表示「真的连上了」，其余状态都不是可用连接。 */
data class KernelVpnStatus(
    val state: KernelVpnState = KernelVpnState.STOPPED,
    val reason: String? = null,
)
