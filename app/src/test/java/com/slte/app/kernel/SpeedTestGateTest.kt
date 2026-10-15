// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 后台测速去重门：运行日志 08:24:40.205 / 08:24:41.669 / 08:24:52.025 / 08:24:55.353
 * 那 4 次 29 节点全量拨测不应再发生——窗口内的后续入口只复用结果。
 *
 * 用 Robolectric 是因为去重命中分支会走 AppLog.d → android.util.Log。
 * sdk 固定 34：本机 JDK 17，Robolectric 跑 targetSdk 36 需要 Java 21。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class SpeedTestGateTest {
    private var clock = 0L
    private var runs = 0

    @After
    fun tearDown() {
        SpeedTestGate.reset()
        SpeedTestGate.nowMs = { System.nanoTime() / 1_000_000L }
    }

    private fun installClock() {
        clock = 0L
        runs = 0
        SpeedTestGate.nowMs = { clock }
    }

    private suspend fun probe(
        force: Boolean = false,
        cached: Map<String, Int> = mapOf("cached" to 1),
    ): Map<String, Int> = SpeedTestGate.run(force = force, cached = { cached }) {
        runs++
        mapOf("probe" to runs)
    }

    @Test
    fun `窗口内的后续入口只复用结果不重复拨测`() = runTest {
        installClock()

        assertEquals(mapOf("probe" to 1), probe())
        clock += 1_000L
        assertEquals(mapOf("cached" to 1), probe())
        clock += 13_000L
        assertEquals(mapOf("cached" to 1), probe())
        assertEquals(1, runs)
    }

    @Test
    fun `窗口到期后重新真测`() = runTest {
        installClock()

        probe()
        clock += SpeedTestGate.REUSE_WINDOW_MS
        assertEquals(mapOf("probe" to 2), probe())
        assertEquals(2, runs)
    }

    @Test
    fun `force 忽略窗口`() = runTest {
        installClock()

        probe()
        clock += 100L
        assertEquals(mapOf("probe" to 2), probe(force = true))
        assertEquals(2, runs)
    }

    @Test
    fun `空结果不占窗口以便内核未就绪时继续重试`() = runTest {
        installClock()
        var calls = 0

        SpeedTestGate.run(cached = { emptyMap() }) {
            calls++
            emptyMap()
        }
        clock += 1_000L
        SpeedTestGate.run(cached = { emptyMap() }) {
            calls++
            mapOf("a" to 1)
        }

        assertEquals(2, calls)
    }

    @Test
    fun `并发入口排队后复用而不是一起拨测`() = runTest {
        installClock()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0

        val first =
            async {
                SpeedTestGate.run(cached = { mapOf("cached" to 1) }) {
                    calls++
                    started.complete(Unit)
                    release.await()
                    mapOf("probe" to 1)
                }
            }
        started.await()
        val second =
            async {
                SpeedTestGate.run(cached = { mapOf("cached" to 1) }) {
                    calls++
                    mapOf("probe" to 2)
                }
            }
        release.complete(Unit)

        assertEquals(mapOf("probe" to 1), first.await())
        assertEquals(mapOf("cached" to 1), second.await())
        assertEquals(1, calls)
    }

    @Test
    fun `探测抛异常后时间戳回滚不会把后续入口锁死在缓存上`() = runTest {
        installClock()

        runCatching {
            SpeedTestGate.run(cached = { mapOf("cached" to 1) }) {
                runs++
                error("拨测崩了")
            }
        }
        assertEquals(1, runs)

        // D4：异常路径若不回滚 lastStartedAtMs，紧随其后的入口会命中 15 秒窗口，
        // 只回放旧缓存且不再拨测——用户会看到"测速按钮失灵"。
        clock += 100L
        assertEquals(mapOf("probe" to 2), probe())
        assertEquals(2, runs)
    }

    @Test
    fun `探测被取消后时间戳回滚`() = runTest {
        installClock()
        val entered = CompletableDeferred<Unit>()

        val job =
            launch {
                SpeedTestGate.run(cached = { mapOf("cached" to 1) }) {
                    runs++
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
        entered.await()
        job.cancelAndJoin()
        assertEquals(1, runs)

        // 取消（用户退到后台/切节点）也必须回滚，否则窗口内所有入口只回放旧缓存。
        clock += 100L
        assertEquals(mapOf("probe" to 2), probe())
        assertEquals(2, runs)
    }
}
