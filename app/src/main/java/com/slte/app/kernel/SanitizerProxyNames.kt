package com.slte.app.kernel

internal object SanitizerProxyNames {

    private const val PROXIES_KEY = "proxies"
    private const val PROVIDERS_KEY = "proxy-providers"
    private const val GROUPS_KEY = "proxy-groups"
    private const val NAME_KEY = "name"
    private const val RENAME_SUFFIX_LIMIT = 99
    private const val RENAME_MARKER = " #"

    private val ITEM_NAME = Regex("^\\s*-\\s*name\\s*:")

    private val NAME_KEY_ANYWHERE = Regex("(?:^|[\\[{,])\\s*-?\\s*['\"]?name['\"]?\\s*:")

    private val FLOW_PROXIES = Regex("proxies\\s*:\\s*\\[")

    fun dedupe(lines: MutableList<String>): Int {
        val proxiesIndex = SanitizerYamlLines.topLevelBlockIndices(lines, PROXIES_KEY).firstOrNull() ?: return 0
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, proxiesIndex) ?: return 0
        val limit = SanitizerYamlLines.topLevelBlockEnd(lines, proxiesIndex)

        val entries = mutableListOf<Entry>()
        var index = proxiesIndex + 1
        while (index < limit && index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                index++
                continue
            }
            if (SanitizerYamlLines.leadingIndent(line) != itemIndent ||
                !SanitizerRules.LIST_ITEM.containsMatchIn(line.trimStart())
            ) {
                index++
                continue
            }

