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

/** 主选择组名：唯一真源，必须与内核 GroupNameSelector（routing_table.go）逐字一致。 */
const val PrimaryGroupName: String = "🚀 节点选择"

/** 结构出口名，与内核 outboundDirect / outboundReject 一致。 */
const val OUTBOUND_DIRECT: String = "DIRECT"
const val OUTBOUND_REJECT: String = "REJECT"

/** 内核保留组名（结构组 + 兜底组），与 Go 侧 GroupName* 常量一致，自定义组不得占用。 */
val RoutingReservedNames: Set<String> =
    linkedSetOf(PrimaryGroupName, "自动选择", "故障转移", "🐟 漏网之鱼")

/**
 * 解析节点页「节点选择」卡对应的主组（纯函数，可单测）。
 *
 * 顺序即契约：
 * 1. 精确名命中（本地分流开启时的正常路径）；
 * 2. 第一个可选（Selector）组——本地分流关闭 / 面板改名时的回退，
 *    保证不会出现「没有任何节点被选中」；
 * 3. 都没有 → null（UI 显示空态）。
 *
 * 注意不要用 `now == null` 判空态：主组存在但尚无选中项时要照常渲染成员列表。
 */
fun primaryGroupOf(groups: List<KernelProxyGroupInfo>): KernelProxyGroupInfo? {
    val byName = groups.firstOrNull { it.name == PrimaryGroupName }
    return byName ?: groups.firstOrNull { it.selectable }
}

/**
 * 按面板节点顺序重排主组成员（纯函数，可单测）。
 *
 * 内核 `include-all` 并入的成员会被 mihomo 的 `slices.Sort` 按 UTF-8 字节序
 * 排序，与面板 API 顺序不一致；而 Kotlin 的 `String.compareTo` 是 UTF-16
 * code unit 序，含 emoji 的节点名两者结果不同——所以要按索引映射重排，
 * 不做字符串比较。
 *
 * 规则：结构项（组 / DIRECT / REJECT）保持内核相对位置；节点段按 [nodeOrderIndex]
 * 排序；不在面板列表里的成员（仅来自 proxy-provider）按内核序追加到节点段末尾。
 * [nodeOrderIndex] 为空时原样返回内核序（降级，绝不丢成员）。
 */
fun orderMembers(
    members: List<KernelProxyMember>,
    nodeOrderIndex: Map<String, Int>,
): List<KernelProxyMember> {
    if (nodeOrderIndex.isEmpty()) return members
    val structural = members.filter { it.kind != KernelProxyMemberKind.NODE }
    val nodes = members.filter { it.kind == KernelProxyMemberKind.NODE }
    if (nodes.isEmpty()) return members
    val ordered = nodes.sortedBy { nodeOrderIndex[it.name] ?: Int.MAX_VALUE }
    return structural + ordered
}
