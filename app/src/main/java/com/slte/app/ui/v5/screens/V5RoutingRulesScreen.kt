// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.screen.settings.RoutingRulesViewModel
import com.slte.app.ui.screen.settings.RoutingSync
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5Banner
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5PageBody
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5RowItem
import com.slte.app.ui.v5.V5Switch
import com.slte.app.ui.v5.V5TopBar

/**
 * v5 分流规则管理页（Karing 式每条规则组独立开关 + 自定义规则组）。
 * 状态与写盘逻辑与 v4 版共用 RoutingRulesViewModel。
 */
@Composable
internal fun V5RoutingRulesScreen(
    onBack: () -> Unit,
    viewModel: RoutingRulesViewModel = hiltViewModel(),
) {
    val c = V5ThemeColors.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    val customGroupState by viewModel.customGroupState.collectAsStateWithLifecycle()

    V5PageScaffold(tab = null) {
        V5TopBar(stringResource(R.string.settings_routing_rules), onBack = onBack)
        V5PageBody {
            Text(
                text = stringResource(R.string.routing_rules_desc),
                fontSize = 12.sp,
                color = c.text3,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // —— 内置分流组
            V5CardFlat(Modifier) {
                data.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    V5RowItem(
                        title = item.name,
                        sub = stringResource(outboundLabelOf(item.defaultOut)),
                        icon = SlteIcons.Route,
                        value = if (item.enabled) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off),
                        trailing = {
                            V5Switch(checked = item.enabled)
                        },
                        onClick =
                        if (data.sync == RoutingSync.Idle) {
                            { viewModel.setEnabled(item.name, !item.enabled) }
                        } else {
                            null
                        },
                    )
                }
            }

            data.errorMessageRes?.let { res ->
                V5Banner(ChipTone.DANGER, stringResource(res))
            }

            // —— 自定义规则组
            V5CardFlat(Modifier) {
                V5RowItem(
                    title = stringResource(R.string.routing_custom_add),
                    sub = stringResource(R.string.routing_custom_add_desc),
                    icon = SlteIcons.Add,
                    highlight = true,
                    chevron = true,
                    onClick = viewModel::showAddCustomGroup,
                )
                data.custom.forEach { custom ->
                    HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    V5RowItem(
                        title = custom.name,
                        sub = custom.url,
                        icon = SlteIcons.Delete,
                        danger = true,
                        chevron = true,
                        onClick = { viewModel.removeCustomGroup(custom.name) },
                    )
                }
            }

            // —— 恢复默认
            V5CardFlat(Modifier) {
                V5RowItem(
                    title = stringResource(R.string.routing_rules_reset),
                    sub = stringResource(R.string.routing_rules_reset_desc),
                    icon = SlteIcons.UpdateSubscription,
                    chevron = true,
                    onClick = viewModel::resetDefaults,
                )
            }
        }
    }

    val editing = customGroupState as? com.slte.app.ui.screen.settings.CustomGroupState.Editing
    if (editing != null) {
        com.slte.app.ui.screen.settings.CustomRuleGroupSheet(
            state = editing,
            onNameChange = viewModel::onCustomNameChange,
            onUrlChange = viewModel::onCustomUrlChange,
            onBehaviorChange = viewModel::onCustomBehaviorChange,
            onSubmit = viewModel::submitCustomGroup,
            onDismiss = viewModel::dismissCustomGroup,
        )
    }
}

private fun outboundLabelOf(defaultOut: String): Int = when (defaultOut) {
    "direct" -> R.string.routing_outbound_direct
    "block" -> R.string.routing_outbound_block
    else -> R.string.routing_outbound_proxy
}
