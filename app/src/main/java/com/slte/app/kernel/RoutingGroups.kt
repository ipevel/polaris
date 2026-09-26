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

/** 主组的自动测速组名（内核 GroupNameAuto），保留为用户可选出口。 */
const val AUTO_GROUP_NAME: String = "自动选择"

/** 主组的故障转移组名（内核 GroupNameFallback）。按产品要求从节点页 UI 移除。 */
const val FALLBACK_GROUP_NAME: String = "故障转移"

/**
 * 节点页各组成员里实际可见的成员（纯函数，可单测）。
 *
 * 产品决策：**移除「故障转移」入口**（用户反馈其行为不符合预期），**保留「自动选择」**
 * ——它是内核主组的首位成员即默认出口，且本次已修好"选择不生效"的两个缺陷
 * （`PatchSelector` 吞错 → 改为回读 `now` 确认；`selectPrimary` 硬编码组名 → 改为传入
 * 实际展示的组名）。
 *
 * 必须保留 `now` 命中项：否则当某组当前出口正是「故障转移」时，卡片头部会显示该名字
 * 而列表里没有任何勾选行（"鬼选中"）。
 */
fun visibleGroupMembers(
    members: List<KernelProxyMember>,
    now: String?,
): List<KernelProxyMember> = members.filter { member ->
    member.name != FALLBACK_GROUP_NAME || member.name == now
}

/** 内核保留组名（结构组 + 兜底组），与 Go 侧 GroupName* 常量一致，自定义组不得占用。 */
val RoutingReservedNames: Set<String> =
    linkedSetOf(PrimaryGroupName, AUTO_GROUP_NAME, FALLBACK_GROUP_NAME, "🐟 漏网之鱼")

/**
 * 节点页「节点选择」主组卡的折叠键。
 *
 * 刻意不用组名：主组在本地分流关闭 / 面板改名时会回退到"第一个 Selector 组"（见 [primaryGroupOf]），
 * 用组名做键有两个坑——
 * 1. 回退名恰好与某条分流组同名时，两张卡共用同一个键，收一张连带另一张；
 * 2. 解析结果在刷新之间漂移时，用户刚收起的键对不上，卡片自己弹回展开。
 * 该键含不可见字符，不可能与任何真实组名相等。
 */
const val PRIMARY_SECTION_KEY: String = "\u0000primary"

/**
 * 节点页区块折叠集合的切换（纯函数，可单测）。集合内 = **已收起**。
 *
 * 语义仍是"黑名单（收起集合）"：不在集合里 = 展开。但**初始值不再是空集**——
 * 产品要求节点页分组"默认折叠"，由 ServerViewModel 在区块**首次出现**时把键写进集合
 * （首见即收起）。所以 UI 侧只需要一次 `name in collapsed` 判断，不需要散落 if-else。
 *
 * key 必须是**跨刷新稳定的标识**：每次测速/订阅更新都会整体重建组对象
 * （同名对象的 now/members 已变，data class 不相等），用对象或下标做 key
 * 会让折叠态在刷新后静默失效。主组卡用 [PRIMARY_SECTION_KEY]，其余用组名。
 */
fun toggleCollapsed(
    current: Set<String>,
    name: String,
): Set<String> = if (name in current) current - name else current + name

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
