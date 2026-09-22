// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.api

interface ApiResponse<out T> {
    val data: T?
    val message: String?
}
