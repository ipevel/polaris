// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunApiTest {
    @Test
    fun `成功返回 success`() = runBlocking {
        val result = runApi { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `异常转为 failure`() = runBlocking {
        val boom = IllegalStateException("boom")
        val result = runApi { throw boom }
        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `CancellationException 重抛而非吞掉`() {
        val cancelled = CancellationException("cancel")
        try {
            runBlocking {
                runApi { throw cancelled }
            }
            throw AssertionError("CancellationException 应被重抛")
        } catch (e: CancellationException) {
            assertEquals("cancel", e.message)
        }
    }
}
