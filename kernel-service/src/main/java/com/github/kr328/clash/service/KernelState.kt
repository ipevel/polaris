package com.github.kr328.clash.service

/**
 * 内核服务生命周期状态。
 *
 * Running 必须同时满足 TUN 已建立、配置已加载；只有到达 Running 才向 App 广播
 * ACTION_CLASH_STARTED，避免"服务起来了但 TUN/配置还没就绪"被当成已连接。
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
