// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.document

import java.util.*

data class Path(
    val uuid: UUID?,
    val scope: Scope?,
    val relative: List<String>?
) {
    enum class Scope {
        Configuration, Providers
    }

    override fun toString(): String {
        if (uuid == null)
            return "/"

        if (scope == null)
            return "/$uuid"

        val sc = when (scope) {
            Scope.Configuration -> Paths.CONFIGURATION_ID
            Scope.Providers -> Paths.PROVIDERS_ID
        }

        if (relative == null)
            return "/$uuid/$sc"

        return "/$uuid/$sc/${relative.joinToString(separator = "/")}"
    }
}