package com.slte.app.data.remote.adapter.xboard

import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.domain.model.TicketMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XboardTicketData(
    val id: Int = 0,
    val level: Int = 0,
    @SerialName("reply_status")
    val replyStatus: Int = 0,
    val status: Int = 0,
    val subject: String = "",
    val message: List<XboardTicketMessageData>? = null,
    @SerialName("created_at")
    val createdAt: Long = 0L,
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
)

@Serializable
data class XboardTicketMessageData(
    val id: Int = 0,
    @SerialName("ticket_id")
    val ticketId: Int = 0,
    @SerialName("is_me")
    val isMe: Boolean = false,
    val message: String = "",
    @SerialName("created_at")
    val createdAt: Long = 0L,
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
)

fun XboardTicketData.toDomain() = Ticket(
    id = id,
    level = level,
    replyStatus = replyStatus,
    status = status,
    subject = subject,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun XboardTicketData.toDetailDomain() = TicketDetail(
    ticket = toDomain(),
    messages = message.orEmpty().map { it.toDomain() },
)

fun XboardTicketMessageData.toDomain() = TicketMessage(
    id = id,
    ticketId = ticketId,
    isMe = isMe,
    message = message,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Serializable
data class XboardCreateTicketRequest(
    val subject: String,
    val level: Int,
    val message: String,
)

@Serializable
data class XboardReplyTicketRequest(
    val id: Int,
    val message: String,
)

@Serializable
data class XboardCloseTicketRequest(
    val id: Int,
)
