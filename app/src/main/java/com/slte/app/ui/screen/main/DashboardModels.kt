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

    /** 实时上行速率（字节/秒），断开时为 0。 */
    val uploadSpeedBps: Long = 0L,

    /** 实时下行速率（字节/秒），断开时为 0。 */
    val downloadSpeedBps: Long = 0L,
    /** 后端站点名称（动态拉取，未配置为空） */
    val siteName: String = "",
    /** 后端站点描述（动态拉取，未配置为空） */
    val siteDescription: String = "",

    /** 最近速度采样历史（上传, 下载 bps），供波形图渲染，最多保留 60 点 */
    val speedHistory: List<Pair<Long, Long>> = emptyList(),

    /** 本次连接累计上传（字节），由内核累计流量差值基线推导，断开归零 */
    val sessionUploadBytes: Long = 0L,

    /** 本次连接累计下载（字节），断开归零 */
    val sessionDownloadBytes: Long = 0L,

    /** 本机内网 IPv4 地址 */
    val lanIp: String = Constants.PLACEHOLDER_DASH,

    /** 当前应用进程占用内存（PSS，MB） */
    val appMemoryUsedMb: Int = 0,

    /** 连接建立时刻（SystemClock.elapsedRealtime，毫秒），未连接为 0 */
    val connectedSinceElapsedMs: Long = 0L,
)
