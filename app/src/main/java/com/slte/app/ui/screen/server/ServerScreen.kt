// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.ui.component.CircleIconButton
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.UsageCard
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@Composable
fun ServerScreen(
    subscriptionName: String = "",
    usedBytes: Long = 0L,
    totalBytes: Long = 0L,
    isValid: Boolean = false,
    hasPlan: Boolean = false,
    daysUntilExpired: Int = 0,
    expiredAt: Long = 0L,
    onUpdateSubscription: (() -> Unit)? = null,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val proxyGroups by viewModel.proxyGroups.collectAsStateWithLifecycle()
    val isLoadingGroups by viewModel.isLoadingGroups.collectAsStateWithLifecycle()
    val testingGroup by viewModel.testingGroup.collectAsStateWithLifecycle()

    // 策略组随页面直接加载
    LaunchedEffect(Unit) { viewModel.loadProxyGroups() }

    SlteScaffold(
        title = stringResource(R.string.server_title),
        showBack = false,
        actions = {
            CircleIconButton(
                icon = SlteIcons.SpeedTest,
                description = stringResource(R.string.server_speed_test),
                onClick = viewModel::startSpeedTest,
            )
            CircleIconButton(
                icon = SlteIcons.SyncSubscription,
                description = stringResource(R.string.dashboard_update_subscription),
                onClick = onUpdateSubscription ?: viewModel::updateSubscription,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.dashboardScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
        ) {
            // 复用首页订阅卡片：套餐用量 + 进度条 + 到期信息，动作按键改为"更新订阅"
            item {
                UsageCard(
                    planName = subscriptionName,
                    usedBytes = usedBytes,
                    totalBytes = totalBytes,
                    isValid = isValid,
                    hasPlan = hasPlan,
                    daysUntilExpired = daysUntilExpired.takeIf { it > 0 },
                    expiredAtDate = expiredAt.takeIf { it > 0 }?.let { FormatUtils.formatExpiryDate(it) },
                    actionText = stringResource(R.string.dashboard_update_subscription),
                    actionEnabled = true,
                    onAction = onUpdateSubscription ?: viewModel::updateSubscription,
                )
            }

            item { Spacer(modifier = Modifier.height(Dimens.gap.md)) }

            item {
                Text(
                    text = stringResource(R.string.proxy_groups_title),
                    style = SlteType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = Dimens.gap.xs),
                )
            }
            when {
                proxyGroups.isEmpty() && isLoadingGroups ->
                    item { ProxyGroupsHint(message = stringResource(R.string.proxy_groups_loading), showLoading = true) }
                proxyGroups.isEmpty() ->
                    item { ProxyGroupsHint(message = stringResource(R.string.proxy_groups_empty), showLoading = false) }
                else ->
                    items(proxyGroups, key = { it.name }) { group ->
                        ProxyGroupCard(
                            group = group,
                            isTesting = testingGroup == group.name,
                            onSelect = viewModel::selectInGroup,
                            onTest = { viewModel.testGroup(group.name) },
                        )
                    }
            }
        }
    }
}
