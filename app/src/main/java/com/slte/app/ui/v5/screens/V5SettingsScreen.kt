// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.screen.settings.AppearanceMode
import com.slte.app.ui.screen.settings.LanguageMode
import com.slte.app.ui.screen.settings.SettingsViewModel
import com.slte.app.ui.theme.V5Spacing
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.theme.V5Type
import com.slte.app.ui.v5.SectionTitle
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Divider
import com.slte.app.ui.v5.V5PageBody
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.v5Enter

/* ============================================================
   v5 设置页：分流规则入口 + 本地分流开关 + 外观/语言/TUN/提醒/改密
   （数据接线：SettingsViewModel；外观等弹层沿用现有 Slte 实现）
   ============================================================ */

@Composable
internal fun V5SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onTunStack: () -> Unit,
    onChangePassword: () -> Unit,
) {
    val c = V5ThemeColors.current
    val data by viewModel.data.collectAsStateWithLifecycle()

    V5PageScaffold(tab = null) {
        V5TopBar(stringResource(R.string.settings_title), onBack = onBack)
        V5PageBody {
            // 分流相关的两个入口（分流规则管理 / 本地分流方案开关）已迁到节点页底部，
            // 这里不再出现，避免同一设置项两处入口。
            // —— 通用
            Column(
                modifier = Modifier.v5Enter(0),
                verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
            ) {
                SectionTitle(stringResource(R.string.settings_group_general))
                V5CardFlat(Modifier) {
                    V5RowItem(
                        title = stringResource(R.string.settings_appearance),
                        value = stringResource(appearanceLabel(viewModel)),
                        chevron = true,
                        onClick = onAppearance,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.settings_language),
                        value = stringResource(languageLabel(viewModel)),
                        chevron = true,
                        onClick = onLanguage,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.settings_tun_stack),
                        value = stringResource(data.tunStackMode.labelRes),
                        chevron = true,
                        onClick = onTunStack,
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.settings_change_password),
                        chevron = true,
                        onClick = onChangePassword,
                    )
                }
            }

            // —— 提醒
            Column(
                modifier = Modifier.v5Enter(1),
                verticalArrangement = Arrangement.spacedBy(V5Spacing.dp10),
            ) {
                SectionTitle(stringResource(R.string.settings_group_remind))
                V5CardFlat(Modifier) {
                    V5RowItem(
                        title = stringResource(R.string.settings_expire_remind),
                        switchState = data.expireRemindEnabled,
                        trailing = {
                            V5Switch(checked = data.expireRemindEnabled)
                        },
                        onClick =
                        if (data.remindSync == com.slte.app.ui.screen.settings.RemindSync.Idle) {
                            { viewModel.setExpireRemind(!data.expireRemindEnabled) }
                        } else {
                            null
                        },
                    )
                    V5Divider()
                    V5RowItem(
                        title = stringResource(R.string.settings_traffic_remind),
                        switchState = data.trafficRemindEnabled,
                        trailing = {
                            V5Switch(checked = data.trafficRemindEnabled)
                        },
                        onClick =
                        if (data.remindSync == com.slte.app.ui.screen.settings.RemindSync.Idle) {
                            { viewModel.setTrafficRemind(!data.trafficRemindEnabled) }
                        } else {
                            null
                        },
                    )
                }
            }

            data.errorMessageRes?.let { res ->
                Text(
                    text = stringResource(res),
                    fontSize = V5Type.sp12_5,
                    // 原来用语义实色 c.danger(#E5484D)：对页面底 #F1F0F6 只有 3.36:1，低于 AA ——
                    // 也就是说 App 出问题时**唯一说明原因的那行字**恰恰最难读。
                    // 换 c.dangerText(#C13D41)：同底色实测 4.62:1。
                    color = c.dangerText,
                    modifier = Modifier.padding(horizontal = V5Spacing.dp4),
                )
            }
        }
    }
}

@Composable
private fun appearanceLabel(viewModel: SettingsViewModel): Int {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    return AppearanceMode.fromThemeMode(themeMode).labelRes
}

@Composable
private fun languageLabel(viewModel: SettingsViewModel): Int {
    val data by viewModel.data.collectAsStateWithLifecycle()
    return LanguageMode.fromLocale(data.locale).labelRes
}
