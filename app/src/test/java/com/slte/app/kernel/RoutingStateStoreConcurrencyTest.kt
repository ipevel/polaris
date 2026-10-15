// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * D5 回归：`routing.json` 的「读-改-写」必须是原子的。
 *
 * 背景：每个写入口都是 `load() → copy → write()`，而 [RoutingStateStore.write] 自己只保证
 * 「写 .tmp + rename」原子。两个调用方各自读到同一份旧状态、再先后写盘时，后写者会用
 * 陈旧快照覆盖先写者已落盘的结果（丢更新）——用户表现为「刚加/刚删的分流组过一会儿又回来了」。
 *
 * 这里用真实文件 IO（Robolectric 提供 filesDir）而不是 mock：只有真的让两条线程同时
 * 读同一份 routing.json、再各自 rename，才能复现丢更新；去掉 [RoutingStateStore] 的锁后
 * 两个用例都会稳定失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class RoutingStateStoreConcurrencyTest {
    private lateinit var store: RoutingStateStore

    @Before
    fun setUp() {
        store = RoutingStateStore(RuntimeEnvironment.getApplication())
        // 每个用例都从「没有 routing.json」开始：上一条用例的落盘结果会让计数断言失真。
        store.stateFile().delete()
        File(store.stateFile().parentFile, "routing.json.tmp").delete()
    }

    /** 在 [count] 条线程上同时执行 [block]，用栅栏尽量让它们真的并发进入临界区。 */
    private fun <T> concurrently(
        count: Int,
        block: (Int) -> T,
    ): List<T> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(count)
        return try {
            val futures =
                (0 until count).map { i ->
                    pool.submit<T> {
                        start.await()
                        block(i)
                    }
                }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `并发新增不同名字的自定义组不会丢更新`() {
        val count = 8

        val results =
            concurrently(count) { i ->
                store.addCustomGroup(
                    RoutingCustomGroup(name = "grp-$i", url = "https://example.com/$i.yaml"),
                )
            }

        assertTrue("每次写入都应成功，实际 $results", results.all { it })
        assertEquals(
            "并发读-改-写必须原子：无锁时后写者会用旧快照覆盖先写者",
            (0 until count).map { "grp-$it" }.toSet(),
            store.load().custom.map { it.name }.toSet(),
        )
    }

    @Test
    fun `并发开关写入与自定义组写入互不覆盖`() {
        val adds = 4
        val toggles = 4
        val groupName = RoutingGroups.first().name

        val results =
            concurrently(adds + toggles) { i ->
                if (i < adds) {
                    store.addCustomGroup(
                        RoutingCustomGroup(name = "mix-$i", url = "https://example.com/mix-$i.yaml"),
                    )
                } else {
                    store.setEnabled(true) && store.setGroupEnabled(groupName, true)
                }
            }

        val state = store.load()
        assertTrue("每次写入都应成功，实际 $results", results.all { it })
        assertTrue("总开关不得被自定义组写入覆盖", state.enabled)
        assertEquals("组开关不得被自定义组写入覆盖", true, state.groups[groupName])
        assertEquals(
            "并发新增的自定义组不得被开关写入覆盖",
            (0 until adds).map { "mix-$it" }.toSet(),
            state.custom.map { it.name }.toSet(),
        )
    }
}
