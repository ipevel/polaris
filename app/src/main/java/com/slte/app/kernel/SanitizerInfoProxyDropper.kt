// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/**
 * 订阅「信息伪节点」剔除。
 *
 * 背景（真机实测，见 design/emulator 证据）：部分面板把账号信息伪装成**真实可用的代理**塞进
 * `proxies:`，名字就是说明文本（如「剩余流量：3983.45 GB」「距离下次重置剩余：5 天」
 * 「套餐到期：2029-04-20」），且与某个真节点**共用同一 server/port/uuid**，再被 include-all
 * 并入所有策略组。后果：
 *
 * 1. 内核 `url-test`（自动选择）在**配置候选集**里挑最低延迟，会真的选中这些信息条目，
 *    表现为「已连接 · 剩余流量：3983.45 GB · 249 ms」且速率 0B/s、IP 查询失败；
 * 2. App 节点页按面板 API 的节点索引排序（[orderMembers]），这些条目不在 API 列表里 →
 *    被排到列表最末尾，用户看不见，于是出现「列表里没有它，自动选择却选中了它」的错觉。
 *
 * 关键点：**过滤必须做在配置层**。只改 UI 没有用——内核根本不知道 App 的排序。
 *
 * 判定采用**双重条件**，对独享端点的真实节点零误杀：
 * 1. 名字像信息条目（含全角冒号「：」或命中 [INFO_NAME]）；且
 * 2. 该条目的端点（type+server+port+凭据）与**同文档内另一条代理完全重复**。
 *
 * 同一端点若**全部**是信息条目也不动（无从判断保留哪条），保证任何端点至少留一个代表。
 * 另外移除分组对这些名字的引用——否则内核会因为「引用了不存在的代理」拒绝加载整份配置。
 */
internal object SanitizerInfoProxyDropper {

    private const val PROXIES_KEY = "proxies"

    private const val GROUPS_KEY = "proxy-groups"

    /** 条目起始：flow 写法 `- {` 或块状写法 `- name:` */
    private val ITEM_START = Regex("^-\\s*(?:\\{|name\\s*:)")

    private val BLOCK_PROXIES_KEY = Regex("^\\s*proxies\\s*:\\s*(\\[.*)?$")

    /** 全角冒号：信息条目最显著的特征，真实节点名不用它 */
    private const val FULL_WIDTH_COLON = '\uFF1A'

    /** 信息条目的关键词（与端点重复条件配合使用，故可适度宽松） */
    private val INFO_NAME =
        Regex(
            "剩余流量|已用流量|总流量|套餐流量|套餐到期|到期时间|过期时间|距离下次重置|重置剩余|" +
                "流量重置|剩余天数|剩余时间|官网|官方网站|续费|购买|客服|邀请|订阅地址|订阅链接|" +
                "机场|公告|群组|频道|Telegram|电报群",
        )

    /**
     * 剔除信息伪节点，返回剔除条数。会同时清理 `proxy-groups` 里对它们的引用。
     *
     * 任一分组的成员会被清空时**整步放弃**（宁可留下伪节点，也不产出内核拒绝加载的配置）。
     */
    fun dropInfoProxies(lines: MutableList<String>): Int {
        val block = blockRange(lines, PROXIES_KEY) ?: return 0
        val entries = entries(lines, block)
        if (entries.size < 2) return 0

        val doomed = doomedNames(entries)
        if (doomed.isEmpty()) return 0

        if (wouldEmptyAnyGroup(lines, doomed)) {
            AppLog.w(SanitizerRules.LOG_TAG, "剔除信息伪节点会清空某个分组，已放弃本次剔除")
            return 0
        }

        // 先摘引用、再删代理行：删行会让后续下标整体前移，故引用的行号必须在删行前用掉。
        // 先删代理条目、再摘分组引用：删行会让其后的行号整体前移，而分组块通常在 proxies 之后
        // ——反过来做就会拿旧行号删到别的内容。摘引用内部会重新定位分组块，故这个顺序安全。
        //
        // 删的必须是**条目整段行**：块状写法一个条目跨 name/type/server/… 多行，只删首行会留下
        // 一堆孤立的键值行，产出坏 YAML（内核直接拒绝加载整份配置）。
        entries
            .filter { it.name in doomed }
            .map { it.line until it.end }
            .sortedByDescending { it.first }
            .forEach { range -> range.reversed().forEach { lines.removeAt(it) } }
        stripGroupReferences(lines, doomed)

        val detail = doomed.joinToString(", ") { sanitizeLog(it) }
        AppLog.i(SanitizerRules.LOG_TAG, "已剔除 ${doomed.size} 个信息伪节点（与真节点端点重复且名字为说明文本）: $detail")
        return doomed.size
    }

