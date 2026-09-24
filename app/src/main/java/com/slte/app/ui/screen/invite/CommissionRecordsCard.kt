// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@Composable
fun CommissionRecordsCard(records: List<CommissionRecord>) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.lg),
        ) {
            Text(
                text = stringResource(R.string.invite_records_title),
                style = SlteType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.invite_records_empty),
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.gap.md),
                )
            } else {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                records.forEachIndexed { index, record ->
                    CommissionRecordItem(record = record, topDivider = index > 0)
                }
            }
        }
    }
}

@Composable
private fun CommissionRecordItem(
    record: CommissionRecord,
    topDivider: Boolean,
) {
    val amountLabel =
        stringResource(R.string.invite_record_amount_label, FormatUtils.balance(record.orderAmount))
    val date = FormatUtils.formatDate(record.createdAt)
    val meta =
        if (date.isBlank()) {
            amountLabel
        } else {
            amountLabel + " " + stringResource(R.string.plan_separator) + " " + date
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.invite_record_order, record.tradeNo),
                    style = SlteType.valueSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Dimens.gap.xs))
                Text(
                    text = meta,
                    style = SlteType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(Dimens.gap.sm))
            Text(
                text = stringResource(R.string.invite_record_amount, FormatUtils.balance(record.getAmount)),
                style = SlteType.valueSmall,
                color = SlteColors.current.accentInteractive,
                maxLines = 1,
            )
        }
    }
}
