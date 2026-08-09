package com.slte.app.kernel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * KernelProxy 出口 IP 扩展：双栈（IPv4/IPv6）并发探测。
 * 与 KernelProxy 同包，访问其 internal 成员。
 */

/** 查询当前出口公网 IP（走内核隧道），失败返回 null；国家代码本地 GeoIP 解析 */
suspend fun KernelProxy.fetchPublicIp(): IpGeoInfo? = withContext(Dispatchers.IO) {
    try {
        // 双栈并发探测：IPv4 保底，IPv6 端点失败自动降级（规则/全局模式下请求都走节点）
        val (ipv4, ipv6) = coroutineScope {
            val v4 = async { queryIp(ipClient, KernelProxy.IPIFY_V4_URL) }
            val v6 = async { queryIp(ipClient, KernelProxy.IPIFY_V6_URL) }
            v4.await() to v6.await()
        }
        val ip = ipv4 ?: ipv6 ?: return@withContext null
        IpGeoInfo(ip = ip, ipv6 = ipv6, countryCode = geoIpResolver.countryCode(ip))
    } catch (e: Exception) {
        null
    }
}

/** 单端点 IP 查询；失败/非 2xx 返回 null */
internal fun KernelProxy.queryIp(client: OkHttpClient, url: String): String? {
    return try {
        client.newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()?.trim()?.takeIf { it.isNotBlank() }
            }
    } catch (e: Exception) {
        null
    }
}
