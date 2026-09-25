// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

/**
 * 自定义分流组名 / 直连域名的输入校验。
 *
 * 与内核 `native/config/routing/reserved.go` 的校验规则必须保持一致——
 * **两侧都要有**：
 * - Kotlin 侧负责入口体验（即时报错）与存量清洗（`routing.json` 可能由旧版本
 *   或其它写入点留下非法值）；
 * - Go 侧是权威最后防线（`routing.json` 是磁盘文件，任何绕过 UI 的写入都会
 *   直达内核）。
 *
 * 为什么必须拒绝逗号：这些值会被拼进 mihomo 规则串
 * （`RULE-SET,<key>,<name>` / `DOMAIN-SUFFIX,<domain>,DIRECT`），而 mihomo 用
 * `strings.Split(ruleRaw, ",")` 分列。名字里的逗号会让字段错位：多数情况
 * 整个 profile 加载失败（连不上），形如 `DIRECT,x` 还会静默把规则指向别的出口。
 */
internal object RoutingInputValidator {

    /** 本地分流占用的全部名称：结构组 + 内置分流组 + mihomo 预注册出站。 */
    val reservedNames: Set<String> =
        RoutingReservedNames +
            RoutingGroups.map { it.name } +
            setOf(
                OUTBOUND_DIRECT,
                OUTBOUND_REJECT,
                "REJECT-DROP",
                "COMPATIBLE",
                "PASS",
                "PASS-RULE",
                "GLOBAL",
                "default",
            )

    private const val MAX_NAME_RUNES = 32
    private const val MAX_DOMAIN_LEN = 253
    private const val MAX_LABEL_LEN = 63

    /** 禁止出现的字符：规则分隔符、控制字符、引号。 */
    private const val NAME_FORBIDDEN = ",\"\t\r\n"

    fun isValidGroupName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name != name.trim()) return false
        if (name.codePointCount(0, name.length) > MAX_NAME_RUNES) return false
        if (name.any { it in NAME_FORBIDDEN }) return false
        if (name.any { it.code < 0x20 || it.code == 0x7f }) return false
        return name !in reservedNames
    }

    /** 直连域名的字符集校验（不含后缀白名单判定，后者由 ConfigValidation 负责）。 */
    fun isValidRuleDomainShape(domain: String): Boolean {
        val host = domain.trim().lowercase().trimEnd('.')
        if (host.isEmpty() || host.length > MAX_DOMAIN_LEN) return false
        if (!DOMAIN_CHARSET.matches(host)) return false
        val labels = host.split(".")
        if (labels.size < 2) return false
        return labels.none {
            it.isEmpty() || it.length > MAX_LABEL_LEN || it.startsWith("-") || it.endsWith("-")
        }
    }

    private val DOMAIN_CHARSET = Regex("^[a-z0-9.-]+$")
}
