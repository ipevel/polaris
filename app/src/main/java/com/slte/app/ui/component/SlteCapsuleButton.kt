package com.slte.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slte.app.ui.theme.SlteAlpha
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 小胶囊按钮：主题浅色半透明底（玻璃质感）+ 全圆角，无描边；按压反馈为触觉振动。
 */
@Composable
fun SlteCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier =
        modifier
            .height(Dimens.size.buttonMd)
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }.clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(percent = Dimens.planStatusChipCornerPercent),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = SlteAlpha.capsuleButtonBg),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxHeight()
                .padding(horizontal = Dimens.gap.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = SlteType.body,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
