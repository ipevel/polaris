// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.api.dto

data class LoginResponseDto(
    val token: String,
    val authData: String,
)
