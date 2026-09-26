// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.ui.v5.ButtonStyle
import com.slte.app.ui.v5.V5Button
import com.slte.app.ui.v5.V5CardFlat
import com.slte.app.ui.v5.noRippleClickable
import com.slte.app.utils.copyToClipboard

/**
 * 邀请码卡（v5）：标题 + 「生成」小号按钮 + 邀请码行（码/访问量/复制）。
 *
 * 三处 v4 → v5 的变化：
 * 1. 「生成」从 `TextButton`（Material 文字按钮）换成 `V5Button(small = true, TONAL)`——
 *    与状态卡片的按钮同规格，全站只有一种"卡片内动作按钮"；
 * 2. 邀请码行底从 `surfaceVariant` 换成 `surface2`（v5 表面阶梯），圆角 14dp；
 * 3. 复制按钮从 Material `IconButton`（48dp 触控框 + 涟漪）换成 30dp 圆角图标块。
 *    注意：**触控目标由 48dp 缩到 30dp**，属"用户本轮明确不改无障碍/触控"的范围内；
 *    这里如实标注，不当作已达标。
 */
@Composable
fun InviteCodeCard(
    codes: List<InviteCodeInfo>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    context: android.content.Context,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    V5CardFlat(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.invite_code_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                V5Button(
                    text = stringResource(R.string.invite_code_generate),
                    style = ButtonStyle.TONAL,
                    small = true,
                    leadingIcon = SlteIcons.Add,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onGenerate()
                    },
                )
            }

            if (codes.isEmpty()) {
                Text(
                    text = stringResource(R.string.invite_code_empty),
                    fontSize = 12.5.sp,
                    color = c.text3,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 2.dp),
                )
            } else {
                codes.forEachIndexed { index, code ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline2)
                    InviteCodeItem(code = code, context = context)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun InviteCodeItem(
    code: InviteCodeInfo,
    context: android.content.Context,
) {
    val c = V5ThemeColors.current
    val haptic = LocalHapticFeedback.current
    val toast = rememberToast()
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = code.code,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.invite_code_pv, code.pv),
            fontSize = 11.5.sp,
            color = c.text3,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.accentBg)
                .then(
                    noRippleClickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyToClipboard(context, "invite_code", code.code)
                        toast.show(R.string.invite_code_copied)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SlteIcons.Copy,
                contentDescription = stringResource(R.string.invite_code_copy),
                modifier = Modifier.size(17.dp),
                tint = c.accent,
            )
        }
    }
}
