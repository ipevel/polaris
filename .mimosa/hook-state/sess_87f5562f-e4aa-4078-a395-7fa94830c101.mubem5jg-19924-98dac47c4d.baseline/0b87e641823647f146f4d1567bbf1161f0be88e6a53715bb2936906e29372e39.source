package com.slte.app.kernel

internal object SanitizerNeutralizer {

    fun zeroTopLevelPorts(lines: MutableList<String>) {
        for (i in lines.indices) {
            val line = lines[i]
            val indent = SanitizerYamlLines.tabIndentLength(line)
            if (indent < 0) continue
            val body = if (indent == 0) line else line.substring(indent)
            val m = SanitizerRules.TOP_LEVEL_KEY_VALUE.matchEntire(body) ?: continue
            val key = m.groupValues[1]
            lines[i] =
                when (key) {
                    "allow-lan" -> "allow-lan: false"
                    "bind-address" -> "bind-address: \"\""
                    in SanitizerRules.ZEROED_PORT_KEYS -> "$key: 0"
                    else -> line
                }
        }
    }

    fun clearSubtitlePattern(lines: MutableList<String>) {
        for (i in lines.indices) {
            val m = SanitizerRules.SUBTITLE_PATTERN_LINE.matchEntire(lines[i]) ?: continue
            lines[i] = m.groupValues[1] + "\"\""
        }
    }

    fun neutralizeControlSurface(lines: MutableList<String>) {
        dropTopLevelKeys(lines, SanitizerRules.DROPPED_TOP_LEVEL_KEYS)
        forceOffTopLevelSwitches(lines, SanitizerRules.FORCED_OFF_TOP_LEVEL_KEYS)
        for (i in lines.indices) {
            val key = SanitizerYamlLines.topLevelKey(lines[i]) ?: continue
            if (key in SanitizerRules.NEUTRALIZED_SCALAR_KEYS) lines[i] = "$key: \"\""
        }
        clearAuthentication(lines)
    }

    fun hasUnsafeResidue(lines: List<String>): Boolean {
        for (i in lines.indices) {
            val line = lines[i]
            if (!SanitizerYamlLines.isTopLevelLine(line)) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed == "---" || trimmed == "...") continue
            if (trimmed == "?" || trimmed.startsWith("? ")) return true
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return true
            val colon = SanitizerYamlLines.unquotedColonIndex(line)
            if (SanitizerRules.KEY_SMUGGLING.containsMatchIn(line.substring(0, maxOf(colon, 0)))) return true
            val key = SanitizerYamlLines.topLevelKey(line) ?: continue
            val value = line.substring(colon + 1).trim()
            if (key in SanitizerRules.DROPPED_TOP_LEVEL_KEYS) return true
            if (key in SanitizerRules.NEUTRALIZED_SCALAR_KEYS && value.isNotEmpty() && value != "\"\"") return true
            if (key in SanitizerRules.ZEROED_PORT_KEYS && value != "0") return true
            if (key == "allow-lan" && value != "false") return true
            if (key == "bind-address" && value.isNotEmpty() && value != "\"\"") return true
            if (key == "authentication" && value.isNotEmpty() && value != "[]") return true
            if (key in SanitizerRules.FORCED_OFF_TOP_LEVEL_KEYS && !switchDisabled(lines, i)) return true
        }
        return false
    }

    private fun switchDisabled(
        lines: List<String>,
        index: Int,
    ): Boolean {
        val line = lines[index]
        if (SanitizerRules.TOP_LEVEL_FLOW_HEAD.containsMatchIn(line)) return line.substringAfter(':').contains("enable: false")
        if (!SanitizerRules.TOP_LEVEL_BLOCK_HEAD.matches(line)) return false
        val end = SanitizerYamlLines.topLevelBlockEnd(lines, index)
        val childIndent = SanitizerYamlLines.childKeyIndent(lines, index, end, "") ?: return false
        return lines.subList(index + 1, end).none { child ->
            SanitizerYamlLines.leadingIndent(child) == childIndent &&
                SanitizerRules.ENABLE_KEY.containsMatchIn(child.trimStart()) &&
                child.substringAfter(':').trim() != "false"
        }
    }

    private fun dropTopLevelKeys(
        lines: MutableList<String>,
        keys: Set<String>,
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val key = SanitizerYamlLines.topLevelKey(line)
            if (key == null || key !in keys) {
                i++
                continue
            }
            val end = if (SanitizerRules.TOP_LEVEL_BLOCK_HEAD.matches(line)) SanitizerYamlLines.topLevelBlockEnd(lines, i) else i + 1
            lines.subList(i, end).clear()
        }
    }

    private fun forceOffTopLevelSwitches(
        lines: MutableList<String>,
        keys: Set<String>,
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val key = SanitizerYamlLines.topLevelKey(line)
            if (key == null || key !in keys) {
                i++
                continue
            }

            if (SanitizerRules.TOP_LEVEL_FLOW_HEAD.containsMatchIn(line) || !SanitizerRules.TOP_LEVEL_BLOCK_HEAD.matches(line)) {
                lines[i] = "$key: {enable: false}"
                i++
                continue
            }
            val end = SanitizerYamlLines.topLevelBlockEnd(lines, i)
            val childIndent = SanitizerYamlLines.childKeyIndent(lines, i, end, "")
            if (childIndent == null) {
                lines.add(i + 1, "  enable: false")
                i++
                continue
            }
            var rewritten = false
            for (j in i + 1 until end) {
                val child = lines[j]
                if (SanitizerYamlLines.leadingIndent(child) != childIndent) continue
                if (!SanitizerRules.ENABLE_KEY.containsMatchIn(child.trimStart())) continue
                lines[j] = "${childIndent}enable: false"
                rewritten = true
            }
            if (!rewritten) lines.add(i + 1, "${childIndent}enable: false")
            i++
        }
    }

    private fun clearAuthentication(lines: MutableList<String>) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (SanitizerYamlLines.topLevelKey(line) != "authentication") {
                i++
                continue
            }
            val value = line.substring(SanitizerYamlLines.unquotedColonIndex(line) + 1).trim()
            if (line.contains('[') || value.isNotEmpty()) {
                lines[i] = "authentication: []"
                i++
                continue
            }
            lines.subList(i, SanitizerYamlLines.topLevelBlockEnd(lines, i)).clear()
        }
    }
}
