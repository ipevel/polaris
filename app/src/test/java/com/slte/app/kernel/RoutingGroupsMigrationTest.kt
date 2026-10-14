// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置分流表的 Kotlin 侧契约：组名、位置、默认开关，以及历史组名保存设置的迁移。
 *
 * 覆盖两次改名：2026-10-13「AI 组三合一」，2026-10-14「23 组 → 12 组」。
 *
 * 迁移必须做在读取侧：改名后旧名会变成未知名被丢弃，用户自己调过的开关会静默
 * 退回内置默认值、拖动顺序会退回内置顺序。
 */
class RoutingGroupsMigrationTest {

    /** 2026-10-13 合并的三个 AI 旧组名。 */
    private val legacyAiNames = listOf("♊️ Google Gemini", "💬 Claude", "💬 OpenAI")

    /** 2026-10-14 合并进现有组的旧组名 → 合并后的当前组名。 */
    private val mergedAliases =
        linkedMapOf(
            "📲 X" to "📲 社交媒体",
            "📸 Instagram" to "📲 社交媒体",
            "📲 Facebook" to "📲 社交媒体",
            "📲 WhatsApp" to "📲 社交媒体",
            "📹 油管视频" to "📺 流媒体",
            "🎥 奈飞视频" to "📺 流媒体",
            "Ⓜ️ 微软云盘" to "Ⓜ️ 微软",
            "📺 哔哩哔哩" to "🎯 国内直连",
            "🎶 网易音乐" to "🎯 国内直连",
        )

    /**
     * 已取消但**刻意不映射**的四个旧组名。硬映射会把「我关掉 Google」变成
     * 「我关掉整个国外穿墙」，比丢一个开关更糟。
     */
    private val unmappedCancelledNames =
        listOf("🐱 GitHub", "🌏 Google Play", "🌏 Google", "Ⓜ️ 微软Bing")

    @Test
    fun `内置表是 12 组且顺序与内核表一致`() {
        assertEquals(
            listOf(
                "📲 电报消息",
                "🎧 TikTok",
                "📲 社交媒体",
                "📺 流媒体",
                AI_GROUP_NAME,
                "🎮 游戏平台",
                "Ⓜ️ 微软",
                "🍎 苹果服务",
                "🎯 国内直连",
                "🌏 国外穿墙",
                "🍃 应用净化",
                "🛑 广告拦截",
            ),
            RoutingGroups.map { it.name },
        )
    }

    @Test
    fun `三个旧 AI 组已合并为 AI 组且位置不变`() {
        legacyAiNames.forEach { legacy ->
            assertFalse("旧组名「$legacy」不应再出现在内置表里", RoutingGroups.any { it.name == legacy })
        }
        val names = RoutingGroups.map { it.name }
        val ai = names.indexOf(AI_GROUP_NAME)
        assertTrue("缺少「$AI_GROUP_NAME」组", ai >= 0)
        assertEquals("AI 组应紧跟在「📺 流媒体」之后", names.indexOf("📺 流媒体") + 1, ai)
        assertEquals("AI 组应紧挨在「🎮 游戏平台」之前", names.indexOf("🎮 游戏平台") - 1, ai)
    }

    @Test
    fun `AI 组默认开启并走代理`() {
        val ai = RoutingGroups.single { it.name == AI_GROUP_NAME }
        assertTrue(ai.defaultOn)
        assertEquals("proxy", ai.defaultOut)
    }

    @Test
    fun `社交媒体组默认开启并走代理`() {
        // 内核表与 Windows 表都是 true，Kotlin 镜像此前漂移成 false——
        // 全新安装的安卓端这几个组会显示为关（开关状态与实际行为不符）。
        val social = RoutingGroups.single { it.name == "📲 社交媒体" }
        assertTrue(social.defaultOn)
        assertEquals("proxy", social.defaultOut)
    }

    @Test
    fun `微软组默认开启且直连`() {
        // 2026-10-13 用户反馈 OneDrive 走代理「要不太费流量了」，本次该组默认直连。
        // 注意：这里断言的是**组级出口**（defaultOut = direct）；该组的覆盖范围在
        // 2026-10-14 两次收窄中变更过，不代表组内每个域名都直连。
        val microsoft = RoutingGroups.single { it.name == "Ⓜ️ 微软" }
        assertTrue(microsoft.defaultOn)
        assertEquals("direct", microsoft.defaultOut)
    }

