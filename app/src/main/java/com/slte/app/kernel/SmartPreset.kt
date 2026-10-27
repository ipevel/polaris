package com.slte.app.kernel

/**
 * 智能内核配置预设：注入"自动选择"smart 组并设为主选择器默认项。
 * 行级转换、幂等；同名 smart 组已存在则复用；无可选组或无出站时原样返回。
 */
internal object SmartPreset {

    private const val GROUPS_KEY = "proxy-groups"
    internal const val AUTO_BASE = "自动选择"
    private const val AUTO_NAME_LIMIT = 9

    fun apply(text: String): String {
        if (SanitizerProxyNames.names(text).isEmpty() && !SanitizerProxyNames.hasProviders(text)) return text
        val lines = SanitizerYamlLines.stripBom(text).lines().toMutableList()
        val groupsIndex = SanitizerYamlLines.topLevelBlockIndices(lines, GROUPS_KEY).firstOrNull() ?: return text
        val itemIndent = SanitizerYamlLines.blockItemIndent(lines, groupsIndex) ?: return text
        val groups = SmartPresetParser.parseGroups(lines, groupsIndex, itemIndent)
        val selector = groups.firstOrNull { it.type.equals("select", ignoreCase = true) } ?: return text

        val autoName = resolveAutoName(groups) ?: return text
        if (groups.none { it.name == autoName }) {
            SmartPresetRewriter.insertAutoGroup(lines, groupsIndex, itemIndent, autoName)
        }
        SmartPresetRewriter.setSelectorDefault(lines, selector, autoName)
        return lines.joinToString("\n")
    }

    /** 名字空闲则新建；已有同名 smart 组则复用；被其他类型占用则换下一个候选名。 */
    private fun resolveAutoName(groups: List<SmartPresetParser.Group>): String? {
        var candidate = AUTO_BASE
        var suffix = 2
        while (true) {
            val existing = groups.firstOrNull { it.name == candidate }
            if (existing == null || existing.type.equals("smart", ignoreCase = true)) return candidate
            if (suffix > AUTO_NAME_LIMIT) return null
            candidate = "$AUTO_BASE $suffix"
            suffix++
        }
    }
}
