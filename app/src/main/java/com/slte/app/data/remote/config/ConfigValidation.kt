// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object ConfigValidation {
    fun isHostAllowed(
        host: String,
        allowedSuffixes: List<String>,
    ): Boolean = allowedSuffixes.any { host == it || host.endsWith(".$it") }

    /**
     * 检查主机名是否指向私有/保留地址（RFC 1918、环回、链路本地等）。
     * 用于面板地址安全确认，防止 SSRF。
     */
    fun isPrivateOrReservedHost(host: String): Boolean {
        // 纯 IP 地址检测
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val octets = parts.map { it.toInt() }
            return when {
                octets[0] == 127 -> true // 127.0.0.0/8  loopback
                octets[0] == 10 -> true // 10.0.0.0/8   RFC 1918
                octets[0] == 172 && octets[1] in 16..31 -> true // 172.16.0.0/12 RFC 1918
                octets[0] == 192 && octets[1] == 168 -> true // 192.168.0.0/16 RFC 1918
                octets[0] == 169 && octets[1] == 254 -> true // 169.254.0.0/16 link-local
                octets[0] == 0 -> true // 0.0.0.0/8    current network
                octets[0] == 100 && octets[1] in 64..127 -> true // 100.64.0.0/10 carrier-grade NAT
                else -> false
            }
        }
        // 域名检测：localhost 及 .local / .internal 等
        val lower = host.lowercase()
        return lower == "localhost" ||
            lower.endsWith(".local") ||
            lower.endsWith(".internal") ||
            lower.endsWith(".localhost")
    }

    fun isValidApiUrl(
        value: String,
        allowedSuffixes: List<String>,
    ): Boolean {
        val url = value.trim().toHttpUrlOrNull() ?: return false
        if (url.scheme != "https") return false
        return isHostAllowed(url.host.lowercase(), allowedSuffixes)
    }

    fun isValidDomain(
        value: String,
        allowedSuffixes: List<String>,
    ): Boolean {
        val host = value.trim().lowercase().trimEnd('.')
        if (host.isEmpty() || host.length > 253) return false
        val labels = host.split(".")
        if (labels.size < 2 || labels.any { it.isEmpty() || it.length > 63 }) return false
        return isHostAllowed(host, allowedSuffixes)
    }

    fun hasSamePath(
        a: String,
        b: String,
    ): Boolean {
        val ua = a.trimEnd('/').toHttpUrlOrNull() ?: return false
        val ub = b.trimEnd('/').toHttpUrlOrNull() ?: return false
        return ua.encodedPath == ub.encodedPath
    }

    /**
     * 规范化用户输入的面板地址：
     * - 空白输入返回 null（表示未填写，回落默认地址）；
     * - 无协议前缀时自动补 https://；
     * - 去掉尾部斜杠；
     * - 仅接受能解析出主机的 https 地址（App 全局禁明文流量，http 在运行时会被
     *   networkSecurityConfig 拦截，这里直接判为无效），其余返回 null 由调用方报错。
     */
    fun normalizePanelUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withScheme =
            when {
                trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
                else -> "https://$trimmed"
            }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host.isBlank()) return null
        return withScheme.trimEnd('/')
    }

    fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val pa = a.trim().split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.trim().split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    fun isCacheFresh(
        fetchedAt: Long,
        now: Long,
        ttlMs: Long,
    ): Boolean = fetchedAt > 0 && now - fetchedAt < ttlMs

    fun decodeApiCandidate(raw: String): String {
        val trimmed = raw.trim()
        val decoded =
            try {
                String(
                    java.util.Base64
                        .getDecoder()
                        .decode(trimmed),
                    Charsets.UTF_8,
                ).trim()
            } catch (_: Exception) {
                return trimmed
            }
        return if (decoded.startsWith("https://") || decoded.startsWith("http://")) decoded else trimmed
    }

    fun pickBest(configs: List<FetchedConfig>): FetchedConfig? = configs.minWithOrNull { a, b ->
        val v = compareVersions(b.version, a.version)
        if (v != 0) v else a.latencyMs.compareTo(b.latencyMs)
    }
}
