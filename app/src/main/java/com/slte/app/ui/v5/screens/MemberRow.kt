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
    onClick: () -> Unit,
) {
    val (mark, tone, label) = delayDisplayOf(member.delay)
    V5RowItem(
        title = memberLabel(member),
        sub = memberSubtitle(member),
        leading = { RadioDot(on = selected) },
        trailing = {
            if (mark == DelayMark.MEASURED && label != null) {
                LatencyText(label, tone)
            } else {
                // 「未测」与「超时」必须可区分：此前两者都显示"超时"
                Text(
                    text = stringResource(
                        if (mark == DelayMark.TIMEOUT) R.string.server_timeout else R.string.server_untested,
                    ),
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
