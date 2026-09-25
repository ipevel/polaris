// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.kernel.KernelProxyGroupInfo
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.RadioDot
import com.slte.app.ui.v5.V5RowItem

/**
 * 分流规则组的出口选择弹层。
 *
 * 成员来源 = 内核该策略组的成员列表（结构出口 跟随节点选择/自动选择/故障转移/
 * DIRECT/REJECT 在前，其后是 include-all 并入的全部节点），点选即切换该组的
 * 出口（内核 patchSelector），可让单个分类走指定节点。
 */
@Composable
fun GroupExitSheet(
    group: KernelProxyGroupInfo,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val c = V5ThemeColors.current
    SlteSheet(
        title = group.name,
        subtitle = stringResource(R.string.v5_group_exit_hint),
        onDismiss = onDismiss,
    ) {
        group.members.forEach { member ->
            V5RowItem(
                title = memberLabel(member.name),
                sub = if (member.isGroup) stringResource(R.string.v5_group_item) else null,
                leading = { RadioDot(on = member.name == group.now) },
                onClick = { onSelect(member.name) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.v5_group_current, group.now ?: "--"),
            fontSize = 11.5.sp,
            color = c.text3,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp),
        )
    }
}

/** 结构出口的本地化名称；节点名原样展示。 */
@Composable
private fun memberLabel(name: String): String = when (name) {
    com.slte.app.kernel.RoutingReservedNames.first() -> stringResource(R.string.v5_group_follow_primary)
    "DIRECT" -> stringResource(R.string.routing_outbound_direct)
    "REJECT" -> stringResource(R.string.routing_outbound_block)
    else -> name
}
