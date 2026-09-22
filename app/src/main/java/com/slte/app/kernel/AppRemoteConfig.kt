// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

interface AppRemoteConfig {
    val apiBaseUrl: String
    val directDomains: List<String>
}
