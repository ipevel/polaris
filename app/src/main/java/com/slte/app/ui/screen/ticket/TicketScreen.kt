// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.Ticket
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.V5EmptyState
import com.slte.app.ui.v5.V5ErrorState
import com.slte.app.ui.v5.V5LoadingState
import com.slte.app.ui.v5.V5PageScaffold
import com.slte.app.ui.v5.V5PullRefresh
import com.slte.app.ui.v5.V5TopBar
import com.slte.app.ui.v5.V5TopIconButton
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.utils.FormatUtils

/**
 * 我的工单页（v5 语言）。
 *
 * 迁移自 v4 的 `SlteScaffold` + `SltePullRefresh` + `EmptyState/ErrorState/LoadingBox` 版本；
 * 入口与行为逐项对齐（见本轮交付报告的「工单页入口对账清单」）：返回、右上「新建工单」、
 * 下拉刷新、刷新失败提示、重试、空态、单卡去重列表、行点击开详情面板、
 * 新建面板、详情面板（回复 / 关闭确认）、创建成功与关闭成功后的收尾。
 */
@Composable
fun TicketScreen(
    onBack: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var selectedTicketId by remember { mutableStateOf<Int?>(null) }

    ToastTip(
        message = uiState.toastRes?.let { stringResource(it) },
        onDismiss = viewModel::clearToast,
    )

    LaunchedEffect(uiState.createSucceeded) {
        if (uiState.createSucceeded) {
            showCreate = false
            viewModel.consumeCreateSucceeded()
        }
    }

    LaunchedEffect(uiState.closeSucceeded) {
        if (uiState.closeSucceeded) {
            selectedTicketId = null
            viewModel.consumeCloseSucceeded()
        }
    }

    LaunchedEffect(selectedTicketId) {
        val id = selectedTicketId
        if (id != null) {
            viewModel.openDetail(id)
        } else {
            viewModel.closeDetail()
        }
    }

    V5PageScaffold(tab = null) {
        V5TopBar(
            title = stringResource(R.string.ticket_title),
            onBack = onBack,
            actions = {
                V5TopIconButton(
                    icon = SlteIcons.Add,
                    onClick = { showCreate = true },
                )
            },
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
                                onRetry = viewModel::loadTickets,
                            )
                        }

                    uiState.tickets.isEmpty() ->
                        StateScrollable {
                            V5EmptyState(title = stringResource(R.string.ticket_empty))
                        }

                    else ->
                        TicketList(
                            tickets = uiState.tickets,
                            onClick = { selectedTicketId = it.id },
                        )
                }
            }
        }
    }

    if (showCreate) {
        TicketCreateSheet(
            submitting = uiState.submitting,
            onDismiss = { showCreate = false },
            onSubmit = { subject, level, message -> viewModel.createTicket(subject, level, message) },
        )
    }

    selectedTicketId?.let {
        TicketDetailSheet(
            detail = uiState.detail,
            loading = uiState.detailLoading,
            errorRes = uiState.detailErrorRes,
            replying = uiState.replying,
            closing = uiState.closing,
            replySucceeded = uiState.replySucceeded,
            onConsumeReplySucceeded = viewModel::consumeReplySucceeded,
            onDismiss = { selectedTicketId = null },
            onReply = { message -> viewModel.replyTicket(message) },
            onCloseTicket = viewModel::closeTicket,
        )
    }
}

/**
 * 工单列表：一张卡装全部工单，行间 1dp 发丝线。
 *
 * 保留 v4 的这个决定：多张等亮卡片竖排时相邻边界只有 1.25:1，分组几乎不可见。
 * 用 `LazyColumn` 而不是 `V5PageBody`：工单条数由面板决定，可能很长，需要惰性渲染。
 */
@Composable
private fun TicketList(
    tickets: List<Ticket>,
    onClick: (Ticket) -> Unit,
) {
    val unique = remember(tickets) { tickets.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            V5CardFlat(modifier = Modifier.fillMaxWidth()) {
                unique.forEachIndexed { index, ticket ->
                    TicketRow(
                        ticket = ticket,
                        topDivider = index > 0,
                        onClick = { onClick(ticket) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketRow(
    ticket: Ticket,
    topDivider: Boolean,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    val rowClick: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .then(noRippleClickable(rowClick))
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = ticket.subject,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TicketStatusChip(closed = ticket.isClosed)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ticket_created_at),
                    fontSize = 11.5.sp,
                    color = c.text3,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = FormatUtils.formatDate(ticket.createdAt),
                    fontSize = 11.5.sp,
                    color = c.text3,
                )
            }
        }
    }
}

/** 工单状态徽标：待处理走警示色、已关闭走中性色（v5 统一胶囊）。 */
@Composable
private fun TicketStatusChip(closed: Boolean) {
    V5Chip(
        tone = if (closed) ChipTone.NEUTRAL else ChipTone.WARN,
        text = stringResource(if (closed) R.string.ticket_status_closed else R.string.ticket_status_open),
    )
}

/** 不滚动内容的脚手架（保持可滚动，否则空/错态下拉刷新失效，理由同公告页）。 */
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
