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
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
fun ServerScreen(
    onUpdateSubscription: (() -> Unit)? = null,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val proxyGroups by viewModel.proxyGroups.collectAsStateWithLifecycle()
    val isLoadingGroups by viewModel.isLoadingGroups.collectAsStateWithLifecycle()
    val testingGroup by viewModel.testingGroup.collectAsStateWithLifecycle()
    val isTestingAll by viewModel.isTestingAll.collectAsStateWithLifecycle()
    val errorMessageRes by viewModel.errorMessageRes.collectAsStateWithLifecycle()
    val speedTestTipRes by viewModel.speedTestTipRes.collectAsStateWithLifecycle()
    val toast = rememberToast()

    // 策略组随页面直接加载
    LaunchedEffect(Unit) { viewModel.loadProxyGroups() }

    // 无套餐 / 加载失败等提示不能静默，否则右上角测速、更新订阅点了像没反应
    LaunchedEffect(errorMessageRes) {
        errorMessageRes?.let {
            toast.show(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(speedTestTipRes) {
        speedTestTipRes?.let {
            toast.show(it)
            viewModel.consumeSpeedTestTip()
        }
    }

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
            // 顶部只保留 Scaffold actions 中的「更新订阅 / 延迟测试」两个圆形按钮；
            // 套餐信息卡已按需求移除，用量统一在首页查看
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
                            isTesting = isTestingAll || testingGroup == group.name,
                            onSelect = viewModel::selectInGroup,
                            onTest = { viewModel.testGroup(group.name) },
                        )
                    }
            }
        }
    }
}
