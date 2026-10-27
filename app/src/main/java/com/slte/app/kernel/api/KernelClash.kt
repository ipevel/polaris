package com.slte.app.kernel.api

/** 分组与测速能力；实现方按 capabilities 声明是否可用。 */
interface KernelClash {
    fun queryTunnelMode(): KernelTunnelMode

    fun coreVersion(): String

    fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    fun queryProxyGroup(
        name: String,
        sort: KernelProxySort,
    ): KernelProxyGroupSnapshot

    suspend fun loadActiveProfile()

    fun patchSelector(
        group: String,
        name: String,
    ): Boolean

    /** 真连一次并返回延迟；与读缓存历史的 queryProxyGroup 不同。 */
    fun urlTest(
        name: String,
        timeoutMs: Int,
    ): KernelUrlTestResult

    suspend fun healthCheck(group: String)

    fun healthCheckAll()

    fun queryPersistedProxyMode(): KernelTunnelMode?

    fun setPersistedProxyMode(mode: KernelTunnelMode)

    fun tunStackMode(): String

    fun setTunStackMode(mode: String)
}
