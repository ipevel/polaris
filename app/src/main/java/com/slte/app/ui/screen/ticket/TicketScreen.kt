// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.Ticket
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.CircleIconButton
import com.slte.app.ui.component.EmptyState
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LoadingBox
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteRadii
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

/** 状态徽标统一高度：24dp = 16dp 行高 + 上下各 4dp 内边距，胶囊形。 */
private val ChipMinHeight = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
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

    SlteScaffold(
        title = stringResource(R.string.ticket_title),
        onBack = onBack,
        actions = {
            CircleIconButton(
                icon = SlteIcons.Add,
                description = stringResource(R.string.ticket_new),
                onClick = { showCreate = true },
            )
        },
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
                                onRetry = viewModel::loadTickets,
                            )
                        }

                    uiState.tickets.isEmpty() ->
                        PullRefreshScrollable {
                            EmptyContent()
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

@Composable
private fun TicketList(
    tickets: List<Ticket>,
    onClick: (Ticket) -> Unit,
) {
    // 一张卡装全部工单，行间 1dp 发丝线：多张等亮卡片竖排时相邻边界只有 1.25:1，分组几乎不可见。
    val unique = remember(tickets) { tickets.distinctBy { it.id } }
    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.dashboardScreenPaddingH),
        contentPadding = PaddingValues(vertical = Dimens.dashboardScreenPaddingV),
    ) {
        item {
            SlteCard(modifier = Modifier.fillMaxWidth()) {
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
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(
                    horizontal = Dimens.gap.lg,
                    vertical = Dimens.gap.md,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ticket.subject,
                    style = SlteType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                TicketStatusChip(closed = ticket.isClosed)
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ticket_created_at),
                    style = SlteType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(Dimens.gap.xs))
                Text(
                    text = FormatUtils.formatDate(ticket.createdAt),
                    style = SlteType.valueSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 工单状态徽标：待处理走警示色、已关闭走中性色，胶囊形。 */
@Composable
private fun TicketStatusChip(closed: Boolean) {
    val fg = if (closed) SlteColors.current.statusNeutral else SlteColors.current.statusWarning
    val bg = if (closed) SlteColors.current.statusNeutralBg else SlteColors.current.statusWarningBg

    Surface(
        shape = RoundedCornerShape(SlteRadii.pill),
        color = bg,
    ) {
        Text(
            text = stringResource(if (closed) R.string.ticket_status_closed else R.string.ticket_status_open),
            style = SlteType.caption,
            color = fg,
            modifier =
            Modifier
                .defaultMinSize(minHeight = ChipMinHeight)
                .padding(
                    horizontal = Dimens.noticeTagPaddingH,
                    vertical = Dimens.gap.xs,
                ),
        )
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
        title = stringResource(R.string.ticket_empty),
        modifier = modifier,
    )
}
