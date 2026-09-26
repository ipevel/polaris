// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.domain.model.Notice
import com.slte.app.ui.component.RichText
import com.slte.app.ui.component.SlteSheet
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ChipTone
import com.slte.app.ui.v5.V5Chip
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.utils.FormatUtils

/**
 * 公告行的 v5 语言（22dp 卡片内的清单行）。
 *
 * 与 v4 版的三处差异，都是为了"全部 V5"：
 * 1. 分隔线从 `MaterialTheme.outlineVariant`（v4 灰）换成 v5 发丝线 `hairline2`；
 * 2. 标签从自绘 `NoticeTag` 换成 v5 徽标胶囊 [V5Chip]（同一语言下只有一种胶囊）；
 * 3. 图标/字号走 v5 刻度（14/12.5/11.5sp），不再引用 v4 的 `SlteType`。
 */

/** 摘要最多两行：再多会把「日期」这一行挤出视口。 */
private const val NoticeBodyMaxLines = 2

/**
 * 公告行（不带卡片外壳）：由调用方放进同一张 [com.slte.app.ui.v5.V5CardFlat]，
 * 行间以 1dp 发丝线分隔（`topDivider = true`）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeRow(
    notice: Notice,
    topDivider: Boolean,
    onClick: () -> Unit,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    // 抽成局部变量而不是直接传尾随 lambda：`noRippleClickable` 的形参是可空函数类型
    // `(() -> Unit)?`（null 表示整行不可点），显式变量能让类型推导无歧义。
    val rowClick: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            // 与 V5SettingsScreen/V5MeScreen 等既有 v5 页面同一条发丝线写法。
            HorizontalDivider(thickness = 1.dp, color = c.hairline2)
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                // `noRippleClickable` 是返回 Modifier 的普通 @Composable（不是 Modifier 扩展），
                // 所以走 `.then(...)`——与 V5Components 内部所有调用点保持一致。
                .then(noRippleClickable(rowClick))
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = notice.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = c.text3,
                )
            }

            if (notice.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    notice.tags.forEach { tag ->
                        V5Chip(tone = ChipTone.ACCENT, text = tag)
                    }
                }
            }

            val plainBody =
                remember(notice.body) {
                    notice.body.replace(Regex("<[^>]*>"), "").trim()
                }
            if (plainBody.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = plainBody,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = c.text2,
                    maxLines = NoticeBodyMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = FormatUtils.formatDate(notice.createdAt),
                fontSize = 11.5.sp,
                color = c.text3,
            )
        }
    }
}

/** v5 面板形状与标题字号集中在 `V5Theme.kt` 定义（见 `V5SheetShape` / `V5SheetTitleStyle`）。 */

/**
 * 公告详情面板。
 *
 * 保留 [SlteSheet]（Material3 `ModalBottomSheet`）而不是换成自绘的 v5 `SheetOverlay`：公告正文
 * 是面板可滚动的长文（`RichText`），`SheetOverlay` 不滚动、不避让 IME，换过去会丢行为。
 * 这里只把面板形状与标题字号显式传成 v5 取值，正文沿用 `RichText`（跳转白名单逻辑在内）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeDetailSheet(
    notice: Notice,
    onDismiss: () -> Unit,
) {
    val c = V5ThemeColors.current
    SlteSheet(
        onDismiss = onDismiss,
        shape = V5SheetShape,
        title = notice.title,
        titleStyle = V5SheetTitleStyle,
    ) {
        if (notice.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                notice.tags.forEach { tag -> V5Chip(tone = ChipTone.ACCENT, text = tag) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = FormatUtils.formatDate(notice.createdAt),
            fontSize = 11.5.sp,
            color = c.text3,
        )

        Spacer(modifier = Modifier.height(12.dp))

        RichText(
            text = notice.body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
