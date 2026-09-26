// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.ticket

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.TicketDetail
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5ErrorState
import com.slte.app.ui.v5.V5FieldHint
import com.slte.app.ui.v5.V5Input
import com.slte.app.ui.v5.V5LoadingState

/**
 * 新建工单面板（v5）。
 *
 * 行为逐项保留：主题输入、严重程度三选一（默认「中」）、正文多行输入、提交按钮的可用条件
 * （主题与正文都非空且未在提交中）、提交中禁用全部输入并锁定关闭、提交中按钮转圈。
 */
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
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        V5Input(
            value = subject,
            onValueChange = { subject = it },
            placeholder = stringResource(R.string.ticket_subject),
            icon = SlteIcons.Ticket,
            iconDesc = stringResource(R.string.ticket_subject),
            enabled = !submitting,
            small = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TicketLevelSelector(
            selected = level,
            enabled = !submitting,
            onSelect = { level = it },
        )

        Spacer(modifier = Modifier.height(12.dp))

        TicketMessageField(
            value = message,
            onValueChange = { message = it },
            placeholder = stringResource(R.string.ticket_message),
            enabled = !submitting,
        )

        Spacer(modifier = Modifier.height(20.dp))

        V5Button(
            text = stringResource(R.string.ticket_submit),
            onClick = { onSubmit(subject, level, message) },
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.PRIMARY,
            onClickEnabled = subject.isNotBlank() && message.isNotBlank(),
            loading = submitting,
        )
    }
}

/** 工单详情面板（v5）：消息气泡列表 + 回复区 / 关闭确认区。 */
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
        shape = V5SheetShape,
        titleStyle = V5SheetTitleStyle,
    ) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    V5LoadingState()
                }
            }

            errorRes != null -> {
                V5ErrorState(
                    message = stringResource(errorRes),
                    onRetry = onDismiss,
                )
            }

            detail != null -> {
                val messages = detail.messages
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ticket_empty),
                        fontSize = 12.5.sp,
                        color = V5ThemeColors.current.text3,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        messages.forEach { message ->
                            TicketMessageBubble(message)
                        }
                    }
                }

                // 已关闭的工单不再显示回复/关闭区（与 v4 一致）。
                if (!detail.ticket.isClosed) {
                    Spacer(modifier = Modifier.height(20.dp))
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
        V5FieldHint(text = stringResource(R.string.ticket_close_confirm))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            V5Button(
                text = stringResource(R.string.ticket_cancel),
                onClick = onCancelClose,
                modifier = Modifier.weight(1f),
                style = ButtonStyle.NEUTRAL,
                onClickEnabled = !closing,
            )
            V5Button(
                text = stringResource(R.string.ticket_confirm),
                onClick = onConfirmClose,
                modifier = Modifier.weight(1f),
                style = ButtonStyle.DANGER,
                onClickEnabled = !closing,
                loading = closing,
            )
        }
    } else {
        TicketMessageField(
            value = reply,
            onValueChange = onReplyChange,
            placeholder = stringResource(R.string.ticket_reply_hint),
            enabled = !replying && !closing,
        )
        Spacer(modifier = Modifier.height(12.dp))
        V5Button(
            text = stringResource(R.string.ticket_reply),
            onClick = onReply,
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.PRIMARY,
            onClickEnabled = reply.isNotBlank() && !closing,
            loading = replying,
        )
        Spacer(modifier = Modifier.height(10.dp))
        V5Button(
            text = stringResource(R.string.ticket_close),
            onClick = onRequestClose,
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.NEUTRAL,
            onClickEnabled = !replying && !closing,
        )
    }
}

/** 严重程度三选一（v5 分段按钮）。 */
@Composable
private fun TicketLevelSelector(
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        V5FieldHint(text = stringResource(R.string.ticket_level))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TICKET_LEVELS.forEach { (value, labelRes) ->
                V5Button(
                    text = stringResource(labelRes),
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    style = if (selected == value) ButtonStyle.PRIMARY else ButtonStyle.NEUTRAL,
                    small = true,
                    onClickEnabled = enabled,
                )
            }
        }
    }
}

/**
 * 多行正文输入（v5）。
 *
 * 与 [com.slte.app.ui.v5.V5Input] 同一套外壳（16dp 圆角、`surface2` 底、聚焦蓝描边），
 * 差别只在于它可以换行——`V5Input` 是 `singleLine = true`，工单正文与回复必须是多行。
 */
@Composable
private fun TicketMessageField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    val c = V5ThemeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val strokeColor = if (focused) c.accent else c.hairline
    val base = Modifier.fillMaxWidth().clip(shape).background(c.surface2)
    val bordered =
        if (focused) {
            base.then(Modifier.background(c.accentBg))
        } else {
            base
        }

    Box(
        modifier =
        bordered
            .then(
                Modifier.border(
                    1.5.dp,
                    strokeColor,
                    shape,
                ),
            )
            .heightIn(min = 96.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 14.sp,
                color = c.text3,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = c.text),            cursorBrush = SolidColor(c.accent),
        )
    }
}

private const val DEFAULT_LEVEL = 1

private val TICKET_LEVELS =
    listOf(
        0 to R.string.ticket_level_low,
        1 to R.string.ticket_level_medium,
        2 to R.string.ticket_level_high,
    )
