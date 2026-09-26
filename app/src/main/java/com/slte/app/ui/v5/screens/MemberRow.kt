// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.kernel.KernelProxyMember
import com.slte.app.kernel.KernelProxyMemberKind
import com.slte.app.kernel.PrimaryGroupName
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.LatencyText
import com.slte.app.ui.v5.RadioDot
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.utils.Constants
import com.slte.app.utils.extractCountryCode

/**
 * 候选成员行的共享渲染：节点页「节点选择」卡与分流组出口弹层用同一实现，
 * 保证「同一节点在两处显示相同延迟与相同标签」（否则又是双实现漂移源）。
 */

/** 延迟标记：未测 / 超时 / 实测。 */
internal enum class DelayMark { PENDING, TIMEOUT, MEASURED }

/**
 * 延迟展示判定（纯函数，可单测）。
 *
 * - null → 无数据 → 「未测」
 * - [Constants.DELAY_PENDING]（0）→ 从未测过 → 「未测」
 * - ≥ [Constants.DELAY_TIMEOUT]（999）→ 测过但不存活 → 「超时」
 * - 其余 → 实测，按阈值分色（<120 好 / <300 一般 / 其余差）
 */
internal fun delayDisplayOf(delay: Int?): Triple<DelayMark, ChipTone, String?> {
    if (delay == null || delay <= Constants.DELAY_PENDING) {
        return Triple(DelayMark.PENDING, ChipTone.NEUTRAL, null)
    }
    if (delay >= Constants.DELAY_TIMEOUT) {
        return Triple(DelayMark.TIMEOUT, ChipTone.DANGER, null)
    }
    val tone = when {
        delay < 120 -> ChipTone.OK
        delay < 300 -> ChipTone.WARN
        else -> ChipTone.DANGER
    }
    return Triple(DelayMark.MEASURED, tone, "$delay ms")
}

@Composable
internal fun memberLabel(member: KernelProxyMember): String = when (member.kind) {
    KernelProxyMemberKind.GROUP ->
        if (member.name == PrimaryGroupName) {
            stringResource(R.string.v5_group_follow_primary)
        } else {
            // 自动选择 / 故障转移 是独立组，也是主组的成员，原样显示组名
            member.name
        }
    KernelProxyMemberKind.DIRECT -> stringResource(R.string.routing_outbound_direct)
    KernelProxyMemberKind.REJECT -> stringResource(R.string.routing_outbound_block)
    KernelProxyMemberKind.NODE -> member.name
}

@Composable
internal fun MemberRow(
    member: KernelProxyMember,
    selected: Boolean,
    // 可为 null：内核未运行时的只读兜底名单没有任何可切换目标，
    // 传 null 让整行不可点且无涟漪（V5RowItem 的 noRippleClickable 语义），
    // 避免"点得动但什么都没发生"的假交互。
    onClick: (() -> Unit)? = null,
) {
    val (mark, tone, label) = delayDisplayOf(member.delay)
    // 结构项没有"延迟"这个概念（整改要求 3）：直连/拦截没有远端可探，给它显示"未测"
    // 会让人以为"忘了测"；组出口的延迟由内核按 interval=300 自行拨测产出，与 App 测速无关。
    val structuralRes =
        when (member.kind) {
            KernelProxyMemberKind.DIRECT, KernelProxyMemberKind.REJECT -> R.string.v5_delay_not_needed
            KernelProxyMemberKind.GROUP -> R.string.v5_delay_auto
            KernelProxyMemberKind.NODE -> null
        }
    V5RowItem(
        title = memberLabel(member),
        sub = memberSubtitle(member),
        leading = { RadioDot(on = selected) },
        trailing = {
            when {
                // 结构项若真拿到实测值（组拨测结果已回读）照样显示数字，不被占位符吞掉
                mark == DelayMark.MEASURED && label != null -> LatencyText(label, tone)
                // 超时是真实信号，优先于占位符
                mark == DelayMark.TIMEOUT ->
                    Text(
                        text = stringResource(R.string.server_timeout),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = V5ThemeColors.current.text3,
                    )
                structuralRes != null ->
                    Text(
                        text = stringResource(structuralRes),
                        fontSize = 12.5.sp,
                        color = V5ThemeColors.current.text3,
                    )
                else ->
                    // 节点未测/超时必须可区分：此前两者都显示"超时"
                    Text(
                        text = stringResource(R.string.server_untested),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = V5ThemeColors.current.text3,
                    )
            }
        },
        onClick = onClick,
    )
}

/** 组 → 「策略组」标记；节点 → 国家码（名称解析）。 */
@Composable
private fun memberSubtitle(member: KernelProxyMember): String? = when (member.kind) {
    KernelProxyMemberKind.GROUP -> stringResource(R.string.v5_group_item)
    KernelProxyMemberKind.NODE -> extractCountryCode(member.name).takeIf { it != "XX" }
    else -> null
}
