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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.InviteStat
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.V5CardFlat

/**
 * 邀请返利概览（v5）：可提现佣金额是这一屏的第一信息（大号等宽数字 + 强调色），
 * 四项明细压缩成等宽数值列表，避免原先「贴纸压过数字」的层级倒置。
 */
@Composable
fun InviteStatCard(stat: InviteStat) {
    val c = V5ThemeColors.current
    V5CardFlat(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Text(
                text = formatCurrency(stat.availableBalance),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = c.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.invite_stat_available),
                fontSize = 11.5.sp,
                color = c.text3,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 1.dp, color = c.hairline2)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatColumn(
                    label = stringResource(R.string.invite_stat_registered),
                    value =
                    pluralStringResource(
                        R.plurals.invite_users,
                        stat.registeredUsers,
                        stat.registeredUsers,
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.invite_stat_commission_rate),
                    value = stringResource(R.string.invite_rate, stat.commissionRate),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.invite_stat_total),
                    value = formatCurrency(stat.totalCommission),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.invite_stat_pending),
                    value = formatCurrency(stat.pendingCommission),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = V5ThemeColors.current
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = c.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
