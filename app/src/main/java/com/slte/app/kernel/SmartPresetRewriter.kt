package com.slte.app.kernel

/** 配置改写：追加自动测速组、把主选择器默认项指向它。块式与流式写法都覆盖。 */
internal object SmartPresetRewriter {

    private const val PROXIES_KEY = "proxies"
    private const val TEST_URL = "http://www.gstatic.com/generate_204"

    /** 追加到 proxy-groups 块末尾；位置在主选择器之后，不影响其块内索引。 */
    fun insertAutoGroup(
        lines: MutableList<String>,
        groupsIndex: Int,
        itemIndent: String,
        autoName: String,
    ) {
        val end = SanitizerYamlLines.topLevelBlockEnd(lines, groupsIndex)
        val keyIndent = itemIndent + "  "
        lines.addAll(
            end.coerceAtMost(lines.size),
            listOf(
                itemIndent + "- name: " + quote(autoName),
                // 智能内核原生 smart 分组：按延迟/丢包/ASN 排序，可选 LightGBM 预测
                keyIndent + "type: smart",
                keyIndent + "include-all: true",
                keyIndent + "url: " + TEST_URL,
                keyIndent + "interval: 120",
                keyIndent + "uselightgbm: true",
                keyIndent + "collectdata: true",
                keyIndent + "prefer-asn: true",
            ),
        )
    }

    fun setSelectorDefault(
        lines: MutableList<String>,
        selector: SmartPresetParser.Group,
        autoName: String,
    ) {
        val head = lines[selector.headIndex]
        if (head.contains('{')) {
            flowListSetDefault(head, autoName)?.let { lines[selector.headIndex] = it }
            return
        }
        val itemIndent = SanitizerYamlLines.leadingIndent(head)
        val keyIndent = SanitizerYamlLines.childKeyIndent(lines, selector.headIndex, selector.endIndex, itemIndent) ?: return
        val proxiesLine =
            SanitizerYamlLines.blockKeyLineIndex(lines, selector.headIndex, selector.endIndex, keyIndent, PROXIES_KEY)
        if (proxiesLine == null) {
            // 只有 use: 的分组：补一个 proxies 键引用自动组（mihomo 允许 proxies 与 use 并存）
            val useLine =
                SanitizerYamlLines.blockKeyLineIndex(lines, selector.headIndex, selector.endIndex, keyIndent, "use")
                    ?: return
            lines.add(useLine, keyIndent + PROXIES_KEY + ":")
            lines.add(useLine + 1, keyIndent + "  - " + quote(autoName))
            return
        }
        if (lines[proxiesLine].substringAfter(':').trim().isNotEmpty()) {
            flowListSetDefault(lines[proxiesLine], autoName)?.let { lines[proxiesLine] = it }
            return
        }
        blockSetDefault(lines, proxiesLine, selector.endIndex, keyIndent, autoName)
    }

    private fun blockSetDefault(
        lines: MutableList<String>,
        proxiesLine: Int,
        endIndex: Int,
        keyIndent: String,
        autoName: String,
    ) {
        data class Member(
            val index: Int,
            val name: String,
        )

        val members = mutableListOf<Member>()
        var memberIndent: String? = null
        for (i in proxiesLine + 1 until endIndex.coerceAtMost(lines.size)) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length <= keyIndent.length) break
            val trimmed = line.trimStart()
            if (!SanitizerRules.LIST_ITEM.containsMatchIn(trimmed)) continue
            if (memberIndent == null) memberIndent = indent
            members.add(Member(i, SmartPresetParser.listItemName(trimmed)))
        }

        if (members.firstOrNull()?.name == autoName) return
        var insertAt = members.firstOrNull()?.index ?: (proxiesLine + 1)
        val indent = memberIndent ?: keyIndent + "  "
        // 从后往前删同名成员并同步补偿插入点，避免索引位移
        for (member in members.asReversed()) {
            if (member.name == autoName) {
                lines.removeAt(member.index)
                if (member.index < insertAt) insertAt--
            }
        }
        lines.add(insertAt.coerceAtMost(lines.size), indent + "- " + quote(autoName))
    }

    /** `proxies: [a, b]` 内联列表：自动组移到首位。已在其位时返回 null 表示无需改写。 */
    private fun flowListSetDefault(
        line: String,
        autoName: String,
    ): String? {
        val head = Regex("proxies\\s*:\\s*\\[").find(line) ?: return null
        val open = head.range.last
        val close = flowListEnd(line, open)
        if (close < 0) return null
        val tokens = splitFlowList(line.substring(open + 1, close)).map { it.trim() }
        if (tokens.isNotEmpty() && SanitizerYamlLines.unwrapQuotes(tokens.first()).trim() == autoName) return null
        val kept = tokens.filter { SanitizerYamlLines.unwrapQuotes(it).trim() != autoName }
        val merged = (listOf(quote(autoName)) + kept).joinToString(", ")
        return line.substring(0, open + 1) + merged + line.substring(close)
    }

    private fun flowListEnd(
        line: String,
        open: Int,
    ): Int {
        var index = open + 1
        var quote: Char? = null
        var depth = 0
        while (index < line.length) {
            val char = line[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else {
                when (char) {
                    '"', '\'' -> quote = char
                    '[' -> depth++
                    ']' -> if (depth == 0) return index else depth--
                }
            }
            index++
        }
        return -1
    }

    private fun splitFlowList(inner: String): List<String> {
        val tokens = mutableListOf<String>()
        val buffer = StringBuilder()
        var quote: Char? = null
        for (char in inner) {
            if (quote != null) {
                buffer.append(char)
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '"', '\'' -> {
                    quote = char
                    buffer.append(char)
                }
                ',' -> {
                    if (buffer.isNotBlank()) tokens.add(buffer.toString())
                    buffer.clear()
                }
                else -> buffer.append(char)
            }
        }
        if (buffer.isNotBlank()) tokens.add(buffer.toString())
        return tokens
    }

    private fun quote(name: String): String = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
