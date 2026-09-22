// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import java.util.Collections
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryPreferencesConcurrencyTest {
    @Test
    fun `多线程并发写入不丢键`() {
        val prefs = InMemoryPreferences()
        val threads = 8
        val perThread = 200
        val start = CountDownLatch(1)

        val workers =
            (0 until threads).map { t ->
                Thread {
                    start.await()
                    for (i in 0 until perThread) {
                        prefs.edit().putString("k-$t-$i", "v").apply()
                    }
                }
            }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join() }

        assertEquals(threads * perThread, prefs.getAll().size)
        (0 until threads).forEach { t ->
            assertEquals("v", prefs.getString("k-$t-0", null))
            assertEquals("v", prefs.getString("k-$t-${perThread - 1}", null))
        }
    }

    @Test
    fun `读写并发时读取不抛异常且写入不丢键`() {
        val prefs = InMemoryPreferences()
        prefs.edit().putString("seed", "v").apply()
        val start = CountDownLatch(1)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        val reader =
            Thread {
                start.await()
                repeat(5_000) {
                    try {
                        prefs.getAll()
                        prefs.getString("seed", null)
                        prefs.getStringSet("seed", null)
                    } catch (e: Throwable) {
                        failures.add(e)
                    }
                }
            }
        val writers =
            (0 until 4).map { t ->
                Thread {
                    start.await()
                    repeat(200) { i -> prefs.edit().putString("k-$t-$i", "v").apply() }
                }
            }

        (listOf(reader) + writers).forEach { it.start() }
        start.countDown()
        (listOf(reader) + writers).forEach { it.join() }

        assertEquals(failures.toList(), emptyList<Throwable>())
        assertEquals(1 + 4 * 200, prefs.getAll().size)
    }
}
