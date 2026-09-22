// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

interface FailoverConfig {

    val apiBaseUrl: String

    fun apiCandidates(primary: String): List<String>
}
