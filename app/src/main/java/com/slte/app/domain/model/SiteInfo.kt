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
