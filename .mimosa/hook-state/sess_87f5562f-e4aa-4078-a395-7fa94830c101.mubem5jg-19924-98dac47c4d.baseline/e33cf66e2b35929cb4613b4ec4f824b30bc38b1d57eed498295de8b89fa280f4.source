package com.slte.app.ui.screen.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.R
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketMessage
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@Composable
internal fun TicketCard(
    ticket: Ticket,
    onClick: () -> Unit,
) {
    SlteCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.gap.lg,
                    vertical = Dimens.gap.lg,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ticket.subject,
                    fontWeight = FontWeight.SemiBold,
                    style = SlteType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                TicketStatusTag(closed = ticket.status == 1)
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text =
                stringResource(R.string.ticket_created_at) + " " +
                    FormatUtils.formatDate(ticket.createdAt),
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.noticeTimeAlpha),
            )
        }
    }
}

@Composable
internal fun TicketStatusTag(closed: Boolean) {
    val container =
        if (closed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            SlteColors.current.accentInteractiveBg
        }
    val content =
        if (closed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            SlteColors.current.accentInteractive
        }

    Surface(
        shape = SlteShapes.small,
        color = container,
    ) {
        Text(
            text = stringResource(if (closed) R.string.ticket_status_closed else R.string.ticket_status_open),
            style = SlteType.caption,
            fontWeight = FontWeight.Medium,
            color = content,
            modifier =
            Modifier.padding(
                horizontal = Dimens.noticeTagPaddingH,
                vertical = Dimens.gap.xs,
            ),
        )
    }
}

@Composable
internal fun TicketMessageBubble(message: TicketMessage) {
    val isMe = message.isMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = stringResource(if (isMe) R.string.ticket_sender_me else R.string.ticket_sender_support),
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Dimens.gap.xs))
            Surface(
                shape = SlteShapes.medium,
                color =
                if (isMe) {
                    SlteColors.current.accentInteractiveBg
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = message.message,
                    style = SlteType.body,
                    color =
                    if (isMe) {
                        SlteColors.current.accentInteractive
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier =
                    Modifier.padding(
                        horizontal = Dimens.gap.md,
                        vertical = Dimens.gap.sm,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gap.xs))
            Text(
                text = FormatUtils.formatDate(message.createdAt),
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.noticeTimeAlpha),
            )
        }
    }
}