    @Test
    fun `广告拦截默认开启而应用净化默认关闭`() {
        // 内核表 2026-10-14 把「🛑 广告拦截」从默认关闭改成默认开启，
        // Kotlin 镜像当时还停在 false——新装的机器不会拦广告。
        assertTrue(RoutingGroups.single { it.name == "🛑 广告拦截" }.defaultOn)
        assertFalse(RoutingGroups.single { it.name == "🍃 应用净化" }.defaultOn)
    }

    @Test
    fun `已取消的四个组名仍在表里消失`() {
        unmappedCancelledNames.forEach { gone ->
            assertFalse("已取消的组「$gone」不应再出现在内置表里", RoutingGroups.any { it.name == gone })
        }
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
    fun `2026-10-14 的合并组同样按任一旧名为开迁移`() {
        val groups =
            migrateLegacyGroups(
                mapOf("📲 Facebook" to false, "📸 Instagram" to true, "🎶 网易音乐" to false),
            )
        assertEquals(mapOf("📲 社交媒体" to true, "🎯 国内直连" to false), groups)
    }

    @Test
    fun `合并组里显式的新组名同样优先于旧键`() {
        val groups =
            migrateLegacyGroups(mapOf("📲 社交媒体" to false, "📲 WhatsApp" to true))
        assertEquals(mapOf("📲 社交媒体" to false), groups)
    }

    @Test
    fun `其余组开关与未知名原样保留`() {
        val groups =
            migrateLegacyGroups(
                mapOf("🛑 广告拦截" to false, "💬 Claude" to true, "别的组" to true),
            )
        assertEquals(mapOf("🛑 广告拦截" to false, "别的组" to true, AI_GROUP_NAME to true), groups)
    }

    @Test
    fun `没有旧组名时原样返回同一实例`() {
        val groups = mapOf("🛑 广告拦截" to false)
        assertTrue("没有旧组名时不应重建 map", groups === migrateLegacyGroups(groups))
    }

    @Test
    fun `顺序里的旧组名折叠到最靠前的位置并去重`() {
        val order = migrateLegacyOrder(listOf("💬 Claude", "📲 电报消息", "♊️ Google Gemini"))
        assertEquals(listOf(AI_GROUP_NAME, "📲 电报消息"), order)
    }

    @Test
    fun `合并组的旧名在顺序里也折叠到最靠前的位置`() {
        val order = migrateLegacyOrder(listOf("📲 X", "📲 Facebook", "🎶 网易音乐", "🎯 国内直连"))
        assertEquals(listOf("📲 社交媒体", "🎯 国内直连"), order)
    }

    @Test
    fun `orderedGroups 认旧组名且绝不丢组`() {
        val ordered = orderedGroups(listOf("💬 Claude", "📲 电报消息"))
        assertEquals("重排不得丢组", RoutingGroups.size, ordered.size)
        assertEquals(AI_GROUP_NAME, ordered.first().name)
        assertEquals("📲 电报消息", ordered[1].name)
    }

    @Test
    fun `orderedGroups 认合并组旧名且绝不丢组`() {
        val ordered = orderedGroups(listOf("📲 Facebook", "📲 X", "🛑 广告拦截"))
        assertEquals("重排不得丢组", RoutingGroups.size, ordered.size)
        assertEquals("📲 社交媒体", ordered.first().name)
        assertEquals("🛑 广告拦截", ordered[1].name)
    }

    @Test
    fun `历史组名一律映射到同一个当前组名`() {
        legacyAiNames.forEach { assertEquals(AI_GROUP_NAME, canonicalGroupName(it)) }
        mergedAliases.forEach { (legacy, current) ->
            assertEquals("旧组名「$legacy」的映射不对", current, canonicalGroupName(legacy))
        }
    }

    @Test
    fun `已取消的组名刻意不做映射`() {
        unmappedCancelledNames.forEach { name ->
            assertEquals("已取消的「$name」不应被映射", name, canonicalGroupName(name))
        }
    }

    @Test
    fun `不是历史的组名原样返回`() {
        assertEquals("不是历史名", "不是历史名", canonicalGroupName("不是历史名"))
        assertEquals("📲 电报消息", canonicalGroupName("📲 电报消息"))
    }

    @Test
    fun `AI 组名落在内核保留名里`() {
        assertTrue("自定义组不得占用 AI 组名", AI_GROUP_NAME in RoutingInputValidator.reservedNames)
    }
}
