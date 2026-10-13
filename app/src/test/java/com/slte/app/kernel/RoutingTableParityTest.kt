// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Kotlin 镜像（[RoutingGroups]）与内核真源（`routing_table.go` 的 `Table`）的一致性契约。
 *
 * Kotlin 侧只是设置页的展示与写开关用的镜像，名称/顺序/默认值一旦漂移，开关就写不进
 * 对应组——2026-10-13 之前 `📲 Facebook` / `📲 WhatsApp` 的默认开关漂移就是这么漏出去的。
 * 所以这里直接读 Go 源文件做结构比对：不依赖内核产物、不需要真机，CI 上也会跑。
 *
 * 找不到内核源码（例如单独构建 app 模块）时跳过，不误报。
 */
class RoutingTableParityTest {

    private data class GoGroup(
        val name: String,
        val defaultOn: Boolean,
        val defaultOut: String,
        val body: String,
    )

    private val goTableFile: File? =
        findUpwards("kernel-core/src/main/golang/native/config/routing/routing_table.go")

    private fun goTable(): List<GoGroup>? {
        val file = goTableFile ?: return null
        return parseGoTable(file.readText())
    }

    @Test
    fun `Kotlin 镜像与内核表的名称 顺序 默认开关 默认出口逐字一致`() {
        val groups = goTable()
        assumeTrue("未找到内核分流表源码，跳过", groups != null)
        val go = groups!!
        assertEquals("组名与顺序必须逐字一致", go.map { it.name }, RoutingGroups.map { it.name })
        assertEquals("默认开关必须一致", go.map { it.defaultOn }, RoutingGroups.map { it.defaultOn })
        assertEquals("默认出口必须一致", go.map { it.defaultOut }, RoutingGroups.map { it.defaultOut })
    }

    @Test
    fun `内核表的 AI 组有 151 条内联规则且不含 Google 分类`() {
        val groups = goTable()
        assumeTrue("未找到内核分流表源码，跳过", groups != null)
        val ai = groups!!.singleOrNull { it.name == AI_GROUP_NAME }
        assertNotNull("内核表里没有「$AI_GROUP_NAME」组", ai)
        val rules = inlineRules(ai!!.body)
        assertEquals("AI 组内联规则条数", 151, rules.size)
        assertEquals(
            "前 4 条必须是 GEOSITE 分类（顺序即规格）",
            listOf(
                "GEOSITE,category-ai-!cn,{t}",
                "GEOSITE,openai,{t}",
                "GEOSITE,anthropic,{t}",
                "GEOSITE,google-gemini,{t}",
            ),
            rules.take(4),
        )
        assertEquals(
            "AI 组不得带 Google 分类（会把 🌏 Google / 🌏 Google Play 整体吃掉）",
            emptyList<String>(),
            rules.filter { it == "GEOSITE,google,{t}" || it == "GEOSITE,google-play,{t}" },
        )
        assertEquals("内联规则不得重复", rules.size, rules.toSet().size)
    }

    @Test
    fun `Google Play 排在 Google 之前 且 Google 之前没有组声明 google 分类`() {
        val groups = goTable()
        assumeTrue("未找到内核分流表源码，跳过", groups != null)
        val list = groups!!
        val google = list.indexOfFirst { it.name == "🌏 Google" }
        val play = list.indexOfFirst { it.name == "🌏 Google Play" }
        val ai = list.indexOfFirst { it.name == AI_GROUP_NAME }
        assertTrue("内核表里没有「🌏 Google」组", google >= 0)
        assertTrue("内核表里没有「🌏 Google Play」组", play >= 0)
        // google-play 的 10 条里有 8 条同时也在 google 分类里，排在后面就是死开关。
        assertTrue("「🌏 Google Play」必须排在「🌏 Google」之前：play=$play google=$google", play < google)
        assertTrue("AI 组必须排在「🌏 Google Play」之前：ai=$ai play=$play", ai < play)
        list.take(google).forEach { group ->
            inlineRules(group.body).forEach { rule ->
                assertTrue(
                    "组「${group.name}」排在「🌏 Google」之前却声明了 $rule，Google 组又会被整体吃掉",
                    rule != "GEOSITE,google,{t}",
                )
            }
        }
    }

    private fun inlineRules(body: String): List<String> = Regex("\"([^\"]*\\{t\\})\"").findAll(body).map { it.groupValues[1] }.toList()

    private companion object {
        /** 从当前工作目录逐级向上找仓库内文件（Gradle 单测的 workingDir 可能是 app/ 或仓库根）。 */
        fun findUpwards(relative: String): File? {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(6) {
                val candidate = File(dir, relative)
                if (candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            return null
        }

        /**
         * 从 `var Table = []RuleGroup{` 起到列首的 `}` 为止切出 Table 字面量，
         * 再按 `{Name: ...}` 头逐个切出条目（`InlineRules` 里的 `{t}` 括号成对，
         * 所以嵌套条目不需要额外的括号配平）。
         */
        fun parseGoTable(text: String): List<GoGroup> {
            val start = text.indexOf("var Table = []RuleGroup{")
            check(start >= 0) { "routing_table.go 中找不到 Table 定义" }
            val end = text.indexOf("\n}", start)
            check(end > start) { "Table 字面量未闭合" }
            val block = text.substring(start, end)
            val header = Regex("\\{Name:\\s*\"([^\"]+)\",\\s*DefaultOn:\\s*(true|false),\\s*DefaultOut:\\s*\"([^\"]+)\"")
            val matches = header.findAll(block).toList()
            check(matches.isNotEmpty()) { "Table 里没解析出任何分组" }
            return matches.mapIndexed { i, m ->
                val bodyEnd = matches.getOrNull(i + 1)?.range?.first ?: block.length
                GoGroup(
                    name = m.groupValues[1],
                    defaultOn = m.groupValues[2] == "true",
                    defaultOut = m.groupValues[3],
                    body = block.substring(m.range.first, bodyEnd),
                )
            }
        }
    }
}
