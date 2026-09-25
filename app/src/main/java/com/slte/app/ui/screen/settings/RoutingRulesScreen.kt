// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 分流规则管理页（Karing 式「每条规则组独立开关」）：列出内置分流组并
 * 逐组开关，写盘后触发内核重载即时生效。组列表来自 Kotlin 镜像
 * [com.slte.app.kernel.RoutingGroups]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingRulesScreen(
    onBack: () -> Unit,
    viewModel: RoutingRulesViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()

    SlteScaffold(
        title = stringResource(R.string.settings_routing_rules),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.dashboardCardSpacing),
            contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
        ) {
            item {
                Text(
                    text = stringResource(R.string.routing_rules_desc),
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.gap.lg),
                )
            }

            items(data.items.size, key = { data.items[it].name }) { index ->
                val item = data.items[index]
                SlteCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        icon = SlteIcons.Route,
                        title = item.name,
                        subtitle = stringResource(outboundLabelOf(item.defaultOut)),
                        checked = item.enabled,
                        enabled = data.sync == RoutingSync.Idle,
                        onCheckedChange = { viewModel.setEnabled(item.name, it) },
                    )
                }
            }

            item {
                SlteCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = SlteIcons.UpdateSubscription,
                        title = stringResource(R.string.routing_rules_reset),
                        subtitle = stringResource(R.string.routing_rules_reset_desc),
                        onClick = viewModel::resetDefaults,
                    )
                }
            }

            item {
                SlteCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = SlteIcons.Add,
                        title = stringResource(R.string.routing_custom_add),
                        subtitle = stringResource(R.string.routing_custom_add_desc),
                        onClick = viewModel::showAddCustomGroup,
                    )
                }
            }

            items(data.custom.size, key = { data.custom[it].name }) { index ->
                val custom = data.custom[index]
                SlteCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = SlteIcons.Delete,
                        title = custom.name,
                        subtitle = custom.url,
                        onClick = { viewModel.removeCustomGroup(custom.name) },
                    )
                }
            }

            data.errorMessageRes?.let { res ->
                item {
                    Text(
                        text = stringResource(res),
                        style = SlteType.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Dimens.gap.lg),
                    )
                }
            }
        }
    }

    val customGroupState by viewModel.customGroupState.collectAsStateWithLifecycle()
    val editing = customGroupState as? CustomGroupState.Editing
    if (editing != null) {
        CustomRuleGroupSheet(
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
