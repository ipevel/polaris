package com.slte.app.kernel

/** 运行期配置访问抽象（API 地址与直连域名），由 data 层实现 */
interface AppRemoteConfig {
    val apiBaseUrl: String
    val directDomains: List<String>
}
