package com.slte.app.utils

/** 合并三张表（简体优先匹配）。 */
internal val COUNTRY_KEYWORDS: Map<String, String> =
    linkedMapOf<String, String>().apply {
        putAll(SIMPLIFIED_COUNTRY_KEYWORDS)
        putAll(TRADITIONAL_COUNTRY_KEYWORDS)
    }
