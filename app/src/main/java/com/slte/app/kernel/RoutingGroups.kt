// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.kernel

/**
 * 内置分流组的 Kotlin 侧镜像，仅供设置页展示与开关写入（routing.json 的
 * groups 键）。真源是内核 `native/config/routing/routing_table.go`——名称
 * 必须逐字一致（含 emoji），否则开关写不进对应组。默认开关/默认出站语义
 * 对齐 Karing 预设 cn.json。
 */
data class RoutingGroupInfo(
    val name: String,
    val defaultOn: Boolean,
    val defaultOut: String, // proxy | direct | block
)

val RoutingGroups: List<RoutingGroupInfo> =
    listOf(
        RoutingGroupInfo("🛑 广告拦截", defaultOn = false, defaultOut = "block"),
        RoutingGroupInfo("🍃 应用净化", defaultOn = false, defaultOut = "block"),
        RoutingGroupInfo("📢 苹果推送通知", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🍎 苹果服务", defaultOn = true, defaultOut = "direct"),
        RoutingGroupInfo("📹 油管视频", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("♊️ Google Gemini", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🌏 Google Play", defaultOn = true, defaultOut = "proxy"),
        RoutingGroupInfo("📢 Google FCM", defaultOn = false, defaultOut = "direct"),
        RoutingGroupInfo("🌏 Google", defaultOn = true, defaultOut = "proxy"),
        RoutingGroupInfo("📲 Facebook", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("📲 X", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🎧 TikTok", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("📸 Instagram", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🎥 奈飞视频", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("📲 WhatsApp", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("📲 电报消息", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("💬 Claude", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("💬 OpenAI", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🐱 GitHub", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("Ⓜ️ 微软Bing", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("Ⓜ️ 微软云盘", defaultOn = false, defaultOut = "direct"),
        RoutingGroupInfo("Ⓜ️ 微软服务", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("🎮 游戏平台", defaultOn = false, defaultOut = "proxy"),
        RoutingGroupInfo("📺 哔哩哔哩", defaultOn = true, defaultOut = "direct"),
        RoutingGroupInfo("🎶 网易音乐", defaultOn = false, defaultOut = "direct"),
        RoutingGroupInfo("🎯 国内直连", defaultOn = true, defaultOut = "direct"),
        RoutingGroupInfo("🌏 国外穿墙", defaultOn = true, defaultOut = "proxy"),
    )

/** 内核保留组名（结构组 + 兜底组），与 Go 侧 GroupName* 常量一致，自定义组不得占用。 */
val RoutingReservedNames: Set<String> =
    setOf("🚀 节点选择", "自动选择", "故障转移", "🐟 漏网之鱼")
