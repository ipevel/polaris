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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.InviteStat
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.formatCurrency
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 邀请返利概览：可提现佣金额是这一屏的第一信息（大号等宽数字 + 强调色），
 * 四项明细压缩成等宽数值列表，避免原先「贴纸压过数字」的层级倒置。
 */
@Composable
fun InviteStatCard(stat: InviteStat) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.inviteStatCardPaddingV),
        ) {
            Text(
                text = formatCurrency(stat.availableBalance),
                style = SlteType.value.copy(fontSize = 28.sp, lineHeight = 34.sp),
                color = SlteColors.current.accentInteractive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.xs))
            Text(
                text = stringResource(R.string.invite_stat_available),
                style = SlteType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Dimens.gap.lg))
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
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
    Column(modifier = modifier) {
        Text(
            text = value,
            style = SlteType.valueSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = label,
            style = SlteType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
