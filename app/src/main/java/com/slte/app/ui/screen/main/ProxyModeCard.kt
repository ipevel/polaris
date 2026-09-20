package com.slte.app.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slte.app.R
import com.slte.app.ui.component.SlteCard
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@Composable
fun ProxyModeCard(
    proxyMode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SlteCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gap.lg, vertical = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorfulIconBadge(
                icon = SlteIcons.ProxyMode,
                colors = listOf(Color(0xFF4A90D9), Color(0xFF7B5EE0)),
            )

            Spacer(modifier = Modifier.width(Dimens.gap.md))

            Text(
                text = stringResource(R.string.action_proxy_mode),
                style = SlteType.body.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = proxyModeLabelRes(proxyMode)?.let { stringResource(it) } ?: proxyMode,
                fontWeight = FontWeight.Medium,
                style = SlteType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(Dimens.dashboardChevronGap))

            Icon(
                imageVector = SlteIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.icon.md),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
