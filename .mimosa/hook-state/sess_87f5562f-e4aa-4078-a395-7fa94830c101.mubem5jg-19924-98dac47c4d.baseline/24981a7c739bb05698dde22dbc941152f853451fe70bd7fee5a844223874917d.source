package com.slte.app.ui.screen.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.R
import com.slte.app.domain.model.Ticket
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.component.CircleIconButton
import com.slte.app.ui.component.EmptyState
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SltePullRefresh
import com.slte.app.ui.component.SlteScaffold
import com.slte.app.ui.component.ToastTip
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.utils.Dimens

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
    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.dashboardScreenPaddingH),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap.md),
        contentPadding = PaddingValues(vertical = Dimens.gap.lg),
    ) {
        items(tickets.distinctBy { it.id }, key = { it.id }) { ticket ->
            TicketCard(
                ticket = ticket,
                onClick = { onClick(ticket) },
            )
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
        LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.ticket_empty),
        modifier = modifier,
    )
}
