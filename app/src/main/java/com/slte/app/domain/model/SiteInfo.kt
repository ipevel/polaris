package com.slte.app.domain.model

/**
 * 站点基础信息，从后端 `guest/comm/config` 动态拉取。
 * 用于关于页与首页展示，替代硬编码的应用名/描述。
 */
data class SiteInfo(
    /** 站点名称；未配置时返回 app_name 字符串资源的默认值 */
    val appName: String? = null,
    /** 站点描述；未配置返回 about_app_desc 字符串资源的默认值 */
    val appDescription: String? = null,
    /** 站点地址 */
    val appUrl: String? = null,
)

/**
 * 从面板 URL 的 hostname 派生出可读的站点名称。
 * 例如 https://panel.mysite.com → "mysite"
 */
internal fun String?.parseHostnameForSiteName(): String? {
    if (this.isNullOrBlank()) return null
    val hostname = try {
        java.net.URI(this).host ?: return null
    } catch (_: Exception) {
        return null
    }
    // 取第一段作为名称（如 "panel.mysite.com" → "panel"）
    val parts = hostname.split(".")
    val first = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    return if (first.length <= 3 && parts.size > 1) {
        // 太短可能是 www/panel/api 等，取第二段
        parts[1].takeIf { it.isNotBlank() }
    } else {
        first
    }?.replaceFirstChar { it.uppercase() }
}
