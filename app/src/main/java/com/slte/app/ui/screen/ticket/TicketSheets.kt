// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import com.slte.app.R
import com.slte.app.domain.model.TicketDetail
import com.slte.app.ui.component.ErrorState
import com.slte.app.ui.component.LottieLoadingIcon
import com.slte.app.ui.component.SlteButton
import com.slte.app.ui.component.SlteButtonStyle
import com.slte.app.ui.component.SlteInput
import com.slte.app.ui.component.SlteInputSize
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TicketCreateSheet(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (subject: String, level: Int, message: String) -> Unit,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(DEFAULT_LEVEL) }

    SlteSheet(
        title = stringResource(R.string.ticket_new),
        onDismiss = onDismiss,
        dismissible = !submitting,
    ) {
        SlteInput(
            value = subject,
            onValueChange = { subject = it },
            placeholder = stringResource(R.string.ticket_subject),
            icon = SlteIcons.Ticket,
            iconDesc = stringResource(R.string.ticket_subject),
            enabled = !submitting,
            size = SlteInputSize.Compact,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        TicketLevelSelector(
            selected = level,
            enabled = !submitting,
            onSelect = { level = it },
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        TicketMessageField(
            value = message,
            onValueChange = { message = it },
            placeholder = stringResource(R.string.ticket_message),
            enabled = !submitting,
        )

        Spacer(modifier = Modifier.height(Dimens.gap.xl))

        SlteButton(
            text = stringResource(R.string.ticket_submit),
            onClick = { onSubmit(subject, level, message) },
            modifier = Modifier.fillMaxWidth(),
            enabled = subject.isNotBlank() && message.isNotBlank() && !submitting,
            loading = submitting,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TicketDetailSheet(
    detail: TicketDetail?,
    loading: Boolean,
    @StringRes errorRes: Int?,
    replying: Boolean,
    closing: Boolean,
    replySucceeded: Boolean,
    onConsumeReplySucceeded: () -> Unit,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit,
    onCloseTicket: () -> Unit,
) {
    var reply by rememberSaveable { mutableStateOf("") }
    var showCloseConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(replySucceeded) {
        if (replySucceeded) {
            reply = ""
            onConsumeReplySucceeded()
        }
    }

    SlteSheet(
        title = detail?.ticket?.subject ?: stringResource(R.string.ticket_detail),
        onDismiss = onDismiss,
        dismissible = !replying && !closing,
    ) {
        when {
            loading -> {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimens.size.row * 3),
                    contentAlignment = Alignment.Center,
                ) {
                    LottieLoadingIcon(modifier = Modifier.size(Dimens.icon.lg))
                }
            }

            errorRes != null -> {
                ErrorState(
                    message = stringResource(errorRes),
                    onRetry = onDismiss,
                )
            }

            detail != null -> {
                val messages = detail.messages
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ticket_empty),
                        style = SlteType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap.md)) {
                        messages.forEach { message ->
                            TicketMessageBubble(message)
                        }
                    }
                }

                if (!detail.ticket.isClosed) {
                    Spacer(modifier = Modifier.height(Dimens.gap.xl))
                    TicketReplySection(
                        showCloseConfirm = showCloseConfirm,
                        reply = reply,
                        replying = replying,
                        closing = closing,
                        onReplyChange = { reply = it },
                        onReply = { onReply(reply) },
                        onRequestClose = { showCloseConfirm = true },
                        onCancelClose = { showCloseConfirm = false },
                        onConfirmClose = onCloseTicket,
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketReplySection(
    showCloseConfirm: Boolean,
    reply: String,
    replying: Boolean,
    closing: Boolean,
    onReplyChange: (String) -> Unit,
    onReply: () -> Unit,
    onRequestClose: () -> Unit,
    onCancelClose: () -> Unit,
    onConfirmClose: () -> Unit,
) {
    if (showCloseConfirm) {
        Text(
            text = stringResource(R.string.ticket_close_confirm),
            style = SlteType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
        ) {
            SlteButton(
                text = stringResource(R.string.ticket_cancel),
                onClick = onCancelClose,
                modifier = Modifier.weight(1f),
                style = SlteButtonStyle.Neutral,
                enabled = !closing,
            )
            SlteButton(
                text = stringResource(R.string.ticket_confirm),
                onClick = onConfirmClose,
                modifier = Modifier.weight(1f),
                style = SlteButtonStyle.Danger,
                loading = closing,
                enabled = !closing,
            )
        }
    } else {
        TicketMessageField(
            value = reply,
            onValueChange = onReplyChange,
            placeholder = stringResource(R.string.ticket_reply_hint),
            enabled = !replying && !closing,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.md))
        SlteButton(
            text = stringResource(R.string.ticket_reply),
            onClick = onReply,
            modifier = Modifier.fillMaxWidth(),
            enabled = reply.isNotBlank() && !replying && !closing,
            loading = replying,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.sm))
        SlteButton(
            text = stringResource(R.string.ticket_close),
            onClick = onRequestClose,
            modifier = Modifier.fillMaxWidth(),
            style = SlteButtonStyle.Neutral,
            enabled = !replying && !closing,
        )
    }
}

@Composable
private fun TicketLevelSelector(
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ticket_level),
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gap.sm),
        ) {
            TICKET_LEVELS.forEach { (value, labelRes) ->
                SlteButton(
                    text = stringResource(labelRes),
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    style = if (selected == value) SlteButtonStyle.Primary else SlteButtonStyle.Neutral,
                    enabled = enabled,
                    height = Dimens.size.buttonMd,
                )
            }
        }
    }
}

@Composable
private fun TicketMessageField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SlteShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
        BorderStroke(
            Dimens.strokeMedium,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.size.row * 2)
                .padding(
                    horizontal = Dimens.gap.md,
                    vertical = Dimens.gap.sm,
                ),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = SlteType.field.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                textStyle = SlteType.field.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(SlteColors.current.accentInteractive),
            )
        }
    }
}

private const val DEFAULT_LEVEL = 1

private val TICKET_LEVELS =
    listOf(
        0 to R.string.ticket_level_low,
        1 to R.string.ticket_level_medium,
        2 to R.string.ticket_level_high,
    )
