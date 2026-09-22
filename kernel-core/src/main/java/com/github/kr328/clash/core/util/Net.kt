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
    val url = URL("https://$address")

    return InetSocketAddress(InetAddress.getByName(url.host), url.port)
}