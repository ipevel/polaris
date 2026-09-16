package com.slte.app.data.remote.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object ConfigValidation {
    fun isHostAllowed(
        host: String,
        allowedSuffixes: List<String>,
    ): Boolean = allowedSuffixes.any { host == it || host.endsWith(".$it") }

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
