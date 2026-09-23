// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.slte.app.R
import com.slte.app.ui.navigation.RootTab
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

/**
 * 四个根 Tab 的底部导航栏（首页/节点/流量/我的）。
 * 激活项用主题主色高亮，顶部以「星辉金」小圆点做强调。
 */
@Composable
fun SlteBottomNavBar(
    currentTab: RootTab,
    onTabSelected: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            thickness = Dimens.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimens.bottomNavHeight),
        ) {
            RootTab.values().forEach { tab ->
                val selected = tab == currentTab
                val visual = tab.visual()
                BottomNavItem(
                    icon = visual.icon,
                    label = stringResource(visual.labelRes),
                    selected = selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color =
        if (selected) {
            SlteColors.current.accentInteractive
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(Dimens.bottomNavIndicatorSize)
                .clip(CircleShape)
                .background(if (selected) SlteColors.current.brandGold else Color.Transparent),
        )
        Spacer(modifier = Modifier.height(Dimens.gap.sm))
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(Dimens.icon.md),
            tint = color,
        )
        Spacer(modifier = Modifier.height(Dimens.gap.xs))
        Text(
            text = label,
            style = SlteType.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}

private data class TabVisual(val icon: ImageVector, val labelRes: Int)

private fun RootTab.visual(): TabVisual = when (this) {
    RootTab.Home -> TabVisual(SlteIcons.Home, R.string.tab_home)
    RootTab.Server -> TabVisual(SlteIcons.Node, R.string.tab_server)
    RootTab.Traffic -> TabVisual(SlteIcons.TrafficChart, R.string.tab_traffic)
    RootTab.Profile -> TabVisual(SlteIcons.Profile, R.string.tab_profile)
}
