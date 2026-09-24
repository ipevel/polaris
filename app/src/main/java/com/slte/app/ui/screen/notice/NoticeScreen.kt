// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.notice
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.Notice
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.EmptyState
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LoadingBox
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.utils.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBack: () -> Unit,
    viewModel: NoticeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNotice by remember { mutableStateOf<Notice?>(null) }

    ToastTip(
        message = uiState.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    SlteScaffold(
        title = stringResource(R.string.notice_title),
        onBack = onBack,
    ) { innerPadding ->
        if (uiState.phase == ContentPhase.Loading) {
            LoadingContent(modifier = Modifier.padding(innerPadding))
        } else {
            val errorRes = uiState.errorMessageRes
            SltePullRefresh(
                isRefreshing = uiState.phase == ContentPhase.Refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(innerPadding),
            ) {
                when {
                    errorRes != null ->
                        PullRefreshScrollable {
                            ErrorState(
                                message = uiState.errorMessage ?: stringResource(errorRes),
                                onRetry = viewModel::loadNotices,
                            )
                        }
                    uiState.notices.isEmpty() ->
                        PullRefreshScrollable {
                            EmptyContent()
                        }
                    else ->
                        NoticeList(
                            notices = uiState.notices,
                            onClick = { selectedNotice = it },
                        )
                }
            }
        }
    }

    selectedNotice?.let { notice ->
        NoticeDetailSheet(
            notice = notice,
            onDismiss = { selectedNotice = null },
        )
    }
}

@Composable
private fun NoticeList(
    notices: List<Notice>,
    onClick: (Notice) -> Unit,
) {
    // 整份列表装进同一张卡、行间 1dp 发丝线：多张等亮卡片竖排时相邻边界只有 1.25:1，几乎看不出分组。
    val unique = remember(notices) { notices.distinctBy { it.id } }
    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.dashboardScreenPaddingH),
        contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
    ) {
        item {
            SlteCard(modifier = Modifier.fillMaxWidth()) {
                unique.forEachIndexed { index, notice ->
                    NoticeRow(
                        notice = notice,
                        topDivider = index > 0,
                        onClick = { onClick(notice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PullRefreshScrollable(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingBox()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.notice_empty),
        modifier = modifier,
    )
}
