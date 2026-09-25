// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * routing.json 的 Kotlin↔Go 契约测试：字段名必须与 Go 侧
 * native/config/routing.State 的 json tag 一致（小写下划线），否则内核
 * 解码会静默回退为「本地分流关闭」。
 */
class RoutingStateStoreContractTest {

    // 与 RoutingStateStore 内部配置保持一致（encodeDefaults=true 保证
    // enabled 等带默认值的字段一定被写出，Go 侧不会读到缺省零值）
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `编码字段名与 Go State json tag 一致`() {
        val text =
            json.encodeToString(
                RoutingState.serializer(),
                RoutingState(
                    enabled = true,
                    groups = mapOf("📺 哔哩哔哩" to false),
                    directDomains = listOf("panel.example.cn"),
                    custom = listOf(RoutingCustomGroup(name = "x", url = "https://a/b.yaml", behavior = "domain", interval = 3600)),
                ),
            )

        assertTrue(text.contains("\"version\":1"))
        assertTrue(text.contains("\"enabled\":true"))
        assertTrue(text.contains("\"groups\":"))
        assertTrue(text.contains("\"custom\":"))
        assertTrue(text.contains("\"direct_domains\":[\"panel.example.cn\"]"))
        assertTrue(text.contains("\"name\":\"x\""))
        assertTrue(text.contains("\"url\":\"https://a/b.yaml\""))
        assertTrue(text.contains("\"behavior\":\"domain\""))
        assertTrue(text.contains("\"interval\":3600"))
        assertFalse(text.contains("组")) // 不允许出现非契约字段
    }

    @Test
    fun `未知字段被忽略且缺失字段取 Kotlin 默认值`() {
        val state =
            json.decodeFromString<RoutingState>(
                "{\"version\":1,\"groups\":{\"A\":true},\"future_field\":123}",
            )

        // 缺省 enabled=false 与内核 ReadState 的降级语义一致（缺省=关闭）；
        // 启用由 ensureDefault 写盘后的真实文件内容决定
        assertFalse(state.enabled)
        assertEquals(mapOf("A" to true), state.groups)
        assertTrue(state.custom.isEmpty())
    }

    @Test
    fun `roundtrip 保留组开关与自定义组`() {
        val original =
            RoutingState(
                enabled = false,
                groups = mapOf("🛑 广告拦截" to true, "📲 电报消息" to false),
                custom = listOf(
                    RoutingCustomGroup(name = "我的规则", url = "https://example.com/r.yaml"),
                ),
            )

        val decoded = json.decodeFromString<RoutingState>(json.encodeToString(RoutingState.serializer(), original))

        assertEquals(original, decoded)
    }
}