    /** 需要剔除的名字：同端点条目里「像信息条目」的那些，且该端点须留有非信息条目。 */
    private fun doomedNames(entries: List<ProxyEntry>): Set<String> {
        val doomed = linkedSetOf<String>()
        entries.groupBy { it.endpoint }.values.forEach { sameEndpoint ->
            if (sameEndpoint.size < 2) return@forEach
            val info = sameEndpoint.filter { isInfoLikeName(it.name) }
            if (info.isEmpty() || info.size == sameEndpoint.size) return@forEach
            info.forEach { doomed += it.name }
        }
        return doomed
    }

    fun isInfoLikeName(name: String): Boolean = name.contains(FULL_WIDTH_COLON) || INFO_NAME.containsMatchIn(name)

    // ---------------- 代理条目解析 ----------------

    private data class ProxyEntry(
        val line: Int,
        /** 条目结束行（不含）。块状写法一条目跨多行，删条目必须删整段。 */
        val end: Int,
        val name: String,
        val endpoint: String,
    )

    private fun entries(
        lines: List<String>,
        block: Block,
    ): List<ProxyEntry> {
        val result = mutableListOf<ProxyEntry>()
        var i = block.start
        while (i < block.end) {
            val line = lines[i]
            if (SanitizerYamlLines.leadingIndent(line) != block.itemIndent || !ITEM_START.containsMatchIn(line.trimStart())) {
                i++
                continue
            }
            val itemEnd = itemEnd(lines, i, block)
            val flow = line.contains('{')
            val name = if (flow) flowField(line, "name") else blockField(lines, i, itemEnd, "name")
            if (!name.isNullOrBlank()) {
                val fields = listOf("type", "server", "port", "uuid", "password").associateWith { key ->
                    if (flow) flowField(line, key) else blockField(lines, i, itemEnd, key)
                }
                result += ProxyEntry(i, itemEnd, name, endpointOf(fields))
            }
            i = itemEnd
        }
        return result
    }

