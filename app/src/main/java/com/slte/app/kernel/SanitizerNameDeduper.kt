// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/**
 * 订阅重名节点的自愈改写。
 *
 * 内核解析 `proxies:` 时对重名直接报 `proxy X is the duplicate name`，整份配置加载失败
 * （代理与策略组同名同样报错），而重名来自面板的命名模板，用户无从自救。
 * 这里在清洗阶段把重名改成唯一名：首个保留原名，后续按出现顺序追加 ` (2)`、` (3)`…；
 * 策略组与规则里的引用不动 —— 它们本来就只能绑定首个同名节点，改名后语义不变。
 *
 * 覆盖范围：顶层 `proxies:` 的块状条目。flow 写法（`- {name: x, …}`）与
 * `proxy-providers` 内联 payload 不在范围内（前者极罕见；后者内核按切片保存、不校验重名）。
 */
internal object SanitizerNameDeduper {

    private const val PROXIES_KEY = "proxies"

    private const val GROUPS_KEY = "proxy-groups"

    /** `- name:` 形式的条目起始（值可能是引号包起来的，这里只看键） */
    private val ITEM_NAME_KEY = Regex("^-\\s*name\\s*:")

    /** 未加引号的值以「空白 + #」结束，之后是注释 */
    private val COMMENT_START = Regex("\\s#")

    /**
     * 内核在解析订阅代理前已占用的名字：
     * - `PARSE_PROXIES` 预注册的 6 个内置代理，订阅再出现同名会直接报错；
     * - `GLOBAL` 虽不报错，但会被内核随后创建的 GLOBAL 伪策略组静默覆盖掉。
     */
    private val RESERVED_NAMES =
        setOf("DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE", "GLOBAL")

    /** 把顶层 `proxies:` 里的重名改成唯一名，返回改名条数。 */
    fun dedupeProxyNames(lines: MutableList<String>): Int {
        val proxies = blockRange(lines, PROXIES_KEY) ?: return 0
        val items = nameSpans(lines, proxies)
        if (items.isEmpty()) return 0

        // 先登记全部原有名字（含策略组名），保证生成的后缀不会撞上文档里已有的名字
        val taken = mutableSetOf<String>()
        taken += RESERVED_NAMES
        nameSpans(lines, blockRange(lines, GROUPS_KEY)).forEach { taken += it.value }
        items.forEach { taken += it.value }

        val seen = mutableSetOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        for (item in items) {
            if (seen.add(item.value)) continue
            val unique = uniqueName(item.value, taken)
            val line = lines[item.line]
            lines[item.line] = line.substring(0, item.start) + render(unique, item.quote) + line.substring(item.end)
            renamed += item.value to unique
        }

        if (renamed.isNotEmpty()) {
            val detail = renamed.joinToString(", ") { "${sanitizeLog(it.first)} → ${sanitizeLog(it.second)}" }
            AppLog.i(SanitizerRules.LOG_TAG, "检出 ${renamed.size} 个重名节点并已改名（内核遇重名会拒绝加载整份配置）：$detail")
        }
        return renamed.size
    }

    private fun uniqueName(
        base: String,
        taken: MutableSet<String>,
    ): String {
        var index = 2
        while (true) {
            val candidate = "$base ($index)"
            if (taken.add(candidate)) return candidate
            index++
        }
    }

    private fun render(
        name: String,
        quote: Char?,
    ): String = when (quote) {
        '"' -> "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        '\'' -> "'" + name.replace("'", "''") + "'"
        else -> name
    }

    private fun blockRange(
        lines: List<String>,
        key: String,
    ): Block? {
        val keyIndex = SanitizerYamlLines.topLevelBlockIndices(lines, key).firstOrNull() ?: return null
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, keyIndex) ?: return null
        var end = keyIndex + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                end++
                continue
            }
            if (SanitizerYamlLines.leadingIndent(line).length < itemIndent.length) break
            end++
        }
        return Block(itemIndent, keyIndex + 1, end)
    }

    private fun nameSpans(
        lines: List<String>,
        block: Block?,
    ): List<ItemSpan> {
        if (block == null) return emptyList()
        val spans = mutableListOf<ItemSpan>()
        for (i in block.start until block.end) {
            itemNameSpan(lines[i], block.itemIndent, i)?.let { spans.add(it) }
        }
        return spans
    }

    private fun itemNameSpan(
        line: String,
        itemIndent: String,
        lineIndex: Int,
    ): ItemSpan? {
        if (SanitizerYamlLines.leadingIndent(line) != itemIndent) return null
        val trimmed = line.trimStart()
        if (!ITEM_NAME_KEY.containsMatchIn(trimmed)) return null
        // flow 写法没有独立的 name 行，跳过（不破坏原文）
        if (trimmed.contains('{') || trimmed.contains('[')) return null

        val colon = SanitizerYamlLines.unquotedColonIndex(line)
        if (colon <= 0) return null
        var start = colon + 1
        while (start < line.length && line[start] == ' ') start++
        val rest = line.substring(start)
        if (rest.isEmpty()) return null

        val quote = rest.first().takeIf { it == '"' || it == '\'' }
        return if (quote != null) {
            val close = findClosingQuote(rest, quote) ?: return null
            val value = SanitizerYamlLines.unwrapQuotes(rest.substring(0, close + 1))
            if (value.isBlank()) return null
            ItemSpan(lineIndex, start, start + close + 1, value, quote)
        } else {
            val commentAt = COMMENT_START.find(rest)?.range?.first ?: rest.length
            val value = rest.substring(0, commentAt).trimEnd()
            if (value.isBlank()) return null
            ItemSpan(lineIndex, start, start + value.length, value, null)
        }
    }

    private fun findClosingQuote(
        text: String,
        quote: Char,
    ): Int? {
        var i = 1
        while (i < text.length) {
            val char = text[i]
            if (quote == '"' && char == '\\') {
                i += 2
                continue
            }
            if (char == quote) return i
            i++
        }
        return null
    }

    private data class Block(
        val itemIndent: String,
        val start: Int,
        val end: Int,
    )

    /** 条目名在原始行中的区间：`start`（含）到 `end`（不含）即名字字面量，含引号。 */
    private data class ItemSpan(
        val line: Int,
        val start: Int,
        val end: Int,
        val value: String,
        val quote: Char?,
    )
}
