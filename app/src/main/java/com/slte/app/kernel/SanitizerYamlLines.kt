// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

internal object SanitizerYamlLines {

    fun stripBom(text: String): String {
        var start = 0
        while (start < text.length && text[start] == SanitizerRules.BOM) start++
        return if (start == 0) text else text.substring(start)
    }

    fun dedentRootKeys(lines: MutableList<String>) {
        val rootIndent = lines.firstNotNullOfOrNull { rootIndentCandidate(it) } ?: return
        if (rootIndent <= 0) return
        for (i in lines.indices) {
            val line = lines[i]
            if (line.length < rootIndent) continue
            var shiftable = true
            for (j in 0 until rootIndent) {
                if (line[j] != ' ') {
                    shiftable = false
                    break
                }
            }
            if (shiftable) lines[i] = line.substring(rootIndent)
        }
    }

    private fun rootIndentCandidate(line: String): Int? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("#") || trimmed.startsWith("%")) return null
        if (trimmed == "---" || trimmed == "...") return null
        var count = 0
        while (count < line.length && line[count] == ' ') count++
        if (count < line.length && line[count] == '\t') return null
        return count
    }

    private fun leadingIndentLength(line: String): Int {
        var count = 0
        while (count < line.length && (line[count] == ' ' || line[count] == '\t')) count++
        return count
    }

    fun leadingIndent(line: String): String = line.substring(0, leadingIndentLength(line))

    fun tabIndentLength(line: String): Int {
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char == ' ') return -1
            if (char != '\t') return index
            index++
        }
        return index
    }

    fun isTopLevelLine(line: String): Boolean = tabIndentLength(line) >= 0

    fun normalizeQuotedKeys(lines: MutableList<String>) {
        for (i in lines.indices) {
            val line = lines[i]
            val colon = unquotedColonIndex(line)
            if (colon <= 0) continue
            val rawKey = line.substring(0, colon).trim()
            val key = unwrapQuotes(rawKey).lowercase()
            // 引号键与大小写变体都要扁平化：前者是 YAML 合法写法，后者则是清洗层的绕过面
            // （下游全是精确串比较，见 normalizeKey 的说明）。
            val rewritten = leadingIndent(line) + key + line.substring(colon)
            if (rewritten != line && SanitizerRules.PLAIN_KEY.matches(key)) lines[i] = rewritten
        }
    }

    fun unwrapQuotes(raw: String): String {
        if (raw.length < 2) return raw
        val quote = raw.first()
        if (quote != '"' && quote != '\'') return raw
        return if (raw.last() == quote) raw.substring(1, raw.length - 1) else raw
    }

    fun unquotedColonIndex(line: String): Int {
        var index = 0
        while (index < line.length && (line[index] == ' ' || line[index] == '\t')) index++
        var quote: Char? = null
        while (index < line.length) {
            val char = line[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else {
                when {
                    char == ':' -> return index
                    char == '"' || char == '\'' -> quote = char
                }
            }
            index++
        }
        return -1
    }

    fun topLevelKey(line: String): String? {
        if (!isTopLevelLine(line)) return null
        val colon = unquotedColonIndex(line)
        if (colon <= 0) return null
        return normalizeKey(line.substring(0, colon)).ifEmpty { null }
    }

    /**
     * 键名归一化：解引号 → 去空白 → **转小写**。
     *
     * 转小写是安全关键，不是风格问题。YAML 规范里映射键是大小写敏感的，但 mihomo/clash
     * 对配置项的解析是大小写不敏感的，`Mixed-Port: 7890` 与 `mixed-port: 7890` 同样生效。
     * 而下游所有判定用的都是精确串比较（[SanitizerNeutralizer] 里的 `key in SET`、`key == "allow-lan"`
     * 等），一旦大小写不归一，`GeOx-Url`、`SECRET`、`Mixed-Port` 这类写法就会整条穿过清洗层，
     * 连兜底校验器 [SanitizerNeutralizer.hasUnsafeResidue] 也拦不住（它复用同一个归一化结果）。
     * 该缺陷属于 SSRF/控制面暴露风险，修复见 2026-09-29 geox-url 专项。
     */
    private fun normalizeKey(raw: String): String = unwrapQuotes(raw.trim()).trim().lowercase()

    fun topLevelBlockIndices(
        lines: List<String>,
        key: String,
    ): MutableList<Int> {
        val indices = mutableListOf<Int>()
        for (i in lines.indices) {
            val line = lines[i]
            if (topLevelKey(line) == key && SanitizerRules.TOP_LEVEL_BLOCK_HEAD.matches(line)) indices.add(i)
        }
        return indices
    }

    fun blockEndIndex(
        lines: List<String>,
        start: Int,
        itemIndent: String,
    ): Int {
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (leadingIndent(line).length <= itemIndent.length) return i
        }
        return lines.size
    }

    fun topLevelBlockEnd(
        lines: List<String>,
        index: Int,
    ): Int {
        for (i in index + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (leadingIndentLength(line) != 0) continue
            if (SanitizerRules.LIST_ITEM.containsMatchIn(line)) continue
            return i
        }
        return lines.size
    }

    fun listBlockEnd(
        lines: List<String>,
        keyIndex: Int,
        itemIndent: String,
    ): Int {
        var end = keyIndex + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                end++
                continue
            }
            if (!SanitizerRules.LIST_ITEM.containsMatchIn(line.trimStart()) || leadingIndent(line) != itemIndent) break
            end++
        }
        return end
    }

    fun blockKeyValue(
        lines: List<String>,
        start: Int,
        end: Int,
        keyIndent: String,
        key: String,
    ): KeyState {
        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = leadingIndent(line)
            if (indent.length < keyIndent.length) return KeyState.MISSING
            if (indent != keyIndent) continue
            val m = SanitizerRules.BLOCK_KEY.find(line) ?: continue
            if (m.groupValues[2] != key) continue
            val value = line.substringAfter(':').trim()
            return if (value.isEmpty() || value == "\"\"" || value == "''") KeyState.EMPTY else KeyState.PRESENT
        }
        return KeyState.MISSING
    }

    fun blockKeyLineIndex(
        lines: List<String>,
        start: Int,
        end: Int,
        keyIndent: String,
        key: String,
    ): Int? {
        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = leadingIndent(line)
            if (indent.length < keyIndent.length) return null
            if (indent != keyIndent) continue
            val m = SanitizerRules.BLOCK_KEY.find(line) ?: continue
            if (m.groupValues[2] == key) return i
        }
        return null
    }

    fun childKeyIndent(
        lines: List<String>,
        start: Int,
        end: Int,
        parentIndent: String,
    ): String? {
        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = leadingIndent(line)
            if (indent.length <= parentIndent.length) return null
            return indent
        }
        return null
    }

    fun blockItemIndent(
        lines: List<String>,
        keyIndex: Int,
    ): String? {
        for (i in keyIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val trimmed = line.trimStart()
            if (trimmed.startsWith("-")) return line.substring(0, line.length - trimmed.length)
            return null
        }
        return null
    }

    fun blockKeyIndent(
        lines: List<String>,
        keyIndex: Int,
    ): String? {
        for (i in keyIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            return leadingIndent(line)
        }
        return null
    }
}
