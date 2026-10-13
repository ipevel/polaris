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
 * 随包种子（`app/src/main/assets/routing/providers/<键>.yaml`）也一并钉住：内核按
 * `<键>` + `FileExt()`（对所有 provider 一律 `.yaml`）找文件，种子名漂移 = 规则整组静默失效。
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

    /** 随包规则集种子目录；Gradle 单测的 workingDir 可能是 `app/` 或仓库根。 */
    private val seedDir: File? =
        findUpwards("app/src/main/assets/routing/providers")
            ?: File("src/main/assets/routing/providers").takeIf { it.isDirectory }

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
    fun `Kotlin 镜像的历史组名映射与内核逐字一致`() {
        val file = goTableFile
        assumeTrue("未找到内核分流表源码，跳过", file != null)
        val block = aliasesBlock(file!!.readText())
        val goAliases = parseGoAliases(block)
        assertEquals("历史组名映射必须逐字一致", goAliases, LegacyGroupAliases)
    }

    @Test
    fun `内核表的 AI 组有 151 条内联规则且声明本地规则集 ai`() {
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
        // 2026-10-14 起 AI 也吃本地规则集（dist/rules/ai.list → providers/polaris-rules/ai.yaml）
        assertEquals("AI 组必须声明本地规则集", listOf("ai"), providers(ai.body))
        assertEquals("内联规则不得重复", rules.size, rules.toSet().size)
    }

    @Test
    fun `国外穿墙排在所有具体平台组之后 只允许两个拦截组跟在它后面`() {
        val groups = goTable()
        assumeTrue("未找到内核分流表源码，跳过", groups != null)
        val list = groups!!
        val abroad = list.indexOfFirst { it.name == "🌏 国外穿墙" }
        assertTrue("内核表里没有「🌏 国外穿墙」组", abroad >= 0)
        // geolocation-!cn 是兜底分类：排在它后面的组永远命中不到，开关设成什么都不生效
        val allowedAfter = setOf("🍃 应用净化", "🛑 广告拦截")
        list.drop(abroad + 1).forEach { group ->
            assertTrue(
                "组「${group.name}」排在「🌏 国外穿墙」之后，会被 geolocation-!cn 整体吃掉",
                group.name in allowedAfter,
            )
        }
        list.take(abroad).forEach { group ->
            inlineRules(group.body).forEach { rule ->
                assertTrue(
                    "组「${group.name}」排在「🌏 国外穿墙」之前却声明了 $rule，国外穿墙组又会被整体吃掉",
                    rule != "GEOSITE,geolocation-!cn,{t}",
                )
            }
        }
    }

    @Test
    fun `随包种子文件名与内核表的 provider 键逐一对应`() {
        val groups = goTable()
        assumeTrue("未找到内核分流表源码，跳过", groups != null)
        val dir = seedDir
        assumeTrue("未找到随包种子目录，跳过", dir != null)
        val declared = groups!!.flatMap { providers(it.body) }.toSortedSet()
        assertEquals("内核表声明的 provider 数", 15, declared.size)
        val files = dir!!.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        assertEquals(
            "旧的 acl_*.yaml 种子必须清干净（内核按 <键>.yaml 读取）",
            emptyList<String>(),
            files.filter { it.startsWith("acl_") },
        )
        assertEquals(
            "种子文件名必须与内核表的 provider 键逐一对应",
            declared,
            files.filter { it.endsWith(".yaml") }.map { it.removeSuffix(".yaml") }.toSortedSet(),
        )
        files.forEach { name ->
            assertTrue("种子文件不得为空：$name", File(dir, name).length() > 0)
        }
    }

    private fun inlineRules(body: String): List<String> = Regex("\"([^\"]*\\{t\\})\"").findAll(body).map { it.groupValues[1] }.toList()

    private fun providers(body: String): List<String> {
        val slice = Regex("Providers:\\s*\\[\\]string\\{([^}]*)\\}").find(body) ?: return emptyList()
        return Regex("\"([^\"]+)\"").findAll(slice.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private companion object {
        /** 从当前工作目录逐级向上找仓库内文件（Gradle 单测的 workingDir 可能是 app/ 或仓库根）。 */
        fun findUpwards(relative: String): File? {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(6) {
                val candidate = File(dir, relative)
                if (candidate.exists()) return candidate
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

        /** 切出 `var legacyGroupAliases = map[string]string{ ... }` 的字面量。 */
        fun aliasesBlock(text: String): String {
            val start = text.indexOf("var legacyGroupAliases = map[string]string{")
            check(start >= 0) { "routing_table.go 中找不到 legacyGroupAliases 定义" }
            val end = text.indexOf("\n}", start)
            check(end > start) { "legacyGroupAliases 字面量未闭合" }
            return text.substring(start, end)
        }

        /** 解析 `"旧名": 目标,` 形式的别名表；目标可能是常量 AIGroupName。 */
        fun parseGoAliases(block: String): Map<String, String> {
            val aiName = Regex("const AIGroupName = \"([^\"]+)\"").let { _ -> "🤖 AI" }
            val out = LinkedHashMap<String, String>()
            Regex("\"([^\"]+)\":\\s*(?:\"([^\"]+)\"|([A-Za-z][A-Za-z0-9_]*))").findAll(block).forEach { m ->
                val target = m.groupValues[2].ifEmpty { if (m.groupValues[3] == "AIGroupName") aiName else m.groupValues[3] }
                out[m.groupValues[1]] = target
            }
            check(out.isNotEmpty()) { "legacyGroupAliases 里没解析出任何映射" }
            return out
        }
    }
}
