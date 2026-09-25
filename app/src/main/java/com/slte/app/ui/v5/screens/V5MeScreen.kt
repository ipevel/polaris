// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.screen.profile.ProfileData
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.AvatarP
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar

/* ============================================================
   v5 我的页：账号卡 + 功能清单 + 退出
   （数据接线：ProfileViewModel 的 ProfileData；二级页沿用现有页面栈）
   ============================================================ */

@Composable
internal fun V5MeScreen(
    data: ProfileData,
    onPlans: () -> Unit,
    onOrders: () -> Unit,
    onInvite: () -> Unit,
    onTickets: () -> Unit,
    onNotices: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
) {
    val c = V5ThemeColors.current
    V5PageScaffold(tab = NavTab.ME, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_me))
        V5ScrollBody(NavTab.ME) {
            // —— 账号卡
            V5Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarP(46.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.email.ifBlank { stringResource(R.string.app_name) },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = c.text,
                                maxLines = 1,
                            )
                            Text(
                                data.subscribeInfo?.planName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.usage_no_plan),
                                fontSize = 12.sp,
                                color = c.text3,
                            )
                        }
                        data.daysUntilExpired?.let { days ->
                            V5Chip(if (days > 0) ChipTone.OK else ChipTone.DANGER, stringResource(R.string.v5_days_left, days))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.v5_balance), fontSize = 12.5.sp, color = c.text2)
                        Text(
                            data.balance,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = c.accent,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // —— 功能清单
            V5CardFlat(Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.me_plans),
                    icon = Icons.Outlined.Wallet,
                    chevron = true,
                    onClick = onPlans,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.me_orders),
                    icon = SlteIcons.Orders,
                    chevron = true,
                    onClick = onOrders,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.me_invite),
                    icon = SlteIcons.Invite,
                    chevron = true,
                    onClick = onInvite,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.me_tickets),
                    icon = SlteIcons.Ticket,
                    chevron = true,
                    onClick = onTickets,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.me_notices),
                    icon = SlteIcons.Notifications,
                    chevron = true,
                    onClick = onNotices,
                )
            }

            V5CardFlat(Modifier.fillMaxWidth()) {
                V5RowItem(
                    title = stringResource(R.string.settings_title),
                    icon = SlteIcons.Settings,
                    chevron = true,
                    onClick = onSettings,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.me_about),
                    icon = Icons.Outlined.Info,
                    chevron = true,
                    onClick = onAbout,
                )
                HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                V5RowItem(
                    title = stringResource(R.string.profile_logout),
                    icon = SlteIcons.Logout,
                    danger = true,
                    chevron = true,
                    onClick = onLogout,
                )
            }
        }
    }
}
