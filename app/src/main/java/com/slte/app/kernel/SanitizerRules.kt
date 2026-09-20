package com.slte.app.kernel

internal enum class KeyState {
    MISSING,
    EMPTY,
    PRESENT,
}

internal object SanitizerRules {

    const val HEALTH_CHECK_URL = "https://www.gstatic.com/generate_204"

    const val HEALTH_CHECK_TIMEOUT_MS = 5_000

    const val MAX_DOMAIN_LENGTH = 253

    const val MAX_DOMAIN_LABEL_LENGTH = 63

    const val LOG_TAG = "SLTE-Sanitizer"

    const val BOM = '\uFEFF'

    val HEALTH_CHECK_GROUP_TYPES = setOf("url-test", "fallback", "load-balance")

    val ZEROED_PORT_KEYS = setOf("port", "socks-port", "mixed-port", "redir-port", "tproxy-port")

    val NEUTRALIZED_SCALAR_KEYS =
        setOf(
            "external-controller",
            "external-controller-tls",
            "external-controller-unix",
            "external-controller-pipe",
            "external-ui",
            "external-ui-name",
            "external-ui-url",
            "secret",
        )

    val DROPPED_TOP_LEVEL_KEYS = setOf("hosts", "script", "scripting", "web", "listeners", "<<")

    val FORCED_OFF_TOP_LEVEL_KEYS = setOf("tun")

    val ZEROED_SWITCH_KEYS = setOf("allow-lan", "bind-address")

    val REWRITTEN_TOP_LEVEL_KEYS = ZEROED_PORT_KEYS + ZEROED_SWITCH_KEYS

    const val RULES_KEY = "rules"

    const val DNS_KEY = "dns"

    val PLAIN_KEY = Regex("^[A-Za-z0-9_-]+$")

    val SUBSCRIBE_ENTRY_KEY = Regex(
        "^['\"]?(proxies|proxy-providers)['\"]?\\s*:\\s*(?:$|#|\\[|\\{|&|!|~|null(?:\\s|$|#))",
    )

    val TOP_LEVEL_BLOCK_HEAD = Regex("^['\"]?(?:<<|[A-Za-z0-9_-]+)['\"]?\\s*:\\s*(?:&\\S+)?\\s*$")

    val TOP_LEVEL_FLOW_HEAD = Regex("^['\"]?(?:<<|[A-Za-z0-9_-]+)['\"]?\\s*:\\s*(?:&\\S+\\s*)?\\{")

    val TOP_LEVEL_KEY_VALUE =
        Regex("^['\"]?(${REWRITTEN_TOP_LEVEL_KEYS.joinToString("|")})['\"]?\\s*:\\s*.*$")

    val SUBTITLE_PATTERN_LINE = Regex("^(\\s*ui-subtitle-pattern\\s*:\\s*).*$")

    val GROUP_ITEM_START = Regex("^\\s*-\\s*name\\s*:")

    val PROVIDER_KEY = Regex("^[A-Za-z0-9_-]+\\s*:\\s*$")

    val BLOCK_KEY = Regex("^(\\s*)([A-Za-z0-9_-]+)\\s*:")

    val ENABLE_KEY = Regex("^['\"]?enable['\"]?\\s*:")

    val FAKE_IP_FILTER_LINE = Regex("^fake-ip-filter\\s*:\\s*$")

    val INLINE_FAKE_IP_FILTER = Regex("^fake-ip-filter\\s*:\\s*\\S")

    val LIST_ITEM = Regex("^-\\s*")

    val KEY_SMUGGLING = Regex("[\\\\&*]")
}
