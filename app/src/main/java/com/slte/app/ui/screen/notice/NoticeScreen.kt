// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.Notice
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5EmptyState
import com.slte.app.ui.v5.V5ErrorState
import com.slte.app.ui.v5.V5LoadingState
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5PullRefresh
import com.slte.app.ui.v5.V5TopBar

/**
 * 公告页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SltePullRefresh` + `EmptyState/ErrorState/LoadingBox` 版本，
 * 行为与入口逐项对齐（详见本轮交付报告的「公告页入口对账清单」）：
 * 返回、下拉刷新、刷新失败提示、重试、空态、单卡去重列表、行点击开详情面板。
 */
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

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.notice_title),
            onBack = onBack,
        )

        if (uiState.phase == ContentPhase.Loading) {
            StateScrollable { V5LoadingState() }
        } else {
            val errorRes = uiState.errorMessageRes
            V5PullRefresh(
                isRefreshing = uiState.phase == ContentPhase.Refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    errorRes != null ->
                        StateScrollable {
                            V5ErrorState(
                                message = uiState.errorMessage ?: stringResource(errorRes),
                                onRetry = viewModel::loadNotices,
                            )
                        }

                    uiState.notices.isEmpty() ->
                        StateScrollable {
                            V5EmptyState(title = stringResource(R.string.notice_empty))
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

/**
 * 公告列表：整份列表装进同一张卡、行间 1dp 发丝线。
 *
 * 保留 v4 的这个决定：多张等亮卡片竖排时相邻边界只有 1.25:1，几乎看不出分组。
 * 用 `LazyColumn` 而不是 `V5PageBody` 的 `verticalScroll`：公告条数由面板决定，可能很长，
 * 需要惰性渲染（`V5PageBody` 是一次性组合全部子项的 Column）。
 */
@Composable
private fun NoticeList(
    notices: List<Notice>,
    onClick: (Notice) -> Unit,
) {
    val unique = remember(notices) { notices.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
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

/**
 * 不滚动内容的脚手架：把空态/错态/加载态卡片在视口里居中，同时**保持可滚动**。
 *
 * 必须有这一层：[V5PullRefresh] 靠子节点的嵌套滚动接收下拉手势，直接放一个 `Box` 会让
 * 空态/错态页面下拉失效（v4 的 `PullRefreshScrollable` 就是为此存在的）。
 */
@Composable
private fun StateScrollable(
    horizontalPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
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
