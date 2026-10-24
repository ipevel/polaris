// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.support.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 内置分流表结构版本（[ROUTING_TABLE_SCHEMA]）迁移。
 *
 * 背景：2026-10-24 新增「☁️ PikPak」组（12 → 13 组）。该组的 7 条规则必须排在
 * 「🎯 国内直连」之前，否则 PikPak 的下载 CDN（*.sandai.net，解析到国内 IP）会被
 * `GEOSITE,cn` / `GEOIP,CN` 先命中而判直连。
 *
 * 危险路径：`routing.json` 里存着用户拖动过的**完整组顺序**（必然含「🎯 国内直连」）。
 * [orderedGroups] 会把未出现在顺序里的组按内置顺序追加到**末尾**，于是新组排到
 * 国内直连之后、规则失效。所以表结构变更时必须在读盘时重置顺序（开关不动）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class RoutingStateStoreTableSchemaMigrationTest {
    private lateinit var store: RoutingStateStore

    @Before
    fun setUp() {
        store = RoutingStateStore(RuntimeEnvironment.getApplication())
        store.stateFile().delete()
        store.stateFile().parentFile?.mkdirs()
    }

    private fun writeRaw(text: String) {
        store.stateFile().writeText(text)
    }

    @Test
    fun `旧版本（version 1）的自定义顺序被重置为内置顺序`() {
        writeRaw(
            """{"version":1,"enabled":true,"groups":{"🎯 国内直连":true},""" +
                """"order":["🎯 国内直连","🛑 广告拦截","🌏 国外穿墙"]}""",
        )

        val state = store.loadSanitized().state

        assertEquals("顺序必须被重置（否则新组排到国内直连之后）", emptyList<String>(), state.order)
        assertEquals(ROUTING_TABLE_SCHEMA, state.version)
        // 开关不受影响：用户拨的开关必须保留
        assertEquals(true, state.groups["🎯 国内直连"])
    }

    @Test
    fun `旧版本迁移后落盘的版本是当前结构版本`() {
        writeRaw("""{"version":1,"enabled":true,"order":["🎯 国内直连"]}""")

        store.loadSanitized()

        // 再读一次：迁移只发生一次，不得反复清空
        val again = store.loadSanitized().state
        assertTrue(
            "落盘必须带当前版本，否则下一次读盘会把用户新拖的顺序再清一次",
            store.stateFile().readText().contains("\"version\":$ROUTING_TABLE_SCHEMA"),
        )
        assertEquals(emptyList<String>(), again.order)
    }

    @Test
    fun `当前版本的自定义顺序原样保留`() {
        writeRaw(
            """{"version":$ROUTING_TABLE_SCHEMA,"enabled":true,""" +
                """"order":["🛑 广告拦截","🌏 国外穿墙"]}""",
        )

        val state = store.loadSanitized().state

        assertEquals(listOf("🛑 广告拦截", "🌏 国外穿墙"), state.order)
    }

    @Test
    fun `用户重新拖动排序后落盘带当前版本，再次读取不会被清掉`() {
        // 老文件（version 1、无顺序）→ 用户在新版里拖动排序
        writeRaw("""{"version":1,"enabled":true}""")
        assertTrue(store.setGroupOrder(listOf("🛑 广告拦截", "🌏 国外穿墙")))

        val state = store.loadSanitized().state

        assertEquals(
            "刚保存的顺序不能被当成旧版本顺序清掉",
            listOf("🛑 广告拦截", "🌏 国外穿墙"),
            state.order,
        )
    }

    @Test
    fun `只有非法条目没有顺序迁移时不回写磁盘`() {
        writeRaw("""{"version":1,"enabled":true,"custom":[]}""")
        val before = store.stateFile().readText()

        store.loadSanitized()

        assertEquals("没有顺序可重置时不得整文件重写", before, store.stateFile().readText())
    }
}
