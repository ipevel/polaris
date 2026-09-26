// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import com.slte.app.kernel.KernelProxyMemberKind
import com.slte.app.ui.screen.server.NodeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未连接（内核未运行）时的节点兜底名单。
 *
 * 回归背景（用户实测反馈）："首页那个连接不点，节点就全不显示"。
 * 节点行只来自内核策略组，未连接时整页只剩「暂无节点，请先更新订阅」——
 * 而订阅缓存里其实一直有节点名单（离线可用），只是没被用上。
 */
class NodesOfflineFallbackTest {

    @Test
    fun `兜底名单保留订阅顺序且不编造延迟`() {
        val nodes =
            listOf(
                NodeItem(id = 1, name = "🇭🇰 香港 01", delay = 88),
                NodeItem(id = 2, name = "🇯🇵 日本 02"),
            )

        val members = offlineMembersOf(nodes)

        assertEquals(
            "顺序必须与订阅一致（用户认的是订阅里的顺序）",
            listOf("🇭🇰 香港 01", "🇯🇵 日本 02"),
            members.map { it.name },
        )
        assertTrue(
            "离线拿不到探测结果，必须显示「未测」而不是沿用订阅里的旧延迟",
            members.all { it.delay == null },
        )
        assertTrue(
            "订阅里的条目都是节点，不能标成策略组",
            members.all { it.kind == KernelProxyMemberKind.NODE && !it.isGroup },
        )
    }

    @Test
    fun `没有缓存节点时兜底名单为空_页面回落到原有空态`() {
        assertTrue(offlineMembersOf(emptyList()).isEmpty())
    }
}
