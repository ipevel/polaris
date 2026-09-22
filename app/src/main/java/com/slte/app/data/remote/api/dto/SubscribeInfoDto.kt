// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.api.dto

data class SubscribeInfoDto(
    val planId: Int = 0,
    val planName: String = "",
    val expiredAt: Long = 0L,
    val transferEnable: Long = 0L,
    val upload: Long = 0L,
    val download: Long = 0L,
    val resetDay: Int? = null,
    val subscribeUrl: String? = null,
)
