package com.slte.app.utils

/** 国家 ISO 码 → 中文地区名（GeoIP 归属地展示）；未收录代码回退大写代码本身 */
private val COUNTRY_NAMES = mapOf(
    "CN" to "中国", "HK" to "香港", "MO" to "澳门", "TW" to "台湾",
    "SG" to "新加坡", "JP" to "日本", "KR" to "韩国",
    "US" to "美国", "GB" to "英国", "CA" to "加拿大", "AU" to "澳大利亚",
    "DE" to "德国", "FR" to "法国", "NL" to "荷兰", "IT" to "意大利",
    "ES" to "西班牙", "CH" to "瑞士", "SE" to "瑞典", "NO" to "挪威",
    "FI" to "芬兰", "DK" to "丹麦", "PL" to "波兰", "RU" to "俄罗斯",
    "TR" to "土耳其", "AE" to "阿联酋", "IN" to "印度", "TH" to "泰国",
    "MY" to "马来西亚", "VN" to "越南", "ID" to "印尼", "PH" to "菲律宾",
    "BR" to "巴西", "ZA" to "南非", "UA" to "乌克兰", "CZ" to "捷克",
    "AT" to "奥地利", "BE" to "比利时", "IE" to "爱尔兰", "PT" to "葡萄牙"
)

fun countryName(code: String): String = COUNTRY_NAMES[code.uppercase()] ?: code.uppercase()
