// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.core.util

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

fun parseInetSocketAddress(address: String): InetSocketAddress {
    // 热路径（每连接回调，来自 TUN 栈）：地址应为 IP 字面量。
    // 字面量下 getByName 不走 DNS；域名输入（不该出现）不再阻塞解析，
    // 也不再向 JNI 回调抛异常（C 侧滞留异常会导致下一次调用 abort 进程）。
    return runCatching {
        val url = URL("https://$address")
        val host = url.host

        if (host.any { !it.isDigit() && it != '.' && it != ':' }) {
            return InetSocketAddress(0)
        }

        InetSocketAddress(InetAddress.getByName(host), url.port)
    }.getOrDefault(InetSocketAddress(0))
}