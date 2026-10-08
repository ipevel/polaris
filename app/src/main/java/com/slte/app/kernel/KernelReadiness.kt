// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.utils.AppLog
import kotlinx.coroutines.delay

/**
 * 等待内核真正就绪：配置已加载、策略组可读。
 *
 * TunService.onStartCommand 一进来就无条件发 ACTION_CLASH_STARTED，而 TUN 的
 * 建立与配置装载是在 onCreate 的协程里异步进行的。只凭该广播就把
 * `isConnected` 置为 true，等于把"内核进程活着"当成"连接成功"，
 * 会出现界面显示已连接、出口 IP 也变了，但实际流量并没有走代理的假连接。
 *
 * 超时用「有界轮询次数 × 每轮 delay」表达而非墙钟比较：生产环境每轮真实
 * 等待 300ms，协程测试（虚拟时钟）下 delay 立即返回，不会热旋真实时间。
 */
suspend fun KernelProxy.awaitTunnelReady(timeoutMs: Long = TUNNEL_READY_TIMEOUT_MS): Boolean {
    val maxPolls = (timeoutMs / TUNNEL_READY_POLL_MS).toInt().coerceAtLeast(1)
    var lastMode: String? = null
    repeat(maxPolls) {
        val clash = manager.clash()
        if (clash != null) {
            val state = runCatching { clash.queryTunnelState() }.getOrNull()
            if (state != null) {
                lastMode = state.mode.name
                if (isProfileLoaded(clash, state.mode)) return true
            }
        }
        delay(TUNNEL_READY_POLL_MS)
    }
    AppLog.w("Polaris-Kernel", "awaitTunnelReady 超时 ${timeoutMs}ms，最后 TunnelState.mode=$lastMode")
    return false
}

/**
 * 配置是否已装载（策略组可读）。
 *
 * 直连模式下内核 `QueryProxyGroupNames` 恒返回空 —— `native/tunnel/proxies.go`
 * 的 `if mode == tunnel.Direct { return []string{} }`。再拿「策略组非空」当
 * 就绪判据就永远等不到，界面会一直卡在「连接中」直到看门狗超时。直连没有
 * 策略组可读，改用「GLOBAL 组可读」作为「配置已装载」的等价判据。
 */
private fun isProfileLoaded(clash: IClashManager, mode: TunnelState.Mode): Boolean = if (mode == TunnelState.Mode.Direct) {
    runCatching { clash.queryProxyGroup("GLOBAL", ProxySort.Default) }.getOrNull() != null
} else {
    !runCatching { clash.queryProxyGroupNames(excludeNotSelectable = false) }.getOrNull().isNullOrEmpty()
}

private const val TUNNEL_READY_TIMEOUT_MS = 12_000L

private const val TUNNEL_READY_POLL_MS = 300L