    private fun itemEnd(
        lines: List<String>,
        start: Int,
        block: Block,
    ): Int {
        for (i in start + 1 until block.end) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (SanitizerYamlLines.leadingIndent(line) == block.itemIndent && ITEM_START.containsMatchIn(line.trimStart())) return i
            if (SanitizerYamlLines.leadingIndent(line).length < block.itemIndent.length) return i
        }
        return block.end
    }

    /** 端点指纹：协议 + 服务器 + 端口 + 凭据。任一项缺失则退化为可用部分的拼接。 */
    private fun endpointOf(fields: Map<String, String?>): String {
        val credential = fields["uuid"] ?: fields["password"] ?: ""
        return listOf(fields["type"] ?: "", fields["server"] ?: "", fields["port"] ?: "", credential).joinToString("|")
    }

    fun flowField(
        line: String,
        key: String,
    ): String? {
        val re = Regex("(?:^|[{,]\\s*)" + Regex.escape(key) + "\\s*:\\s*(?:'([^']*)'|\"([^\"]*)\"|([^,}\\]]+))")
        val m = re.find(line) ?: return null
        val value = m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }
        return value.trim().ifEmpty { null }
    }

    private fun blockField(
        lines: List<String>,
        start: Int,
        end: Int,
        key: String,
    ): String? {
        // 条目首行是 `- name: X`，其余行是 `  type: vless`——故键前的 `- ` 前缀必须可选，
        // 否则 name 永远取不到，整条信息伪节点就漏检了。
        val re = Regex("^\\s*(?:-\\s+)?" + Regex.escape(key) + "\\s*:\\s*(.*)$")
        for (i in start until end) {
            val m = re.find(lines[i]) ?: continue
            val value = SanitizerYamlLines.unwrapQuotes(m.groupValues[1].trim())
            return value.ifEmpty { null }
        }
        return null
    }

    // ---------------- 分组引用清理 ----------------

    /** 某分组的成员被剔除后是否会变成空列表（内核会拒绝加载空组）。 */
    private fun wouldEmptyAnyGroup(
        lines: List<String>,
        doomed: Set<String>,
    ): Boolean {
        val groupBlock = blockRange(lines, GROUPS_KEY) ?: return false
        var i = groupBlock.start
        while (i < groupBlock.end) {
            val line = lines[i]
            if (SanitizerYamlLines.leadingIndent(line) != groupBlock.itemIndent || !ITEM_START.containsMatchIn(line.trimStart())) {
                i++
                continue
            }
            val itemEnd = itemEnd(lines, i, groupBlock)
            val members = membersOf(lines, i, itemEnd)
            if (members.isNotEmpty() && members.all { it in doomed }) return true
            i = itemEnd
        }
        return false
    }

    /** 摘掉全部分组对 [doomed] 的引用（flow 内联数组 / 块状列表两种写法）。 */
    private fun stripGroupReferences(
        lines: MutableList<String>,
        doomed: Set<String>,
    ) {
        val groupBlock = blockRange(lines, GROUPS_KEY) ?: return
        val removals = mutableListOf<Int>()
        var i = groupBlock.start
        while (i < groupBlock.end) {
            val line = lines[i]
            if (SanitizerYamlLines.leadingIndent(line) != groupBlock.itemIndent || !ITEM_START.containsMatchIn(line.trimStart())) {
                i++
                continue
            }
            val itemEnd = itemEnd(lines, i, groupBlock)
            if (line.contains('{')) {
                // flow 内联数组：保留存活条目的原始字面量（含各自引号风格），语义不变
                val items = flowArrayItems(line)
                if (items != null && items.any { it.value in doomed }) {
                    val kept = items.filterNot { it.value in doomed }
                    lines[i] = line.substring(0, items.first().range.first) +
                        kept.joinToString(", ") { it.raw } +
                        line.substring(items.last().range.last + 1)
                }
            } else {
                for (j in i + 1 until itemEnd) {
                    val member = blockListItem(lines[j]) ?: continue
                    if (member in doomed) removals += j
                }
            }
            i = itemEnd
        }
        removals.sortedDescending().forEach { lines.removeAt(it) }
    }

    /** 分组当前引用的成员名（flow 内联数组优先，其次块状列表）。 */
    private fun membersOf(
        lines: List<String>,
        start: Int,
        end: Int,
    ): List<String> {
        val head = lines[start]
        if (head.contains('{')) {
            val items = flowArrayItems(head) ?: return emptyList()
            return items.map { it.value }
        }
        val members = mutableListOf<String>()
        for (j in start + 1 until end) {
            blockListItem(lines[j])?.let { members += it }
        }
        return members
    }

    private data class FlowItem(
        val value: String,
        val raw: String,
        val range: IntRange,
    )

    /**
     * 解析本行 `proxies: [...]` 的内联数组，返回各成员的**原始字面量**与在行内的区间。
     * 无该数组时返回 null。区间必须精确到字符，重组时才能只切掉被删成员。
     */
    private fun flowArrayItems(line: String): List<FlowItem>? {
        val keyAt = Regex("(?:^|[{,]\\s*)proxies\\s*:\\s*\\[").find(line)?.range?.last ?: return null
        val open = line.indexOf('[', keyAt - 1)
        if (open < 0) return null
        val close = matchingBracket(line, open) ?: return null
        val body = line.substring(open + 1, close)
        val items = mutableListOf<FlowItem>()
        var i = 0
        while (i < body.length) {
            while (i < body.length && (body[i] == ' ' || body[i] == ',')) i++
            if (i >= body.length) break
            val start = i
            if (body[i] == '\'' || body[i] == '"') {
                val quote = body[i]
                var j = i + 1
                while (j < body.length) {
                    if (quote == '\'' && body[j] == '\'' && j + 1 < body.length && body[j + 1] == '\'') {
                        j += 2
                        continue
                    }
                    if (body[j] == quote) break
                    j++
                }
                if (j >= body.length) return null
                val raw = body.substring(start, j + 1)
                items += FlowItem(unwrap(raw), raw, (open + 1 + start)..(open + 1 + j))
                i = j + 1
            } else {
                var j = i
                while (j < body.length && body[j] != ',') j++
                var end = j
                while (end > i && body[end - 1] == ' ') end--
                val raw = body.substring(i, end)
                if (raw.isNotEmpty()) {
                    items += FlowItem(unwrap(raw), raw, (open + 1 + i)..(open + 1 + end - 1))
                }
                i = j
            }
        }
        return items
    }

    private fun matchingBracket(
        line: String,
        open: Int,
    ): Int? {
        var depth = 0
        var i = open
        var quote: Char? = null
        while (i < line.length) {
            val char = line[i]
            if (quote != null) {
                if (char == quote) quote = null
            } else {
                when (char) {
                    '\'', '"' -> quote = char
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }

    private fun unwrap(raw: String): String = if (raw.length >= 2 && (raw.first() == '\'' || raw.first() == '"') && raw.last() == raw.first()) {
        raw.substring(1, raw.length - 1).replace("''", "'")
    } else {
        raw
    }

    /** 块状列表项 `- 'node'` 的值；不是列表项时返回 null。 */
    private fun blockListItem(line: String): String? {
        val trimmed = line.trimStart()
        if (!SanitizerRules.LIST_ITEM.containsMatchIn(trimmed)) return null
        val value = trimmed.removePrefix("-").trim()
        if (value.isEmpty()) return null
        return SanitizerYamlLines.unwrapQuotes(value)
    }

    // ---------------- 区块定位 ----------------

    private data class Block(
        val itemIndent: String,
        val start: Int,
        val end: Int,
    )

    /** 顶层 `key:` 块的范围（与 [SanitizerNameDeduper] 同口径，含空行与注释）。 */
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
}
