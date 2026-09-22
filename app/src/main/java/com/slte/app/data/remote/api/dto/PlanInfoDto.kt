// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.api.dto

data class PlanInfoDto(
    val id: Int,
    val name: String,
    val monthPrice: Long? = null,
    val quarterPrice: Long? = null,
    val halfYearPrice: Long? = null,
    val yearPrice: Long? = null,
    val twoYearPrice: Long? = null,
    val threeYearPrice: Long? = null,
    val onetimePrice: Long? = null,
    val resetPrice: Long? = null,
    val speedLimit: Int? = null,
    val deviceLimit: Int? = null,
    val content: String? = null,
    val transferEnable: Int = 0,
    val show: Boolean = true,
    val renew: Boolean = true,
    val sort: Int? = null,
)
