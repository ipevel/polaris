package com.slte.app.data.repository

import com.slte.app.data.local.SessionManager
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepository
@Inject
constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
) {

    suspend fun fetchTickets(): Result<List<Ticket>> = runApi {
        requireLoggedIn()
        authApi.fetchTickets()
    }

    suspend fun fetchTicketDetail(id: Int): Result<TicketDetail> = runApi {
        requireLoggedIn()
        authApi.fetchTicketDetail(id)
    }

    suspend fun createTicket(
        subject: String,
        level: Int,
        message: String,
    ): Result<Boolean> = runApi {
        requireLoggedIn()
        authApi.createTicket(subject, level, message)
    }

    suspend fun replyTicket(
        id: Int,
        message: String,
    ): Result<Boolean> = runApi {
        requireLoggedIn()
        authApi.replyTicket(id, message)
    }

    suspend fun closeTicket(id: Int): Result<Boolean> = runApi {
        requireLoggedIn()
        authApi.closeTicket(id)
    }

    private fun requireLoggedIn() {
        check(sessionManager.sessionState.value is SessionState.LoggedIn) { "未登录" }
    }
}
