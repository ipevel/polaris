// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {
    @Test
    fun `TTL 常量与原实现完全一致`() {
        assertEquals(30_000L, CachePolicy.SUBSCRIBE_INFO_TTL_MS)
        assertEquals(30_000L, CachePolicy.USER_INFO_TTL_MS)
        assertEquals(30L * 60_000L, CachePolicy.SERVER_NODES_TTL_MS)
    }

    @Test
    fun `零时间戳一律视为不新鲜`() {
        val now = 1_700_000_000_000L
        assertFalse(CachePolicy.isFresh(0L, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS))
        assertFalse(CachePolicy.isFresh(0L, now, CachePolicy.SERVER_NODES_TTL_MS))
    }

    @Test
    fun `未到期视为新鲜`() {
        val now = 1_700_000_000_000L
        assertTrue(CachePolicy.isFresh(now, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS))
        assertTrue(CachePolicy.isFresh(now - 29_999L, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS))
        assertTrue(CachePolicy.isFresh(now - (CachePolicy.SERVER_NODES_TTL_MS - 1), now, CachePolicy.SERVER_NODES_TTL_MS))
    }

    @Test
    fun `恰好到期视为过期`() {
        val now = 1_700_000_000_000L
        assertFalse(CachePolicy.isFresh(now - CachePolicy.SUBSCRIBE_INFO_TTL_MS, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS))
        assertFalse(CachePolicy.isFresh(now - CachePolicy.SERVER_NODES_TTL_MS, now, CachePolicy.SERVER_NODES_TTL_MS))
        assertFalse(CachePolicy.isFresh(now - CachePolicy.SERVER_NODES_TTL_MS - 1, now, CachePolicy.SERVER_NODES_TTL_MS))
    }

    @Test
    fun `未来时间戳（时钟回拨）仍按未到期处理`() {
        val now = 1_700_000_000_000L
        assertTrue(CachePolicy.isFresh(now + 60_000L, now, CachePolicy.SUBSCRIBE_INFO_TTL_MS))
    }
}
