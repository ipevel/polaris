// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-13「AI 组三合一」的 Kotlin 侧契约：组名、位置、默认开关，以及旧组名
 * （`♊️ Google Gemini` / `💬 Claude` / `💬 OpenAI`）保存设置的迁移。
 *
 * 迁移必须做在读取侧：改名后旧名会变成未知名被丢弃，用户自己调过的开关会静默
 * 退回内置默认值、拖动顺序会退回内置顺序。
 */
class RoutingGroupsMigrationTest {

    private val legacyNames = listOf("♊️ Google Gemini", "💬 Claude", "💬 OpenAI")

    @Test
    fun `三个旧组已合并为 AI 组且位置不变`() {
        legacyNames.forEach { legacy ->
            assertFalse("旧组名「$legacy」不应再出现在内置表里", RoutingGroups.any { it.name == legacy })
        }
        val names = RoutingGroups.map { it.name }
        val ai = names.indexOf(AI_GROUP_NAME)
        assertTrue("缺少「$AI_GROUP_NAME」组", ai >= 0)
        assertEquals("AI 组应紧跟在 WhatsApp 之后", names.indexOf("📲 WhatsApp") + 1, ai)
        assertEquals("AI 组应紧挨在 GitHub 之前", names.indexOf("🐱 GitHub") - 1, ai)
    }

    @Test
    fun `AI 组默认开启并走代理`() {
        val ai = RoutingGroups.single { it.name == AI_GROUP_NAME }
        assertTrue(ai.defaultOn)
        assertEquals("proxy", ai.defaultOut)
    }

    @Test
    fun `Facebook 与 WhatsApp 的默认开关与内核表一致`() {
        // 内核表与 Windows 表都是 true，Kotlin 镜像此前漂移成 false——
        // 全新安装的安卓端这两个组会显示为关（开关状态与实际行为不符）。
        assertTrue(RoutingGroups.single { it.name == "📲 Facebook" }.defaultOn)
        assertTrue(RoutingGroups.single { it.name == "📲 WhatsApp" }.defaultOn)
    }

    @Test
    fun `旧组名任一为开则新组为开`() {
        val groups = migrateLegacyGroups(mapOf("💬 Claude" to false, "💬 OpenAI" to true))
        assertEquals(mapOf(AI_GROUP_NAME to true), groups)
    }

    @Test
    fun `旧组名全关则新组为关`() {
        val groups = migrateLegacyGroups(mapOf("💬 Claude" to false, "♊️ Google Gemini" to false))
        assertEquals(mapOf(AI_GROUP_NAME to false), groups)
    }

    @Test
    fun `显式的新组名优先于残留旧键`() {
        // 否则用户在新版里把 AI 组关掉后，文件里残留的旧键会把它强行打开。
        val groups = migrateLegacyGroups(mapOf(AI_GROUP_NAME to false, "💬 OpenAI" to true))
        assertEquals(mapOf(AI_GROUP_NAME to false), groups)
    }

    @Test
    fun `其余组开关与未知名原样保留`() {
        val groups =
            migrateLegacyGroups(
                mapOf("📺 哔哩哔哩" to false, "💬 Claude" to true, "别的组" to true),
            )
        assertEquals(mapOf("📺 哔哩哔哩" to false, "别的组" to true, AI_GROUP_NAME to true), groups)
    }

    @Test
    fun `没有旧组名时原样返回同一实例`() {
        val groups = mapOf("📺 哔哩哔哩" to false)
        assertTrue("没有旧组名时不应重建 map", groups === migrateLegacyGroups(groups))
    }

    @Test
    fun `顺序里的旧组名折叠到最靠前的位置并去重`() {
        val order = migrateLegacyOrder(listOf("💬 Claude", "📲 电报消息", "♊️ Google Gemini"))
        assertEquals(listOf(AI_GROUP_NAME, "📲 电报消息"), order)
    }

    @Test
    fun `orderedGroups 认旧组名且绝不丢组`() {
        val ordered = orderedGroups(listOf("💬 Claude", "📲 电报消息"))
        assertEquals("重排不得丢组", RoutingGroups.size, ordered.size)
        assertEquals(AI_GROUP_NAME, ordered.first().name)
        assertEquals("📲 电报消息", ordered[1].name)
    }

    @Test
    fun `历史组名一律映射到同一个当前组名`() {
        legacyNames.forEach { assertEquals(AI_GROUP_NAME, canonicalGroupName(it)) }
        assertEquals("📺 哔哩哔哩", canonicalGroupName("📺 哔哩哔哩"))
    }

    @Test
    fun `AI 组名落在内核保留名里`() {
        assertTrue("自定义组不得占用 AI 组名", AI_GROUP_NAME in RoutingInputValidator.reservedNames)
    }
}
