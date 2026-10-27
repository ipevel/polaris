package com.slte.app.kernel.api

/** 编译期可打入的内核集合；枚举值不参与序列化，持久化用 [storageKey]。 */
enum class KernelType(
    val storageKey: String,
    val displayName: String,
) {
    MIHOMO("mihomo", "mihomo"),
    MIHOMO_SMART("mihomo_smart", "mihomo_smart"),
    ;

    companion object {
        fun fromStorageKey(key: String?): KernelType? = entries.firstOrNull { it.storageKey == key }
    }
}

/** 能力协商：调用方先判断再调用，未声明的能力不提供实现。 */
enum class KernelCapability {
    PROXY_GROUPS,
    URL_TEST,
    SELECTOR_PATCH,
    HEALTH_CHECK,
    PROFILE_MANAGEMENT,
    TUN_STACK_CONFIG,
}
