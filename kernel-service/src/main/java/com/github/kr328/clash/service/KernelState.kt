// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
//
// 移植自上游 shgnx/slte 的 c73c4af6（SLTE 2.0.0「修复内核生命周期与 VPN 连接状态」）。
// 上游同为 GPL-3.0，移植时保留其状态机语义、双条件晋升规则与用例命名。

package com.github.kr328.clash.service

/**
 * 内核生命周期；TUN 与配置均就绪才进入 [State.Running]。
 *
 * 与旧实现的关键差别：`ACTION_CLASH_STARTED` 不再在 `onStartCommand` 里立即广播，
 * 而是等到 TUN 已 attach **且** 配置已加载（见 [isRunning]）才广播，
 * 因此不会再出现「界面显示已连接、内核其实还没起好」的假连接。
 */
object KernelState {
    enum class State { Stopped, Starting, Running, Stopping, Failed }

    @Volatile
    var state: State = State.Stopped
        private set

    @Volatile
    var reason: String? = null
        private set

    private var tunAttached = false
    private var profileLoaded = false

    @Synchronized
    fun starting() {
        state = State.Starting
        reason = null
        tunAttached = false
        profileLoaded = false
    }

    /** TUN 堆栈模式切换导致重建：已就绪的 TUN 作废，重新等 attach。 */
    @Synchronized
    fun restartingTun() {
        tunAttached = false
        if (state == State.Running || state == State.Starting) {
            state = State.Starting
        }
    }

    @Synchronized
    fun tunAttached() {
        tunAttached = true
        promote()
    }

    @Synchronized
    fun profileLoaded() {
        profileLoaded = true
        promote()
    }

    @Synchronized
    fun stopping() {
        if (state != State.Failed) {
            state = State.Stopping
        }
    }

    /** reason 非空表示异常终止，保留为 [State.Failed] 供界面读回。 */
    @Synchronized
    fun destroyed(reason: String?) {
        state = if (reason != null) State.Failed else State.Stopped
        this.reason = reason
        tunAttached = false
        profileLoaded = false
    }

    @Synchronized
    fun failed(reason: String?) {
        state = State.Failed
        this.reason = reason
    }

    @Synchronized
    fun isRunning(): Boolean = state == State.Running

    private fun promote() {
        if (tunAttached && profileLoaded && state != State.Failed && state != State.Stopping) {
            state = State.Running
            reason = null
        }
    }
}
