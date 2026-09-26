// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.utils.FormatUtils

/**
 * 佣金记录卡（v5）：整卡一行标题 + 记录行（订单号/金额+日期/返利额）。
 *
 * 行间发丝线改了两次：
 * 1. 原来用 `MaterialTheme.outlineVariant`（v4 灰）→ 换成 v5 `hairline2`；
 * 2. 更重要的是**原来用的是卡内 `padding(horizontal = 16dp)` 的容器**，发丝线会随内边距缩进、
 *    看起来像"半截线"；v5 的卡片行列表统一是**通宽**发丝线（见 `V5MeScreen`/`V5SettingsScreen`），
 *    因此这里把水平内边距下沉到每一行，分隔线走满卡宽。
 */
@Composable
fun CommissionRecordsCard(records: List<CommissionRecord>) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.invite_records_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            )

            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.invite_records_empty),
                    fontSize = 12.5.sp,
                    color = c.text3,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            } else {
                records.forEachIndexed { index, record ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    CommissionRecordItem(record = record)
                }
            }
        }
    }
}

@Composable
private fun CommissionRecordItem(record: CommissionRecord) {
    val c = V5ThemeColors.current
    val amountLabel =
        stringResource(R.string.invite_record_amount_label, FormatUtils.balance(record.orderAmount))
    val date = FormatUtils.formatDate(record.createdAt)
    val meta =
        if (date.isBlank()) {
            amountLabel
        } else {
            amountLabel + " " + stringResource(R.string.plan_separator) + " " + date
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.invite_record_order, record.tradeNo),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = meta,
                fontSize = 11.5.sp,
                color = c.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.invite_record_amount, FormatUtils.balance(record.getAmount)),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.accent,
            maxLines = 1,
        )
    }
}