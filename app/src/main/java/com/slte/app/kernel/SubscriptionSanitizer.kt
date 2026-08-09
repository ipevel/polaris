package com.slte.app.kernel

import com.slte.app.utils.AppLog

/**
 * 订阅 YAML 清洗器：落盘前清零入站端口、清空 ui-subtitle-pattern、注入直连规则与 fake-ip 豁免。
 * 行级编辑不改变 YAML 结构；结构异常时安全跳过，绝不抛异常。
 */
object SubscriptionSanitizer {

    /** 需清零的顶层端口键（0 = 不监听） */
    private val ZEROED_PORT_KEYS = setOf("port", "socks-port", "mixed-port", "redir-port", "tproxy-port")

    /** 匹配顶层 "key: value" 行 */
    private val TOP_LEVEL_KEY_VALUE = Regex("^(port|socks-port|mixed-port|redir-port|tproxy-port|allow-lan|bind-address)\\s*:\\s*.*$")

    /** 匹配任意缩进的 ui-subtitle-pattern 行 */
    private val SUBTITLE_PATTERN_LINE = Regex("^(\\s*ui-subtitle-pattern\\s*:\\s*).*$")

    private val RULES_KEY = Regex("^rules\\s*:\\s*$")
    private val DNS_KEY = Regex("^dns\\s*:\\s*$")
    private val FAKE_IP_FILTER_KEY = Regex("^fake-ip-filter\\s*:\\s*$")
    private val FLOW_START = Regex("^[^#].*\\{\\s*$")

    /**
     * 订阅内容校验：必须是含 proxies 块的 Clash YAML；
     * HTML 错误页/JSON 错误体等异常响应直接拒绝。
     */
    fun isValidSubscribeYaml(text: String): Boolean {
        if (text.isBlank()) return false
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<") || trimmed.startsWith("{")) return false
        return text.contains("proxies:")
    }

    /**
     * 清洗订阅 YAML。
     *
     * @param domains 需要直连的自家域名列表（如 example.com），全部注入直连规则与 fake-ip 豁免
     * @return 清洗后的 YAML；异常时返回原文，不阻断订阅导入
     */
    fun sanitize(text: String, domains: List<String>): String {
        if (text.isBlank()) return text
        val validDomains = domains.filter { it.isNotBlank() }
        if (validDomains.isEmpty()) return text
        return try {
            val lines = text.lines().toMutableList()
            zeroTopLevelPorts(lines)
            clearSubtitlePattern(lines)
            validDomains.forEach { injectDirectRule(lines, it) }
            validDomains.forEach { injectFakeIpFilter(lines, it) }
            lines.joinToString("\n")
        } catch (_: Exception) {
            // 防御：行编辑逻辑出错时返回原文，宁可保留原配置也不破坏订阅
            AppLog.w("SLTE-Sanitizer", "sanitize 异常回退原文，内核补丁链兜底")
            text
        }
    }

    /** 清零顶层端口/allow-lan/bind-address（仅顶层行） */
    private fun zeroTopLevelPorts(lines: MutableList<String>) {
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isEmpty() || line[0] == ' ' || line[0] == '\t') continue
            val m = TOP_LEVEL_KEY_VALUE.matchEntire(line) ?: continue
            lines[i] = when (m.groupValues[1]) {
                "allow-lan" -> "allow-lan: false"
                "bind-address" -> "bind-address: \"\""
                in ZEROED_PORT_KEYS -> "${m.groupValues[1]}: 0"
                else -> line
            }
        }
    }

    /** 清空 ui-subtitle-pattern（消除 ReDoS 输入面） */
    private fun clearSubtitlePattern(lines: MutableList<String>) {
        for (i in lines.indices) {
            val m = SUBTITLE_PATTERN_LINE.matchEntire(lines[i]) ?: continue
            lines[i] = m.groupValues[1] + "\"\""
        }
    }

    /** 注入直连规则：插入 rules 块头部，缩进跟随已有条目；flow 风格或缺失时跳过 */
    private fun injectDirectRule(lines: MutableList<String>, domain: String) {
        val rule = "DOMAIN-SUFFIX,$domain,DIRECT"
        if (lines.any { it.trim().removePrefix("- ").trim().trim('\'').trim('"') == rule }) return

        val rulesIndex = lines.indexOfFirst { it.trim() == it && RULES_KEY.matches(it.trim()) }
        if (rulesIndex < 0) return
        // flow 风格：rules: [ 同行有 [ 或 { ，无法安全行插入
        if (lines[rulesIndex].contains('[') || lines[rulesIndex].contains('{')) return

        val indent = blockItemIndent(lines, rulesIndex) ?: return
        lines.add(rulesIndex + 1, indent + "- '$rule'")
    }

    /** 注入 fake-ip-filter 条目：跟随已有块缩进；缺失时在 dns 块内新建；异常跳过 */
    private fun injectFakeIpFilter(lines: MutableList<String>, domain: String) {
        val entry = "+.$domain"
        if (lines.any { it.trim().removePrefix("- ").trim().trim('\'').trim('"') == entry }) return

        // 内联/flow 形式（行尾非冒号）无法安全合并：跳过注入避免重复键，域名豁免由内核 patchDns 兜底
        if (lines.any { it.trim().startsWith("fake-ip-filter:") && !it.trim().endsWith(":") }) return

        val keyIndex = lines.indexOfFirst { FAKE_IP_FILTER_KEY.matches(it.trim()) }
        if (keyIndex >= 0) {
            val indent = blockItemIndent(lines, keyIndex) ?: return
            lines.add(keyIndex + 1, indent + "- '$entry'")
            return
        }

        val dnsIndex = lines.indexOfFirst { it.trim() == it && DNS_KEY.matches(it.trim()) }
        if (dnsIndex < 0) return
        if (lines[dnsIndex].contains('{')) return // flow 风格

        val keyIndent = blockKeyIndent(lines, dnsIndex) ?: return
        lines.add(dnsIndex + 1, keyIndent + "fake-ip-filter:")
        lines.add(dnsIndex + 2, keyIndent + "    - '$entry'")
    }

    /** 块内条目缩进：取键后首个非空、非注释行的前导空白 */
    private fun blockItemIndent(lines: List<String>, keyIndex: Int): String? {
        for (i in keyIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (line.trimStart().startsWith("-")) {
                return line.substringBefore(line.trimStart())
            }
            return null // 下一行是键而非条目：块为空或格式异常
        }
        return null
    }

    /** 块内键缩进：取键后首个非空、非注释行的前导空白 */
    private fun blockKeyIndent(lines: List<String>, keyIndex: Int): String? {
        for (i in keyIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = line.substringBefore(line.trimStart())
            return indent
        }
        return null
    }
}
