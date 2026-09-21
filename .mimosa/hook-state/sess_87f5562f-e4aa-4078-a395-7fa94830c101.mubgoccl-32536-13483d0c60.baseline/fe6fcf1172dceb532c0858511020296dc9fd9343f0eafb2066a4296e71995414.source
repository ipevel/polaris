package com.slte.app.kernel

import com.slte.app.utils.AppLog
import kotlinx.coroutines.delay

/**
 * 等待内核真正就绪：配置已加载、策略组可读。
 *
 * TunService.onStartCommand 一进来就无条件发 ACTION_CLASH_STARTED，而 TUN 的
 * 建立与配置装载是在 onCreate 的协程里异步进行的。只凭该广播就把
 * `isConnected` 置为 true，等于把"内核进程活着"当成"连接成功"，
 * 会出现界面显示已连接、出口 IP 也变了，但实际流量并没有走代理的假连接。
 */
suspend fun KernelProxy.awaitTunnelReady(timeoutMs: Long = TUNNEL_READY_TIMEOUT_MS): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    var lastMode: String? = null
    while (System.currentTimeMillis() < deadline) {
        val clash = manager.clash()
        if (clash != null) {
            val state = runCatching { clash.queryTunnelState() }.getOrNull()
            if (state != null) {
                lastMode = state.mode.name
                val groups =
                    runCatching {
                        clash.queryProxyGroupNames(excludeNotSelectable = false)
                    }.getOrNull()
                if (!groups.isNullOrEmpty()) return true
            }
        }
        delay(TUNNEL_READY_POLL_MS)
    }
    AppLog.w("Polaris-Kernel", "awaitTunnelReady 超时 ${timeoutMs}ms，最后 TunnelState.mode=$lastMode")
    return false
}

private const val TUNNEL_READY_TIMEOUT_MS = 12_000L

private const val TUNNEL_READY_POLL_MS = 300L
