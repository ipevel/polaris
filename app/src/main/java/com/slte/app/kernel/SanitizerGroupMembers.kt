package com.slte.app.kernel

/** 重命名后的节点同步补进 proxy-groups 成员列表（块式与流式）。 */
internal object SanitizerGroupMembers {
    private const val GROUPS_KEY = "proxy-groups"
    private const val PROXIES_KEY = "proxies"

    private val FLOW_PROXIES = Regex("proxies\\s*:\\s*\\[")

    fun extendGroupMembers(
        lines: MutableList<String>,
        renamed: Map<String, List<String>>,
    ) {
        val groupsIndex = SanitizerYamlLines.topLevelBlockIndices(lines, GROUPS_KEY).firstOrNull() ?: return
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, groupsIndex) ?: return
        val keyIndent = itemIndent + "  "
        val pending = mutableListOf<Pair<Int, List<String>>>()

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
            if (line.contains('{')) {
                extendFlowGroup(lines, index, renamed)?.let { lines[index] = it }
                index++
                continue
            }
            if (!SanitizerRules.GROUP_ITEM_START.containsMatchIn(line)) {
                index++
                continue
            }

            val itemEnd = SanitizerYamlLines.blockEndIndex(lines, index, itemIndent)
            val listKeyIndex = SanitizerYamlLines.blockKeyLineIndex(lines, index, itemEnd, keyIndent, PROXIES_KEY)
            if (listKeyIndex != null && lines[listKeyIndex].substringAfter(':').trim().isEmpty()) {
                pending.addAll(collectMembers(lines, listKeyIndex + 1, itemEnd, keyIndent, renamed))
            }
            index = if (itemEnd > index) itemEnd else index + 1
        }

        for ((at, added) in pending.sortedByDescending { it.first }) {
            lines.addAll(at + 1, added)
        }
    }

    private fun extendFlowGroup(
        lines: List<String>,
        index: Int,
        renamed: Map<String, List<String>>,
    ): String? {
        val line = lines[index]
        val head = FLOW_PROXIES.find(line) ?: return null
        val open = head.range.last
        val close = flowListEnd(line, open)
        if (close < 0) return null

        val tokens = splitFlowList(line.substring(open + 1, close))
        if (tokens.isEmpty()) return null

        val existing = tokens.mapTo(mutableSetOf()) { SanitizerYamlLines.unwrapQuotes(it.trim()).trim() }
        val additions = mutableListOf<String>()
        for (token in tokens) {
            val name = SanitizerYamlLines.unwrapQuotes(token.trim()).trim()
            renamed[name]?.forEach { fresh -> if (existing.add(fresh)) additions.add(SanitizerYamlLines.quote(fresh)) }
        }
        if (additions.isEmpty()) return null

        val merged = (tokens.map { it.trim() } + additions).joinToString(", ")
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

    private fun collectMembers(
        lines: List<String>,
        from: Int,
        to: Int,
        keyIndent: String,
        renamed: Map<String, List<String>>,
    ): List<Pair<Int, List<String>>> {
        val (existing, entries) = groupMembers(lines, from, to, keyIndent)
        val pending = mutableListOf<Pair<Int, List<String>>>()
        for ((index, name) in entries) {
            val fresh = renamed[name] ?: continue
            val additions = fresh.filter { existing.add(it) }
            if (additions.isEmpty()) continue
            val indent = SanitizerYamlLines.leadingIndent(lines[index])
            pending.add(index to additions.map { indent + "- " + SanitizerYamlLines.quote(it) })
        }
        return pending
    }

    private fun groupMembers(
        lines: List<String>,
        from: Int,
        to: Int,
        keyIndent: String,
    ): Pair<MutableSet<String>, List<Pair<Int, String>>> {
        val existing = mutableSetOf<String>()
        val entries = mutableListOf<Pair<Int, String>>()
        for (index in from until minOf(to, lines.size)) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length <= keyIndent.length) break
            val trimmed = line.trimStart()
            if (!SanitizerRules.LIST_ITEM.containsMatchIn(trimmed)) continue
            val name = SanitizerYamlLines.unwrapQuotes(commentFree(trimmed.removePrefix("-"))).trim()
            if (name.isEmpty()) continue
            existing.add(name)
            entries.add(index to name)
        }
        return existing to entries
    }

    /**
     * 去掉行尾注释后的裸值：`jp-01 # 备注` → `jp-01`，`"jp-01" # 备注` → `jp-01`。
     *
     * 分组引用与节点名必须用同一套解析口径：名字里混进注释会导致重命名后的节点
     * 匹配不上分组里的引用，新名字不会被补进分组。
     */
    private fun commentFree(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed[0] == '#') return ""
        return SanitizerYamlLines.unwrapQuotes(trimmed.substring(0, SanitizerYamlLines.valueEnd(trimmed, 0)).trim())
    }
}
