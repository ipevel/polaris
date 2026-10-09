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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.screen.profile.ProfileData
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.ui.v5.AvatarP
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.SectionTitle
import com.slte.app.ui.v5.V5Card
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5Divider
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5ScrollBody
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.v5Enter

/* ============================================================
   v6 我的页（iOS 简约风）：账号卡 + 功能清单 + 退出
   （数据接线：ProfileViewModel 的 ProfileData；二级页沿用现有页面栈）
   ============================================================ */

@Composable
internal fun V5MeScreen(
    data: ProfileData,
    onPlans: () -> Unit,
    onGiftCard: () -> Unit,
    onOrders: () -> Unit,
    onInvite: () -> Unit,
    onTickets: () -> Unit,
    onNotices: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onNavSelect: (NavTab) -> Unit,
    // Telegram 讨论组入口。v5 重写（b5e3464）删掉 v4 ProfileScreen 时丢了挂载点：
    // 数据层 telegramDiscussLink、图标 SlteIcons.Telegram、文案 profile_telegram 都还在，
    // 但全仓没有消费点。传 null 表示当前拿不到可用链接，此时不渲染（不做点了没反应的死入口）。
    onTelegram: (() -> Unit)? = null,
) {
    val c = V5ThemeColors.current
    V5PageScaffold(tab = NavTab.ME, onNavSelect = onNavSelect) {
        V5TopBar(stringResource(R.string.page_me))
        V5ScrollBody(NavTab.ME) {
            // —— 账号卡（本页唯一的实体卡：身份信息密度高，卡片边界用于聚焦）
            V5Card(Modifier.v5Enter(0)) {
                Column(verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp12)) {
                        AvatarP(46.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.email.ifBlank { stringResource(R.string.app_name) },
                                fontSize = V5Type.sp15,
                                fontWeight = FontWeight.SemiBold,
                                color = c.text,
                                maxLines = 1,
                                // 长邮箱此前只有 maxLines 没有 overflow，溢出部分被硬切，
                                // 看不出是邮箱被截断（默认 Clip）。
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                data.subscribeInfo?.planName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.usage_no_plan),
                                fontSize = V5Type.sp12,
                                color = c.text3,
                            )
                        }
                        // 判据与首页 PlanUsageCard 完全一致（isValid = hasPlan && !expired）：
                        // 此前这里只看 days > 0，过期用户（days == 0 但对象有效）会看到
                        // 「剩 0 天」，与首页同一份数据给出的「已过期」自相矛盾。
                        if (data.isValid) {
                            data.daysUntilExpired?.takeIf { it > 0 }?.let { days ->
                                V5Chip(ChipTone.OK, stringResource(R.string.v5_days_left, days))
                            }
                        } else {
                            V5Chip(ChipTone.DANGER, stringResource(R.string.usage_expired))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V5Spacing.dp8)) {
                        Text(stringResource(R.string.v5_balance), fontSize = V5Type.sp12_5, color = c.text2)
                        Text(
                            data.balance,
                            fontSize = V5Type.sp15,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = c.accent,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // —— 我的服务
            Column(
                modifier = Modifier.v5Enter(1),
                verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
            ) {
                SectionTitle(stringResource(R.string.me_services))
                V5CardFlat(Modifier.fillMaxWidth()) {
                    V5RowItem(
                        title = stringResource(R.string.me_plans),
                        icon = Icons.Outlined.Wallet,
                        chevron = true,
                        onClick = onPlans,
                    )
                    V5Divider()
                    // 礼品卡（兑换码）入口：v5 重写 b5e3464 删掉 v4 ProfileScreen 时丢掉了挂载点，
                    // 组件与 ViewModel 一直保留但全仓无调用点；这里按 v4 原样恢复入口，
                    // 复用既有 R.string.gift_card_title，不新增字符串（三语键集合无需改动）。
                    V5RowItem(
                        title = stringResource(R.string.gift_card_title),
                        icon = SlteIcons.InviteCode,
                        chevron = true,
                        onClick = onGiftCard,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.me_orders),
                        icon = SlteIcons.Orders,
                        chevron = true,
                        onClick = onOrders,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.me_invite),
                        icon = SlteIcons.Invite,
                        chevron = true,
                        onClick = onInvite,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.me_tickets),
                        icon = SlteIcons.Ticket,
                        chevron = true,
                        onClick = onTickets,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.me_notices),
                        icon = SlteIcons.Notifications,
                        chevron = true,
                        onClick = onNotices,
                    )
                    // Telegram 讨论组入口（v4 ProfileScreen 有，v5 重写时丢失）。
                    // 链接由调用方做白名单校验后再交进来，见 ProfilePageContent。
                    onTelegram?.let { openTelegram ->
                        V5Divider()
                        V5RowItem(
                            title = stringResource(R.string.profile_telegram),
                            icon = SlteIcons.Telegram,
                            chevron = true,
                            onClick = openTelegram,
                        )
                    }
                }
            }

            V5CardFlat(Modifier.fillMaxWidth().v5Enter(2)) {
                V5RowItem(
                    title = stringResource(R.string.settings_title),
                    icon = SlteIcons.Settings,
                    chevron = true,
                    onClick = onSettings,
                )
                V5Divider()
                V5RowItem(
                    title = stringResource(R.string.me_about),
                    icon = Icons.Outlined.Info,
                    chevron = true,
                    onClick = onAbout,
                )
                V5Divider()
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
