// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坏地址族记忆的持久化（冷启动优化）。
 *
 * 三条安全约束必须成立，否则"记住坏地址"会变成新的坑：
 * 只影响排序、有 TTL、成功后立刻自愈。
 */
class DnsFailureStoreTest {
    private val store = DnsFailureStore(InMemoryPreferences())

    @Test
    fun `记录后能读回，且带 TTL`() {
        store.markFailed(HOST, "v4", ttlMs = 60_000L)

        val loaded = store.loadFamilies()

        assertEquals(1, loaded.size)
        assertTrue(loaded.keys.single().endsWith("|v4"))
        assertTrue("过期时间必须落在未来", loaded.values.single() > System.currentTimeMillis())
    }

    @Test
    fun `过期项不会被读出，并顺手清理`() {
        store.markFailed(HOST, "v4", ttlMs = -1L)

        assertTrue("已过期的记忆不该再生效", store.loadFamilies().isEmpty())
        // 再读一次仍为空（说明已经写回清理过）
        assertTrue(store.loadFamilies().isEmpty())
    }

    @Test
    fun `连接成功会立刻清掉该族记忆`() {
        store.markFailed(HOST, "v4", ttlMs = 60_000L)
        store.markFailed(HOST, "v6", ttlMs = 60_000L)

        store.markSucceeded(HOST, "v4")

        val loaded = store.loadFamilies()
        assertEquals("只该清掉成功的那一族", 1, loaded.size)
        assertTrue(loaded.keys.single().endsWith("|v6"))
    }

    @Test
    fun `主机名哈希不落明文且对同一主机稳定`() {
        // 用中立示例域名，不写真实面板主机：仓库里不应固化用户的私有部署信息。
        val hash = store.hostHash("App.Example-Panel.Test")

        assertFalse("明文偏好里不该出现面板域名的任何片段", hash.contains("example-panel"))
        assertFalse("哈希结果必须是十六进制摘要，不能回显原文", hash.contains("Example"))
        assertEquals(
            "大小写与空格不应产生两份记忆",
            hash,
            store.hostHash("  app.example-panel.test  "),
        )
        assertTrue(store.hostHash("").isNotBlank())
    }

    private companion object {
        const val HOST = "panel.example.com"
    }
}
