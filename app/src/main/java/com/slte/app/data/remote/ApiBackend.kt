// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

object ApiPaths {

    const val PREFIX = "/api/v1"

    const val AUTH = "$PREFIX/user/"

    const val GUEST_CONFIG = "$PREFIX/guest/comm/config"
}

data class ApiBackend(
    val type: String,
    val baseUrl: String,
    val apiPrefix: String = ApiPaths.PREFIX,
)
