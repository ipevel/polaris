// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request

private const val IP_FAULT_TAG = "Polaris-IP"

/**
 * 查询出口 IP。
 *
 * [ipClient] 未配代理，全靠 Android 把本应用流量路由进 TUN（TunService 的三种
 * 访问控制模式都包含本应用）。隧道未就绪时查询必然直连失败——api.ipify.org 在
 * 直连下不可达——只会产生无谓的超时与日志噪声，故先等就绪再查。
 *
 * 就绪后仍带重试：日志里出现过「隧道已就绪但首次查询超时」的情形，一次失败
 * 就让 IP 永远停在占位符。
 */
suspend fun KernelProxy.fetchPublicIp(): IpGeoInfo? = safe(null, "fetchPublicIp", IP_FAULT_TAG) {
    if (!manager.connected.value) return@safe null
    if (!awaitTunnelReady(timeoutMs = IP_TUNNEL_READY_TIMEOUT_MS)) return@safe null

    var attempt = 0
    while (true) {
        val (ipv4, ipv6) =
            coroutineScope {
                val v4 = async { queryIp(ipClient, KernelProxy.IPIFY_V4_URL) }
                val v6 = async { queryIp(ipClient, KernelProxy.IPIFY_V6_URL) }
                v4.await() to v6.await()
            }
        val ip = ipv4 ?: ipv6
        if (ip != null) {
            return@safe IpGeoInfo(ip = ip, ipv6 = ipv6, countryCode = geoIpResolver.countryCode(ip))
        }
        attempt++
        if (attempt >= IP_QUERY_MAX_ATTEMPTS) break
        delay(IP_QUERY_RETRY_DELAY_MS)
    }
    null
}

internal fun KernelProxy.queryIp(
    client: OkHttpClient,
    url: String,
): String? = try {
    client
        .newCall(Request.Builder().url(url).build())
        .execute()
        .use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body
                    ?.string()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * 隧道就绪等待上限。
 *
 * 正确时序的调用方（MainViewModel 的 tunnelWatchJob）已经等过 [awaitTunnelReady]，
 * 这里会立即返回；只有过早触发的路径才真正等这个窗口。
 */
private const val IP_TUNNEL_READY_TIMEOUT_MS = 3_000L

/** 出口 IP 查询的最大尝试次数（含首次）。 */
private const val IP_QUERY_MAX_ATTEMPTS = 3

/** 两次查询之间的退避间隔。 */
private const val IP_QUERY_RETRY_DELAY_MS = 1_500L
