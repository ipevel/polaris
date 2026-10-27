package com.slte.app.kernel

/** proxy-groups 的行级解析：定位分组名/类型及其在文本中的行区间。 */
internal object SmartPresetParser {

    private const val NAME_KEY = "name"
    private const val TYPE_KEY = "type"

    private val ITEM_NAME = Regex("^\\s*-\\s*name\\s*:")
    private val FLOW_KEY = Regex("(?:^|[\\[{,])\\s*-?\\s*['\"]?(name|type)['\"]?\\s*:")

    data class Group(
        val name: String,
        val type: String,
        val headIndex: Int,
        val endIndex: Int,
    )

    fun parseGroups(
        lines: List<String>,
        groupsIndex: Int,
        itemIndent: String,
    ): List<Group> {
        val groups = mutableListOf<Group>()
        var index = groupsIndex + 1
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                index++
                continue
            }
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length < itemIndent.length) break
            if (indent != itemIndent || !SanitizerRules.LIST_ITEM.containsMatchIn(line.trimStart())) {
                index++
                continue
            }
            val itemEnd = SanitizerYamlLines.blockEndIndex(lines, index, itemIndent)
            parseGroup(lines, index, itemEnd)?.let { groups.add(it) }
            index = if (itemEnd > index) itemEnd else index + 1
        }
        return groups
    }

    private fun parseGroup(
        lines: List<String>,
        headIndex: Int,
        endIndex: Int,
    ): Group? {
        val head = lines[headIndex]
        return if (head.contains('{')) {
            Group(
                name = flowValue(head, NAME_KEY) ?: return null,
                type = flowValue(head, TYPE_KEY) ?: return null,
                headIndex = headIndex,
                endIndex = endIndex,
            )
        } else {
            Group(
                name = blockValue(lines, headIndex, endIndex, NAME_KEY) ?: return null,
                type = blockValue(lines, headIndex, endIndex, TYPE_KEY) ?: return null,
                headIndex = headIndex,
                endIndex = endIndex,
            )
        }
    }

    private fun blockValue(
        lines: List<String>,
        headIndex: Int,
        endIndex: Int,
        key: String,
    ): String? {
        val from = if (key == NAME_KEY) headIndex else headIndex + 1
        for (i in from until endIndex.coerceAtMost(lines.size)) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            ITEM_NAME.find(line)?.let { if (key == NAME_KEY) return scalarValue(line.substring(it.range.last + 1)) }
            val match = SanitizerRules.BLOCK_KEY.find(line) ?: continue
            if (match.groupValues[2] == key) return scalarValue(line.substring(match.range.last + 1))
        }
        return null
    }

    private fun flowValue(
        text: String,
        key: String,
    ): String? {
        var from = 0
        while (true) {
            val match = FLOW_KEY.find(text, from) ?: return null
            if (match.groupValues[1] == key) return flowValueFrom(text, match.range.last + 1)
            from = match.range.last + 1
        }
    }

    private fun flowValueFrom(
        text: String,
        start: Int,
    ): String? {
        var index = start
        while (index < text.length && text[index] == ' ') index++
        if (index >= text.length) return null
        val begin = index
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
        return SanitizerYamlLines.unwrapQuotes(text.substring(begin, index).trim()).trim().ifEmpty { null }
    }

    internal fun listItemName(trimmedItem: String): String {
        val raw = trimmedItem.removePrefix("-").trim()
        if (raw.isEmpty() || raw.startsWith("#")) return ""
        return SanitizerYamlLines.unwrapQuotes(raw.substring(0, scalarEnd(raw, 0)).trim()).trim()
    }

    internal fun scalarValue(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        return SanitizerYamlLines.unwrapQuotes(trimmed.substring(0, scalarEnd(trimmed, 0)).trim()).trim().ifEmpty { null }
    }

    /**
     * 值结束位置（不含行尾注释）：引号值到配对引号为止；裸值到 ` #` 为止——
     * YAML 里 `#` 前有空白才算注释，与 SanitizerProxyNames 同一口径。
     */
    private fun scalarEnd(
        text: String,
        start: Int,
    ): Int {
        val first = text[start]
        if (first == '"' || first == '\'') {
            var index = start + 1
            while (index < text.length) {
                val char = text[index]
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
        while (index < text.length) {
            if (text[index] == '#' && index > start && text[index - 1] == ' ') return index
            index++
        }
        return text.length
    }
}
