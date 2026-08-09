package com.slte.app.ui.screen.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.ui.component.SlteSwitch
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.TextSizes
import com.slte.app.utils.Dimens

/** 设置行卡片：图标 + 标题 + 可选值 + 箭头，点击震动反馈 */
@Composable
internal fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = SlteShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.actionRowHeight)
                .padding(horizontal = Dimens.cardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.actionIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = title,
                fontSize = TextSizes.actionTitle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(
                    text = value,
                    fontSize = TextSizes.actionTitle,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.dashboardChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 开关行卡片（与个人中心同款样式：左侧图标 + 标题 + 右侧 Switch） */
@Composable
internal fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SlteShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.actionRowHeight)
                .padding(horizontal = Dimens.cardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.actionIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = title,
                fontSize = TextSizes.actionTitle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SlteSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { value ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(value)
                }
            )
        }
    }
}
