package com.github.kr328.clash.core.model

/** 覆盖槽位：独立于此前的 Clash 单例，避免仅引用枚举就触发 native 库加载。 */
enum class OverrideSlot {
    Persist,
    Session,
}
