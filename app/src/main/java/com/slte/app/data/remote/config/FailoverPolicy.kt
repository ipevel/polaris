// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import okhttp3.Response

object FailoverPolicy {

    val RETRYABLE_METHODS = setOf("GET", "HEAD", "OPTIONS")

    val FAILURE_CODES = setOf(408, 425, 429, 500, 502, 503, 504, 522, 524, 530)

    fun isRetryableMethod(method: String): Boolean = method in RETRYABLE_METHODS

    fun isFailureCode(code: Int): Boolean = code in FAILURE_CODES

    fun isJsonMismatch(
        contentType: String?,
        firstByte: Int?,
    ): Boolean {
        val declaresJson = contentType?.lowercase()?.contains("json") == true
        if (!declaresJson || firstByte == null) return false
        return firstByte != '{'.code && firstByte != '['.code
    }

    fun firstByteOf(response: Response): Int? = try {
        response
            .peekBody(1)
            .byteStream()
            .read()
            .takeIf { it >= 0 }
    } catch (_: Exception) {
        null
    }
}
