// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverPolicyTest {
    @Test
    fun `仅幂等方法允许重放`() {
        assertTrue(FailoverPolicy.isRetryableMethod("GET"))
        assertTrue(FailoverPolicy.isRetryableMethod("HEAD"))
        assertTrue(FailoverPolicy.isRetryableMethod("OPTIONS"))

        assertFalse(FailoverPolicy.isRetryableMethod("POST"))
        assertFalse(FailoverPolicy.isRetryableMethod("PUT"))
        assertFalse(FailoverPolicy.isRetryableMethod("DELETE"))
        assertFalse(FailoverPolicy.isRetryableMethod("PATCH"))
    }

    @Test
    fun `故障状态码判定`() {
        listOf(408, 425, 429, 500, 502, 503, 504, 522, 524, 530).forEach {
            assertTrue("$it 应为故障码", FailoverPolicy.isFailureCode(it))
        }

        listOf(200, 301, 400, 401, 403, 404, 409, 422).forEach {
            assertFalse("$it 不应为故障码", FailoverPolicy.isFailureCode(it))
        }
    }

    @Test
    fun `JSON 声明与内容不匹配识别劫持页`() {
        assertFalse(FailoverPolicy.isJsonMismatch("application/json", '{'.code))
        assertFalse(FailoverPolicy.isJsonMismatch("application/json; charset=utf-8", '['.code))

        assertTrue(FailoverPolicy.isJsonMismatch("application/json", '<'.code))

        assertFalse(FailoverPolicy.isJsonMismatch("text/html", '<'.code))
        assertFalse(FailoverPolicy.isJsonMismatch(null, '<'.code))

        assertFalse(FailoverPolicy.isJsonMismatch("application/json", null))
    }
}
