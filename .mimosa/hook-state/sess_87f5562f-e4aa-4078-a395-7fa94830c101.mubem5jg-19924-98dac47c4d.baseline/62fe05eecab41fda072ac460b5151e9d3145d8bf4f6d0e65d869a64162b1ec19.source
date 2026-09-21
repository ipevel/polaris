package com.slte.app.ui.screen.main

import com.slte.app.R
import com.slte.app.utils.Constants

internal data class ProxyModeOption(
    val mode: String,
    val labelRes: Int,
    val descRes: Int,
)

internal val PROXY_MODE_OPTIONS =
    listOf(
        ProxyModeOption(
            Constants.DEFAULT_PROXY_MODE,
            R.string.dashboard_proxy_rule,
            R.string.proxy_rule_desc,
        ),
        ProxyModeOption(
            Constants.PROXY_MODE_GLOBAL,
            R.string.dashboard_proxy_global,
            R.string.proxy_global_desc,
        ),
    )

internal fun proxyModeLabelRes(mode: String): Int? = when (mode) {
    Constants.DEFAULT_PROXY_MODE -> R.string.dashboard_proxy_rule
    Constants.PROXY_MODE_GLOBAL -> R.string.dashboard_proxy_global
    Constants.PROXY_MODE_DIRECT -> R.string.dashboard_proxy_direct
    Constants.PROXY_MODE_SCRIPT -> R.string.dashboard_proxy_script
    else -> null
}
