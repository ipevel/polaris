package com.slte.app.ui.screen.ticket

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.TicketRepository
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.ui.ContentPhase
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TicketUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val tickets: List<Ticket> = emptyList(),
    @StringRes val errorMessageRes: Int? = null,
    val errorMessage: String? = null,
    @StringRes val toastRes: Int? = null,
    val isEntering: Boolean = false,
    val submitting: Boolean = false,
    val createSucceeded: Boolean = false,
    val detail: TicketDetail? = null,
    val detailLoading: Boolean = false,
    @StringRes val detailErrorRes: Int? = null,
    val replying: Boolean = false,
    val replySucceeded: Boolean = false,
    val closing: Boolean = false,
    val closeSucceeded: Boolean = false,
)

@HiltViewModel
class TicketViewModel
@Inject
constructor(
    private val ticketRepository: TicketRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TicketUiState())
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()

    private var openedDetailId: Int? = null

    fun enterAndRefresh() {
        _uiState.update { it.copy(isEntering = true) }
        loadTickets()
    }

    fun loadTickets() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, errorMessageRes = null, errorMessage = null) }
        viewModelScope.launch {
            ticketRepository.fetchTickets().fold(
                onSuccess = { tickets ->
                    AppLog.d(TAG, "fetchTickets: ${tickets.size} 条")
                    _uiState.update {
                        it.copy(phase = ContentPhase.Idle, tickets = tickets, errorMessageRes = null, errorMessage = null, isEntering = false)
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "fetchTickets 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            errorMessageRes = R.string.ticket_error,
                            errorMessage = e.message?.let { msg -> sanitizeLog(msg) },
                            isEntering = false,
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        if (_uiState.value.phase == ContentPhase.Refreshing) return
        _uiState.update { it.copy(phase = ContentPhase.Refreshing) }
        viewModelScope.launch {
            ticketRepository.fetchTickets().fold(
                onSuccess = { tickets ->
                    AppLog.d(TAG, "refresh: ${tickets.size} 条")
                    _uiState.update {
                        it.copy(phase = ContentPhase.Idle, tickets = tickets, errorMessageRes = null, errorMessage = null)
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "refresh 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    val hasData = _uiState.value.tickets.isNotEmpty()
                    _uiState.update {
                        if (hasData) {
                            it.copy(phase = ContentPhase.Idle, toastRes = R.string.ticket_refresh_failed)
                        } else {
                            it.copy(
                                phase = ContentPhase.Idle,
                                errorMessageRes = R.string.ticket_error,
                                errorMessage = e.message?.let { msg -> sanitizeLog(msg) },
                            )
                        }
                    }
                },
            )
        }
    }

    fun createTicket(
        subject: String,
        level: Int,
        message: String,
    ) {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            ticketRepository.createTicket(subject.trim(), level, message.trim()).fold(
                onSuccess = { ok ->
                    if (ok) {
                        _uiState.update {
                            it.copy(submitting = false, createSucceeded = true, toastRes = R.string.ticket_submit_success)
                        }
                        reloadTicketsQuietly()
                    } else {
                        _uiState.update { it.copy(submitting = false, toastRes = R.string.ticket_submit_failed) }
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "createTicket 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update { it.copy(submitting = false, toastRes = R.string.ticket_submit_failed) }
                },
            )
        }
    }

    fun consumeCreateSucceeded() = _uiState.update { it.copy(createSucceeded = false) }

    fun clearToast() = _uiState.update { it.copy(toastRes = null) }

    fun openDetail(id: Int) {
        openedDetailId = id
        _uiState.update {
            it.copy(
                detail = null,
                detailLoading = true,
                detailErrorRes = null,
                replying = false,
                replySucceeded = false,
                closing = false,
                closeSucceeded = false,
            )
        }
        viewModelScope.launch {
            ticketRepository.fetchTicketDetail(id).fold(
                onSuccess = { detail ->
                    if (openedDetailId == id) {
                        AppLog.d(TAG, "fetchTicketDetail: id=$id, ${detail.messages.size} 条消息")
                        _uiState.update { it.copy(detailLoading = false, detail = detail) }
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "fetchTicketDetail 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    if (openedDetailId == id) {
                        _uiState.update { it.copy(detailLoading = false, detailErrorRes = R.string.ticket_detail_failed) }
                    }
                },
            )
        }
    }

    fun closeDetail() {
        openedDetailId = null
        _uiState.update {
            it.copy(
                detail = null,
                detailLoading = false,
                detailErrorRes = null,
                replying = false,
                replySucceeded = false,
                closing = false,
                closeSucceeded = false,
            )
        }
    }

    fun replyTicket(message: String) {
        val id = openedDetailId ?: return
        if (_uiState.value.replying) return
        _uiState.update { it.copy(replying = true) }
        viewModelScope.launch {
            ticketRepository.replyTicket(id, message.trim()).fold(
                onSuccess = { ok ->
                    if (ok) {
                        _uiState.update {
                            it.copy(replying = false, replySucceeded = true, toastRes = R.string.ticket_reply_success)
                        }
                        refreshDetailQuietly(id)
                    } else {
                        _uiState.update { it.copy(replying = false, toastRes = R.string.ticket_reply_failed) }
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "replyTicket 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update { it.copy(replying = false, toastRes = R.string.ticket_reply_failed) }
                },
            )
        }
    }

    fun consumeReplySucceeded() = _uiState.update { it.copy(replySucceeded = false) }

    fun closeTicket() {
        val id = openedDetailId ?: return
        if (_uiState.value.closing) return
        _uiState.update { it.copy(closing = true) }
        viewModelScope.launch {
            ticketRepository.closeTicket(id).fold(
                onSuccess = { ok ->
                    if (ok) {
                        _uiState.update {
                            it.copy(closing = false, closeSucceeded = true, toastRes = R.string.ticket_close_success)
                        }
                        reloadTicketsQuietly()
                    } else {
                        _uiState.update { it.copy(closing = false, toastRes = R.string.ticket_close_failed) }
                    }
                },
                onFailure = { e ->
                    AppLog.w(TAG, "closeTicket 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update { it.copy(closing = false, toastRes = R.string.ticket_close_failed) }
                },
            )
        }
    }

    fun consumeCloseSucceeded() = _uiState.update { it.copy(closeSucceeded = false) }

    private fun reloadTicketsQuietly() {
        viewModelScope.launch {
            ticketRepository.fetchTickets().onSuccess { tickets ->
                _uiState.update { it.copy(tickets = tickets) }
            }
        }
    }

    private fun refreshDetailQuietly(id: Int) {
        viewModelScope.launch {
            ticketRepository.fetchTicketDetail(id).onSuccess { detail ->
                if (openedDetailId == id) {
                    _uiState.update { it.copy(detail = detail) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "Polaris-Ticket"
    }
}
