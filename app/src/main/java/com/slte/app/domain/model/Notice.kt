// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.model

data class Notice(
    val id: Int,
    val title: String,
    val body: String,
    val tags: List<String>,
    val createdAt: Long,
)
