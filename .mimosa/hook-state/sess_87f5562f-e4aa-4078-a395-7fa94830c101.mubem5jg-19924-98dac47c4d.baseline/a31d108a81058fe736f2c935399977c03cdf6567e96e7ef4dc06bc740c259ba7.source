package com.slte.app.domain.model

data class Ticket(
    val id: Int,
    val level: Int,
    val replyStatus: Int,
    val status: Int,
    val subject: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isClosed: Boolean get() = status == 1
}

data class TicketMessage(
    val id: Int,
    val ticketId: Int,
    val isMe: Boolean,
    val message: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TicketDetail(
    val ticket: Ticket,
    val messages: List<TicketMessage>,
)
