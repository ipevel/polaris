package com.slte.app.ui.screen.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.slte.app.domain.model.Notice
import com.slte.app.ui.component.RichText
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens
import com.slte.app.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeCard(
    notice: Notice,
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
                    text = notice.title,
                    fontWeight = FontWeight.SemiBold,
                    style = SlteType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
                Icon(
                    imageVector = SlteIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.icon.md),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (notice.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.noticeTagSpacing),
                ) {
                    notice.tags.forEach { tag ->
                        NoticeTag(text = tag)
                    }
                }
            }

            val plainBody =
                remember(notice.body) {
                    notice.body.replace(Regex("<[^>]*>"), "").trim()
                }
            if (plainBody.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gap.sm))
                Text(
                    text = plainBody,
                    style = SlteType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = Dimens.noticeBodyMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Text(
                text = FormatUtils.formatDate(notice.createdAt),
                style = SlteType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.noticeTimeAlpha),
            )
        }
    }
}

@Composable
internal fun NoticeTag(text: String) {
    Surface(
        shape = SlteShapes.small,
        color = SlteColors.current.accentInteractiveBg,
    ) {
        Text(
            text = text,
            style = SlteType.caption,
            fontWeight = FontWeight.Medium,
            color = SlteColors.current.accentInteractive,
            modifier =
            Modifier.padding(
                horizontal = Dimens.noticeTagPaddingH,
                vertical = Dimens.gap.xs,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeDetailSheet(
    notice: Notice,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    SlteSheet(
        onDismiss = onDismiss,
        title = notice.title,
    ) {
        if (notice.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.gap.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.noticeTagSpacing)) {
                notice.tags.forEach { tag -> NoticeTag(text = tag) }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = FormatUtils.formatDate(notice.createdAt),
            style = SlteType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.noticeTimeAlpha),
        )

        Spacer(modifier = Modifier.height(Dimens.gap.md))

        RichText(
            text = notice.body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
