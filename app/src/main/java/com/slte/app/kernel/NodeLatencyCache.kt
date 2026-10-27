package com.slte.app.kernel

/** 节点延迟缓存封装；测量流程在 [nodeLatencyStream]。 */

/** 上次测速的缓存结果（按节点名），用于先渲染再测速。 */
fun KernelProxy.cachedSpeedResults(): Map<String, Int>? = speedResultStore.getSpeedResults()

/** 订阅更新后延迟会变，缓存直接失效，避免界面先显示上一份订阅的旧延迟。 */
fun KernelProxy.clearSpeedResults() = speedResultStore.clearSpeedResults()
