// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.TicketMessage
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.utils.FormatUtils

/**
 * 工单消息气泡（v5）。
 *
 * 「我」= 蓝色底 + 蓝字右对齐；客服 = `surface2` 底 + 主字色左对齐，圆角 16dp
 * （v4 用的是 `SlteShapes.medium`＝12dp，与 v5 卡片语言不搭）。
 */
@Composable
internal fun TicketMessageBubble(message: TicketMessage) {
    val c = V5ThemeColors.current
    val isMe = message.isMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = stringResource(if (isMe) R.string.ticket_sender_me else R.string.ticket_sender_support),
                fontSize = 11.5.sp,
                color = c.text3,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isMe) c.accentBg else c.surface2)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.message,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = if (isMe) c.accent else c.text,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = FormatUtils.formatDate(message.createdAt),
                fontSize = 11.sp,
                color = c.text3,
            )
        }
    }
}
