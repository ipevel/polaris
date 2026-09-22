// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request

private const val IP_FAULT_TAG = "Polaris-IP"

suspend fun KernelProxy.fetchPublicIp(): IpGeoInfo? = safe(null, "fetchPublicIp", IP_FAULT_TAG) {
    val (ipv4, ipv6) =
        coroutineScope {
            val v4 = async { queryIp(ipClient, KernelProxy.IPIFY_V4_URL) }
            val v6 = async { queryIp(ipClient, KernelProxy.IPIFY_V6_URL) }
            v4.await() to v6.await()
        }
    val ip = ipv4 ?: ipv6 ?: return@safe null
    IpGeoInfo(ip = ip, ipv6 = ipv6, countryCode = geoIpResolver.countryCode(ip))
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
                AppLog.d("Polaris-IP", "queryIp($url): HTTP ${response.code}")
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
} catch (e: Exception) {
    AppLog.d(IP_FAULT_TAG, "queryIp 无结果: ${sanitizeLog(e.message ?: "Unknown")}")
    null
}
