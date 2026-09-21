package com.slte.app.kernel

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

internal object SanitizerInjector {

    fun directDomains(domains: List<String>): List<String> {
        val accepted = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        domains.forEach { domain ->
            if (domain.isBlank()) return@forEach
            if (!isValidDirectDomain(domain)) {
                AppLog.w(SanitizerRules.LOG_TAG, "直连域名非法，已忽略: ${sanitizeLog(domain)}")
                return@forEach
            }
            if (!seen.add(domain.lowercase())) return@forEach
            accepted.add(domain)
        }
        return accepted
    }

    private fun isValidDirectDomain(domain: String): Boolean {
        if (domain.isEmpty() || domain != domain.trim() || domain.length > SanitizerRules.MAX_DOMAIN_LENGTH) return false
        val labels = domain.split('.')
        if (labels.any { it.isEmpty() || it.length > SanitizerRules.MAX_DOMAIN_LABEL_LENGTH }) return false
        return labels.all { label ->
            label.all(::isDomainChar) && isDomainEdgeChar(label.first()) && isDomainEdgeChar(label.last())
        }
    }

    private fun isDomainChar(char: Char): Boolean = isDomainEdgeChar(char) || char == '-' || char == '_'

    private fun isDomainEdgeChar(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'

    fun injectHealthCheckConfig(lines: MutableList<String>) {
        injectGroupHealthCheck(lines)
        injectProviderHealthCheck(lines)
    }

    private fun injectGroupHealthCheck(lines: MutableList<String>) {
        val groupsIndex = SanitizerYamlLines.topLevelBlockIndices(lines, "proxy-groups").firstOrNull() ?: return
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, groupsIndex) ?: return
        val keyIndent = itemIndent + "  "

        val pending = mutableListOf<Pair<Int, List<String>>>()
        var i = groupsIndex + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                i++
                continue
            }
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length < itemIndent.length) break
            if (indent != itemIndent || !SanitizerRules.GROUP_ITEM_START.containsMatchIn(line)) {
                i++
                continue
            }

            if (line.contains('{') || line.contains('[')) {
                i++
                continue
            }
            val blockEnd = SanitizerYamlLines.blockEndIndex(lines, i, itemIndent)
            if (groupType(lines, i, blockEnd, keyIndent) !in SanitizerRules.HEALTH_CHECK_GROUP_TYPES) {
                i = blockEnd
                continue
            }
            val additions = mutableListOf<String>()
            val replacements = mutableListOf<Pair<Int, String>>()
            for ((key, value) in listOf("url" to SanitizerRules.HEALTH_CHECK_URL, "timeout" to SanitizerRules.HEALTH_CHECK_TIMEOUT_MS.toString())) {
                when (SanitizerYamlLines.blockKeyValue(lines, i, blockEnd, keyIndent, key)) {
                    KeyState.MISSING -> additions.add(keyIndent + "$key: $value")
                    KeyState.EMPTY ->
                        SanitizerYamlLines.blockKeyLineIndex(lines, i, blockEnd, keyIndent, key)
                            ?.let { replacements.add(it to keyIndent + "$key: $value") }
                    KeyState.PRESENT -> Unit
                }
            }
            if (additions.isNotEmpty()) pending.add(i + 1 to additions)

            for ((at, text) in replacements.asReversed()) {
                lines[at] = text
            }
            i = blockEnd
        }
        for ((at, additions) in pending.asReversed()) {
            lines.addAll(at, additions)
        }
    }

    private fun injectProviderHealthCheck(lines: MutableList<String>) {
        val providersIndex = SanitizerYamlLines.topLevelBlockIndices(lines, "proxy-providers").firstOrNull() ?: return
        val providerIndent = SanitizerYamlLines.blockKeyIndent(lines, providersIndex) ?: return
        val pending = mutableListOf<Pair<Int, List<String>>>()
        var i = providersIndex + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                i++
                continue
            }
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length < providerIndent.length) break
            if (indent != providerIndent || !SanitizerRules.PROVIDER_KEY.matches(line.trim())) {
                i++
                continue
            }

            if (line.contains('{') || line.contains('[')) {
                i++
                continue
            }
            val blockEnd = SanitizerYamlLines.blockEndIndex(lines, i, providerIndent)
            val childKeyIndent = SanitizerYamlLines.childKeyIndent(lines, i, blockEnd, providerIndent)
            if (childKeyIndent == null) {
                i = blockEnd
                continue
            }
            if (hasBlockKey(lines, i, blockEnd, childKeyIndent, "health-check")) {
                i = blockEnd
                continue
            }

            if (lines.subList(i, blockEnd).any {
                    it.contains("health-check:") && it.trim() != "health-check:"
                }
            ) {
                i = blockEnd
                continue
            }
            pending.add(
                i + 1 to
                    listOf(
                        childKeyIndent + "health-check:",
                        childKeyIndent + "  enable: true",
                        childKeyIndent + "  url: ${SanitizerRules.HEALTH_CHECK_URL}",
                        childKeyIndent + "  interval: 300",
                        childKeyIndent + "  timeout: ${SanitizerRules.HEALTH_CHECK_TIMEOUT_MS}",
                        childKeyIndent + "  lazy: true",
                    ),
            )
            i = blockEnd
        }
        for ((at, additions) in pending.asReversed()) {
            lines.addAll(at, additions)
        }
    }

    private fun groupType(
        lines: List<String>,
        start: Int,
        end: Int,
        keyIndent: String,
    ): String {
        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = SanitizerYamlLines.leadingIndent(line)
            if (indent.length < keyIndent.length) return ""
            if (indent != keyIndent) continue
            val m = SanitizerRules.BLOCK_KEY.find(line) ?: continue
            if (m.groupValues[2] != "type") continue
            return line
                .substringAfter(':')
                .trim()
                .trim('\'')
                .trim('"')
                .lowercase()
        }
        return ""
    }

    private fun hasBlockKey(
        lines: List<String>,
        start: Int,
        end: Int,
        keyIndent: String,
        key: String,
    ): Boolean = SanitizerYamlLines.blockKeyValue(lines, start, end, keyIndent, key) != KeyState.MISSING

    fun injectDirectRule(
        lines: MutableList<String>,
        domain: String,
    ) {
        val rule = "DOMAIN-SUFFIX,$domain,DIRECT"
        SanitizerYamlLines.topLevelBlockIndices(lines, SanitizerRules.RULES_KEY).asReversed().forEach { index ->
            val indent = SanitizerYamlLines.blockItemIndent(lines, index) ?: return@forEach
            val end = SanitizerYamlLines.listBlockEnd(lines, index, indent)
            if (hasListItem(lines, index + 1, end, rule)) return@forEach
            lines.add(index + 1, indent + "- '$rule'")
        }
    }

    fun injectFakeIpFilter(
        lines: MutableList<String>,
        domain: String,
    ) {
        val entry = "+.$domain"
        val filterIndex = lines.indexOfFirst { SanitizerRules.FAKE_IP_FILTER_LINE.matches(it.trim()) }
        if (filterIndex >= 0) {
            val keyIndent = SanitizerYamlLines.leadingIndent(lines[filterIndex])
            val indent = SanitizerYamlLines.blockItemIndent(lines, filterIndex)
            if (indent == null) {
                lines.add(filterIndex + 1, keyIndent + "  - '$entry'")
                return
            }
            val end = SanitizerYamlLines.listBlockEnd(lines, filterIndex, indent)
            if (hasListItem(lines, filterIndex + 1, end, entry)) return
            lines.add(filterIndex + 1, indent + "- '$entry'")
            return
        }

        if (lines.any { SanitizerRules.INLINE_FAKE_IP_FILTER.containsMatchIn(it.trim()) }) return

        val dnsIndex = SanitizerYamlLines.topLevelBlockIndices(lines, SanitizerRules.DNS_KEY).lastOrNull() ?: return

        val keyIndent = SanitizerYamlLines.blockKeyIndent(lines, dnsIndex) ?: return
        lines.add(dnsIndex + 1, keyIndent + "fake-ip-filter:")
        lines.add(dnsIndex + 2, keyIndent + "    - '$entry'")
    }

    private fun hasListItem(
        lines: List<String>,
        start: Int,
        end: Int,
        value: String,
    ): Boolean {
        for (i in start until minOf(end, lines.size)) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val trimmed = line.trimStart()
            if (!SanitizerRules.LIST_ITEM.containsMatchIn(trimmed)) continue
            if (SanitizerYamlLines.unwrapQuotes(trimmed.removePrefix("-").trim()).equals(value, ignoreCase = true)) return true
        }
        return false
    }
}
