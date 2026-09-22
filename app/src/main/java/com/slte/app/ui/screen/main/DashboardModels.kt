// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.main

import com.slte.app.utils.Constants

data class DashboardData(
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isValid: Boolean = false,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val daysUntilExpired: Int = 0,
    val expiredAt: Long = 0L,
    val serverName: String = Constants.PLACEHOLDER_DASH,
    val proxyMode: String = Constants.DEFAULT_PROXY_MODE,
    val currentIp: String = Constants.PLACEHOLDER_DASH,

    val ipCountryCode: String? = null,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isRefreshing: Boolean = false,
    val dataLoaded: Boolean = false,
    val errorMessageRes: Int? = null,
    val isUpdating: Boolean = false,
    /** 后端站点名称（动态拉取，未配置为空） */
    val siteName: String = "",
    /** 后端站点描述（动态拉取，未配置为空） */
    val siteDescription: String = "",
)
