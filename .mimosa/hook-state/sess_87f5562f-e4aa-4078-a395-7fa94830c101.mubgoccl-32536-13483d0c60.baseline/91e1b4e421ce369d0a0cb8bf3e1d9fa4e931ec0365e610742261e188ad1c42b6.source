package com.slte.app.data.remote.config

import com.slte.app.BuildConfig

internal object AllowedHosts {

    val SUFFIXES: List<String> =
        buildList {

            add("example.com")
            BuildConfig.ALLOWED_DOMAINS
                .split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .forEach { if (it !in this) add(it) }
        }

    fun isAllowedHost(host: String?): Boolean = !host.isNullOrBlank() && ConfigValidation.isHostAllowed(host.lowercase(), SUFFIXES)

    fun isAllowedUrl(url: String?): Boolean = !url.isNullOrBlank() && ConfigValidation.isValidApiUrl(url, SUFFIXES)
}
