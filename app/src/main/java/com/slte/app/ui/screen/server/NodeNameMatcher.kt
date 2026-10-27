package com.slte.app.ui.screen.server

import com.slte.app.kernel.NodeNameResolver

/** 面板节点名 ↔ 内核节点名的纯匹配逻辑（协议前缀消歧），便于单测。 */
// NodeNameResolver 来自 kernel 包：面板名↔内核名的共享解析器
internal object NodeNameMatcher {

    private val KERNEL_TAG_TO_TYPE =
        mapOf(
            "vless" to "vless",
            "vmess" to "vmess",
            "trojan" to "trojan",
            "ss" to "shadowsocks",
            "hy" to "hysteria",
            "hy2" to "hysteria2",
            "tuic" to "tuic",
            "anytls" to "anytls",
            "socks" to "socks",
        )

    /** 内核节点名按匹配键建索引：键为去协议前缀后的名字。 */
    fun index(kernelNames: List<String>): Map<String, List<String>> = kernelNames.groupBy { NodeNameResolver.of(it) }.filterKeys { it.isNotEmpty() }

    /** 面板节点在内核里的名字：唯一同名直接用，多同名按协议标签消歧。 */
    fun resolveKernelName(
        index: Map<String, List<String>>,
        panelName: String,
        panelType: String,
    ): String? {
        val candidates = index[NodeNameResolver.of(panelName)] ?: return null
        candidates.singleOrNull()?.let { return it }

        val type = panelType.lowercase()
        return candidates
            .filter { candidate -> NodeNameResolver.protocolTag(candidate)?.let(KERNEL_TAG_TO_TYPE::get) == type }
            .singleOrNull()
    }

    fun matchKey(name: String): String = NodeNameResolver.of(name)
}
