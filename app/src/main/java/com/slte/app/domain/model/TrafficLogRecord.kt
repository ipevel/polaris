// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.domain.model

/** 单日流量记录 */
data class TrafficLogRecord(
    val date: String = "",
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
) {
    val totalBytes: Long get() = uploadBytes + downloadBytes
}
