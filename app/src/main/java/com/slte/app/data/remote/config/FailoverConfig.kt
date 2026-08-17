package com.slte.app.data.remote.config

/**
 * failover 拦截器所需的配置访问抽象。
 *
 * 由 [RemoteConfig] 实现（运行期跟随远程配置切换）；
 * 抽象为接口便于集成测试注入固定候选，不依赖 Android 环境。
 */
interface FailoverConfig {
    /** 当前主 API 地址 */
    val apiBaseUrl: String

    /** 运行期 failover 候选列表（主地址在前，其余按健康度排列） */
    fun apiCandidates(primary: String): List<String>
}
