// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.util

data class IPNet(val ip: String, val prefix: Int)

fun parseCIDR(cidr: String): IPNet {
    val s = cidr.split("/", limit = 2)

    if (s.size != 2)
        throw IllegalArgumentException("Invalid address")

    val address = s[0]
    val prefix = s[1].toInt()

    return IPNet(address, prefix)
}