            val itemEnd = SanitizerYamlLines.blockEndIndex(lines, index, itemIndent).coerceAtMost(limit)
            val span = nameSpan(lines, index, itemEnd)
            if (span != null) {
                val raw = lines[span.line].substring(span.start, span.end)
                val name = SanitizerYamlLines.unwrapQuotes(raw.trim()).trim()
                if (name.isNotEmpty()) entries.add(Entry(name, span))
            }
            index = if (itemEnd > index) itemEnd else index + 1
        }

        val used = entries.mapTo(mutableSetOf()) { it.name }
        val seen = mutableSetOf<String>()
        val renamed = linkedMapOf<String, MutableList<String>>()
        for (entry in entries) {
            if (seen.add(entry.name)) continue
            val fresh = freshName(entry.name, used)
            used.add(fresh)
            val line = lines[entry.span.line]
            lines[entry.span.line] = line.replaceRange(entry.span.start, entry.span.end, SanitizerYamlLines.quote(fresh))
            renamed.getOrPut(entry.name) { mutableListOf() }.add(fresh)
        }

        val derived = derivedNames(entries.map { it.name }, renamed)
        if (derived.isEmpty()) return 0
        SanitizerGroupMembers.extendGroupMembers(lines, derived)
        return renamed.values.sumOf { it.size }
    }

    private fun derivedNames(
        proxyNames: List<String>,
        renamed: Map<String, List<String>>,
    ): Map<String, List<String>> {
        if (proxyNames.isEmpty()) return renamed
        val result = linkedMapOf<String, MutableList<String>>()
        renamed.forEach { (base, fresh) -> result.getOrPut(base) { mutableListOf() }.addAll(fresh) }
        for (name in proxyNames) {
            val marker = name.lastIndexOf(RENAME_MARKER)
            if (marker <= 0) continue
            if (name.substring(marker + RENAME_MARKER.length).toIntOrNull() == null) continue
            val base = name.substring(0, marker)
            val bucket = result.getOrPut(base) { mutableListOf() }
            if (name !in bucket) bucket.add(name)
        }
        return result
    }

    fun names(text: String): List<String> {
        val lines = SanitizerYamlLines.stripBom(text).lines()
        val proxiesIndex = SanitizerYamlLines.topLevelBlockIndices(lines, PROXIES_KEY).firstOrNull() ?: return emptyList()
        val end = SanitizerYamlLines.topLevelBlockEnd(lines, proxiesIndex)
        return lines.subList(proxiesIndex, end).flatMap(::nameValues)
    }

    fun duplicateNames(text: String): List<String> = names(text)
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .toList()

    fun hasProviders(text: String): Boolean = SanitizerYamlLines
        .topLevelBlockIndices(SanitizerYamlLines.stripBom(text).lines(), PROVIDERS_KEY)
        .isNotEmpty()

    fun itemsWithoutName(text: String): Int {
        val lines = SanitizerYamlLines.stripBom(text).lines()
        val proxiesIndex = SanitizerYamlLines.topLevelBlockIndices(lines, PROXIES_KEY).firstOrNull() ?: return 0
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, proxiesIndex) ?: return 0
        val limit = SanitizerYamlLines.topLevelBlockEnd(lines, proxiesIndex)

        var total = 0
        var missing = 0
        var index = proxiesIndex + 1
        while (index < limit && index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                index++
                continue
            }
            if (SanitizerYamlLines.leadingIndent(line) != itemIndent ||
                !SanitizerRules.LIST_ITEM.containsMatchIn(line.trimStart())
            ) {
                index++
                continue
            }
            val itemEnd = SanitizerYamlLines.blockEndIndex(lines, index, itemIndent).coerceAtMost(limit)
            total++
            if (nameSpan(lines, index, itemEnd) == null) missing++
            index = if (itemEnd > index) itemEnd else index + 1
        }
        return if (total == 0) 0 else missing
    }

    private data class ValueSpan(val line: Int, val start: Int, val end: Int)

    private data class Entry(val name: String, val span: ValueSpan)

    private fun nameSpan(
        lines: List<String>,
        start: Int,
        end: Int,
    ): ValueSpan? {
        val head = lines[start]
        if (head.contains('{')) return flowSpan(start, head)

        ITEM_NAME.find(head)?.let { return valueSpan(start, head, it.range.last + 1) }

        for (index in start + 1 until end) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val match = SanitizerRules.BLOCK_KEY.find(line) ?: continue
            if (match.groupValues[2] != NAME_KEY) continue
            return valueSpan(index, line, match.range.last + 1)
        }
        return null
    }

    private fun valueSpan(
        line: Int,
        text: String,
        from: Int,
    ): ValueSpan? {
        var index = from
        while (index < text.length && text[index] == ' ') index++
        if (index >= text.length) return null
        if (text[index] == '#') return null
        val start = index
        val end = valueEnd(text, start)
        if (end <= start) return null
        return ValueSpan(line, start, end)
    }

    /**
     * 值结束位置（不含行尾注释）。
     *
     * 引号值到配对引号为止（`name: "HK-01" # 备注` 的备注不属于值）；
     * 裸值到行尾注释为止——YAML 里 `#` 需要前面是空白才算注释，
     * 所以 `name: HK-01#x` 的 `#` 属于名字，只有 ` #` 才截断。
     *
     * 不剥离注释会让"同一名字 + 不同备注"的两个节点被当成不同名字：重名检查放行，
     * 内核却按重名拒绝加载，而且自愈路径用的是同一套判断，会一直修不回来。
     */
    private fun valueEnd(
        text: String,
        start: Int,
    ): Int {
        val first = text[start]
        if (first == '"' || first == '\'') {
            var index = start + 1
            while (index < text.length) {
                val char = text[index]
                // 双引号支持反斜杠转义，单引号里反斜杠是普通字符
                if (char == '\\' && first == '"') {
                    index += 2
                    continue
                }
                if (char == first) return index + 1
                index++
            }
            return text.length
        }
        var index = start
        var limit = text.length
        while (index < text.length) {
            if (text[index] == '#' && index > start && text[index - 1] == ' ') {
                limit = index
                break
            }
            index++
        }
        // 注释前的空白不算值的一部分：重命名时替换区间不能把分隔空格一起吃掉，
        // 否则会拼出 `"HK-01 # 2"# 备注` 这种把注释粘进引号的坏行
        var end = limit
        while (end > start && text[end - 1] == ' ') end--
        return end
    }

    private fun flowSpan(
        line: Int,
        text: String,
    ): ValueSpan? {
        val match = NAME_KEY_ANYWHERE.find(text) ?: return null
        var index = match.range.last + 1
        while (index < text.length && text[index] == ' ') index++
        val start = index
        var quote: Char? = null
        while (index < text.length) {
            val char = text[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else {
                if (char == '"' || char == '\'') quote = char
                if (char == ',' || char == '}') break
            }
            index++
        }
        if (index <= start) return null
        return ValueSpan(line, start, index)
    }

    private fun nameValues(line: String): List<String> {
        if (line.isBlank() || line.trimStart().startsWith("#")) return emptyList()
        val values = mutableListOf<String>()
        var from = 0
        while (from < line.length) {
            val match = NAME_KEY_ANYWHERE.find(line, from) ?: break
            var index = match.range.last + 1
            while (index < line.length && line[index] == ' ') index++
            val start = index
            if (line.contains('{') || line.contains('[')) {
                var quote: Char? = null
                while (index < line.length) {
                    val char = line[index]
                    if (quote != null) {
                        if (char == quote) quote = null
                    } else {
                        if (char == '"' || char == '\'') quote = char
                        if (char == ',' || char == '}') break
                    }
                    index++
                }
            } else {
                index = valueEnd(line, start)
            }
            val name = SanitizerYamlLines.unwrapQuotes(line.substring(start, index).trim()).trim()
            if (name.isNotEmpty()) values.add(name)
            from = if (index > match.range.last) index else match.range.last + 1
        }
        return values
    }

    private fun freshName(
        name: String,
        used: Set<String>,
    ): String {
        var suffix = 2
        while (suffix <= RENAME_SUFFIX_LIMIT) {
            val candidate = "$name$RENAME_MARKER$suffix"
            if (candidate !in used) return candidate
            suffix++
        }
        var fallback = RENAME_SUFFIX_LIMIT + 1
        while (true) {
            val candidate = "$name$RENAME_MARKER$fallback"
            if (candidate !in used) return candidate
            fallback++
        }
    }
}
