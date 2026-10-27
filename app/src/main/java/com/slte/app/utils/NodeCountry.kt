package com.slte.app.utils

internal fun extractCountryCode(name: String): String {
    val folded = foldFullWidth(name)
    val lowered = folded.lowercase()
    COUNTRY_KEYWORDS.forEach { (keyword, code) ->
        if (lowered.contains(keyword)) return code
    }
    flagEmojiCode(folded)?.let { return it }
    delimitedIsoCode(folded)?.let { return it }
    return "XX"
}

private fun foldFullWidth(text: String): String {
    if (text.none { it == '\u3000' || it.code in 0xFF01..0xFF5E }) return text
    val builder = StringBuilder(text.length)
    text.forEach { char ->
        builder.append(
            when {
                char == '\u3000' -> ' '
                char.code in 0xFF01..0xFF5E -> (char.code - 0xFEE0).toChar()
                else -> char
            },
        )
    }
    return builder.toString()
}

private const val REGIONAL_INDICATOR_LEAD = 0xD83C
private val REGIONAL_INDICATOR_TAIL = 0xDDE6..0xDDFF

private fun flagEmojiCode(name: String): String? {
    var index = 0
    while (index + 3 < name.length) {
        val lead = name[index].code
        val first = name[index + 1].code
        val secondLead = name[index + 2].code
        val second = name[index + 3].code
        if (lead == REGIONAL_INDICATOR_LEAD &&
            first in REGIONAL_INDICATOR_TAIL &&
            secondLead == REGIONAL_INDICATOR_LEAD &&
            second in REGIONAL_INDICATOR_TAIL
        ) {
            val code =
                buildString(2) {
                    append(('A'.code + first - REGIONAL_INDICATOR_TAIL.first).toChar())
                    append(('A'.code + second - REGIONAL_INDICATOR_TAIL.first).toChar())
                }
            if (code in ISO_CODES) return code
        }
        index++
    }
    return null
}

private fun delimitedIsoCode(name: String): String? {
    for (index in 0 until name.length - 1) {
        val first = name[index]
        val second = name[index + 1]
        if (!first.isAsciiLetter() || !second.isAsciiLetter()) continue
        val before = if (index == 0) null else name[index - 1]
        val after = if (index + 2 >= name.length) null else name[index + 2]
        if (before != null && before.isAsciiLetter()) continue
        if (after != null && after.isAsciiLetter()) continue
        val code = "${first.uppercaseChar()}${second.uppercaseChar()}"
        if (code in DATA_UNIT_CODES && followsNumber(name, index)) continue
        if (code in ISO_CODES) return code
    }
    return null
}

private fun followsNumber(
    name: String,
    index: Int,
): Boolean {
    var position = index - 1
    if (position >= 0 && name[position] == ' ') position--
    return position >= 0 && name[position].isDigit()
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private val DATA_UNIT_CODES = setOf("GB", "TB", "MB", "KB", "PB", "EB", "ZB", "YB")

private val ISO_CODES =
    setOf(
        "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO",
        "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ",
        "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI",
        "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS",
        "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD",
        "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN",
        "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ",
        "DE", "DJ", "DK", "DM", "DO", "DZ", "EC", "EE",
        "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK",
        "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF",
        "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ",
        "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM",
        "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM",
        "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM",
        "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN",
        "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC",
        "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY",
        "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK",
        "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS",
        "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA",
        "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP",
        "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG",
        "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT",
        "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW",
        "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI",
        "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS",
        "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF",
        "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO",
        "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM",
        "US", "UY", "UZ", "VA", "VC", "VE", "VG", "VI",
        "VN", "VU", "WF", "WS", "XK", "YE", "YT", "ZA",
        "ZM", "ZW",
    )
